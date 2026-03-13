package com.coooolfan.unirhy.service.task

import com.coooolfan.unirhy.model.*
import com.coooolfan.unirhy.model.storage.FileProviderFileSystem
import com.coooolfan.unirhy.model.storage.FileProviderType
import com.coooolfan.unirhy.model.storage.readonly
import com.coooolfan.unirhy.service.task.common.*
import com.coooolfan.unirhy.utils.sha256
import com.fasterxml.jackson.databind.ObjectMapper
import org.babyfish.jimmer.sql.ast.mutation.AssociatedSaveMode
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.exception.ExecutionException
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.Artwork
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.server.ResponseStatusException
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.io.path.Path

@Service
class ScanTaskService(
    private val sql: KSqlClient,
    private val objectMapper: ObjectMapper,
    private val queueStore: AsyncTaskQueueStore,
    private val transactionTemplate: TransactionTemplate,
) {

    private val logger = LoggerFactory.getLogger(ScanTaskService::class.java)

    fun submit(request: ScanTaskRequest) {
        // TODO: 这里可以改成扫描，每个文件的解析元数据可以作为单条任务入队
        validateRequest(request)
        val paramsJson = objectMapper.writeValueAsString(request)
        try {
            queueStore.enqueue(TaskType.SCAN, listOf(paramsJson))
        } catch (_: ExecutionException) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Scan task already pending or running for provider ${request.providerType}:${request.providerId}",
            )
        }
    }

    fun consumePendingTask() {
        try {
            transactionTemplate.executeWithoutResult {
                val claimedTask = queueStore.claimNext(TaskType.SCAN) ?: return@executeWithoutResult
                val request = objectMapper.readValue(claimedTask.paramsJson, ScanTaskRequest::class.java)
                try {
                    execute(request)
                    queueStore.completeTask(claimedTask.id, TaskStatus.COMPLETED, "SUCCESS")
                } catch (ex: Throwable) {
                    logger.error("Scan task failed, logId={}", claimedTask.id, ex)
                    queueStore.completeTask(claimedTask.id, TaskStatus.FAILED, failureReason(ex))
                }
            }
        } catch (ex: Throwable) {
            logger.error("Failed to consume pending scan task", ex)
        }
    }

    private fun validateRequest(request: ScanTaskRequest) {
        if (request.providerType != FileProviderType.FILE_SYSTEM) {
            error("Unsupported provider type: ${request.providerType}")
        }
        sql.findOneById(FileProviderFileSystem::class, request.providerId)
    }

    private fun execute(request: ScanTaskRequest) {
        val provider = sql.findOneById(FileProviderFileSystem::class, request.providerId)
        val writeableProvider = sql.executeQuery(FileProviderFileSystem::class) {
            where(table.readonly eq false)
            select(table)
        }.first()

        // 1. 取到对应路径下的所有音频文件
        val rootDir = File(provider.parentPath)
        val mediaFiles = findAudioFilesRecursively(rootDir)

        // 2. 过滤掉已经存在的媒体文件
        val existingMediaFiles = sql.executeQuery(MediaFile::class) {
            where(table.fsProvider eq provider)
            select(table.objectKey)
        }
        val newMediaFiles = mediaFiles.filter { file ->
            val relativePath = file.relativeTo(rootDir).path
            relativePath !in existingMediaFiles
        }

        // 3. 遍历新找到的音频文件，构建作品
        val works = mutableListOf<Work>()
        for (file in newMediaFiles) {
            val relativePath = file.relativeTo(rootDir).path
            val audioTag = AudioFileIO.read(file)
            val tag = audioTag.tag
            val audioCover = fetchCover(file, provider, writeableProvider, tag?.firstArtwork)

            works.add(Work {
                title = tag?.getFirst(FieldKey.TITLE).orEmpty()
                recordings().addBy {
                    kind = "CD"
                    label = "CD"
                    title = tag?.getFirst(FieldKey.TITLE).orEmpty()
                    comment = tag?.getFirst(FieldKey.COMMENT).orEmpty()
                    cover = audioCover
                    durationMs = audioTag.audioHeader.preciseTrackLength.toLong() * 1000
                    defaultInWork = false
                    artists().addBy {
                        displayName = tag?.getFirst(FieldKey.ARTIST).orEmpty()
                        alias = listOf(tag?.getFirst(FieldKey.ARTIST).orEmpty())
                        comment = "load from local file: $relativePath"
                        avatar = null
                    }
                    albums().addBy {
                        title = tag?.getFirst(FieldKey.ALBUM).orEmpty()
                        kind = "CD"
                        releaseDate = null
                        comment = "load from local file: $relativePath"
                        cover = audioCover
                    }
                    assets().addBy {
                        comment = "load from local file: $relativePath"
                        mediaFile {
                            sha256 = "mocked-sha256"
                            objectKey = relativePath
                            mimeType = Files.probeContentType(file.toPath()) ?: "application/octet-stream"
                            size = file.length()
                            width = null
                            height = null
                            ossProvider = null
                            fsProvider = provider
                        }
                    }
                }
            })
        }

        sql.saveEntities(works) {
            setMode(SaveMode.INSERT_ONLY)
            setAssociatedModeAll(AssociatedSaveMode.APPEND)
            setAssociatedMode(Asset::mediaFile, AssociatedSaveMode.APPEND_IF_ABSENT)
            setAssociatedMode(MediaFile::fsProvider, AssociatedSaveMode.APPEND_IF_ABSENT)
            setAssociatedMode(Album::cover, AssociatedSaveMode.APPEND_IF_ABSENT)
            setAssociatedMode(Recording::cover, AssociatedSaveMode.APPEND_IF_ABSENT)
        }
    }
}

private val COVER_EXTENSIONS = hashSetOf("jpg", "jpeg", "png", "gif")

data class ScanTaskRequest(
    val providerType: FileProviderType,
    val providerId: Long,
)

fun fetchCover(
    file: File,
    provider: FileProviderFileSystem,
    writeableProvider: FileProviderFileSystem,
    artwork: Artwork?
): MediaFile? {
    for (ext in COVER_EXTENSIONS) {
        var coverFile = File(file.parentFile, "${file.nameWithoutExtension}.$ext")
        if (!coverFile.exists()) {
            coverFile = File(file.parentFile, "${file.nameWithoutExtension}.${ext.uppercase()}")
        }
        if (!coverFile.exists()) {
            continue
        }
        return MediaFile {
            sha256 = coverFile.sha256()
            objectKey = coverFile.relativeTo(Path(provider.parentPath).toFile()).path
            mimeType = Files.probeContentType(coverFile.toPath())
            size = coverFile.length()
            width = null
            height = null
            ossProvider = null
            fsProvider = provider
        }
    }

    val safeArtwork = artwork ?: return null
    val binaryData = safeArtwork.binaryData ?: return null
    if (binaryData.isEmpty()) {
        return null
    }

    val mimeType = safeArtwork.mimeType?.trim().takeIf { !it.isNullOrBlank() } ?: "image/jpeg"
    val extension = extensionFromMime(mimeType)
    val sha256 = sha256Bytes(binaryData)

    val targetProvider = if (provider.readonly) writeableProvider else provider
    val objectKey = "covers/$sha256.$extension"
    val coverFile = File(targetProvider.parentPath, objectKey)

    if (!coverFile.exists()) {
        coverFile.parentFile?.mkdirs()
        coverFile.writeBytes(binaryData)
    }

    return MediaFile {
        this.sha256 = sha256
        this.objectKey = objectKey
        this.mimeType = Files.probeContentType(coverFile.toPath()) ?: mimeType
        this.size = binaryData.size.toLong()
        this.width = safeArtwork.width.takeIf { it > 0 }
        this.height = safeArtwork.height.takeIf { it > 0 }
        this.ossProvider = null
        this.fsProvider = targetProvider
    }
}

private fun extensionFromMime(mimeType: String): String =
    when (mimeType.lowercase()) {
        "image/jpeg", "image/jpg" -> "jpg"
        "image/png" -> "png"
        "image/gif" -> "gif"
        "image/webp" -> "webp"
        "image/bmp" -> "bmp"
        else -> "jpg"
    }

private fun sha256Bytes(binaryData: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(binaryData)
        .joinToString("") { "%02x".format(it) }
