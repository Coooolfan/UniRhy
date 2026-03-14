package com.coooolfan.unirhy.service.task

import com.coooolfan.unirhy.model.*
import com.coooolfan.unirhy.model.storage.FileProviderFileSystem
import com.coooolfan.unirhy.model.storage.FileProviderType
import com.coooolfan.unirhy.service.SystemConfigService.Companion.SYSTEM_CONFIG_ID
import com.coooolfan.unirhy.service.task.common.*
import com.coooolfan.unirhy.utils.sha256
import com.fasterxml.jackson.databind.ObjectMapper
import org.babyfish.jimmer.sql.ast.mutation.AssociatedSaveMode
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.Artwork
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
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
        val provider = validateRequest(request)

        // 分批插入
        val paramsJsonBatch = mutableListOf<String>()
        for (payload in discoverScanFileTaskPayloads(
            rootDir = File(provider.parentPath),
            providerType = request.providerType,
            providerId = request.providerId,
        )) {
            paramsJsonBatch += objectMapper.writeValueAsString(payload)
            if (paramsJsonBatch.size >= SCAN_ENQUEUE_BATCH_SIZE) {
                queueStore.enqueueIgnoringConflicts(TaskType.METADATA_PARSE, paramsJsonBatch)
                paramsJsonBatch.clear()
            }
        }
        queueStore.enqueueIgnoringConflicts(TaskType.METADATA_PARSE, paramsJsonBatch)
    }

    fun consumePendingTask() {
        try {
            transactionTemplate.executeWithoutResult {
                val claimedTask = queueStore.claimNext(TaskType.METADATA_PARSE) ?: return@executeWithoutResult
                val payload = objectMapper.readValue(claimedTask.paramsJson, ScanFileTaskPayload::class.java)
                try {
                    val completionReason = execute(payload)
                    queueStore.completeTask(claimedTask.id, TaskStatus.COMPLETED, completionReason)
                } catch (ex: Throwable) {
                    logger.error("Scan task failed, logId={}", claimedTask.id, ex)
                    queueStore.completeTask(claimedTask.id, TaskStatus.FAILED, failureReason(ex))
                }
            }
        } catch (ex: Throwable) {
            logger.error("Failed to consume pending scan task", ex)
        }
    }

    private fun validateRequest(request: ScanTaskRequest): FileProviderFileSystem {
        if (request.providerType != FileProviderType.FILE_SYSTEM) {
            error("Unsupported provider type: ${request.providerType}")
        }
        return sql.findOneById(FileProviderFileSystem::class, request.providerId)
    }

    private fun execute(payload: ScanFileTaskPayload): String {
        val provider = validateRequest(
            ScanTaskRequest(
                providerType = payload.providerType,
                providerId = payload.providerId,
            )
        )
        val writeableProvider = sql.executeQuery(SystemConfig::class) {
            where(table.id eq SYSTEM_CONFIG_ID)
            select(table.fsProvider)
        }.first()

        val rootDir = File(provider.parentPath)
        val file = File(rootDir, payload.objectKey)
        if (!file.exists() || !file.isFile) {
            logger.info(
                "Skip scan task because source file is missing, providerId={}, objectKey={}",
                provider.id,
                payload.objectKey
            )
            return "SKIPPED: source file missing"
        }

        val work = buildWorkFromAudioFile(
            file = file,
            objectKey = payload.objectKey,
            provider = provider,
            writeableProvider = writeableProvider,
        )

        sql.saveEntities(listOf(work)) {
            setMode(SaveMode.INSERT_ONLY)
            setAssociatedModeAll(AssociatedSaveMode.APPEND)
            setAssociatedMode(Asset::mediaFile, AssociatedSaveMode.APPEND_IF_ABSENT)
            setAssociatedMode(MediaFile::fsProvider, AssociatedSaveMode.APPEND_IF_ABSENT)
            setAssociatedMode(Album::cover, AssociatedSaveMode.APPEND_IF_ABSENT)
            setAssociatedMode(Recording::cover, AssociatedSaveMode.APPEND_IF_ABSENT)
        }
        return "SUCCESS"
    }
}

private val COVER_EXTENSIONS = hashSetOf("jpg", "jpeg", "png", "gif")
private const val SCAN_ENQUEUE_BATCH_SIZE = 512

data class ScanTaskRequest(
    val providerType: FileProviderType,
    val providerId: Long,
)

data class ScanFileTaskPayload(
    val providerType: FileProviderType,
    val providerId: Long,
    val objectKey: String,
)

fun discoverScanFileTaskPayloads(
    rootDir: File,
    providerType: FileProviderType,
    providerId: Long,
): Sequence<ScanFileTaskPayload> {
    return sequence {
        for (file in findAudioFilesRecursively(rootDir)) {
            val objectKey = file.relativeTo(rootDir).path
            yield(
                ScanFileTaskPayload(
                    providerType = providerType,
                    providerId = providerId,
                    objectKey = objectKey,
                )
            )
        }
    }
}

private fun buildWorkFromAudioFile(
    file: File,
    objectKey: String,
    provider: FileProviderFileSystem,
    writeableProvider: FileProviderFileSystem,
): Work {
    val audioTag = AudioFileIO.read(file)
    val tag = audioTag.tag
    val audioCover = fetchCover(file, provider, writeableProvider, tag?.firstArtwork)

    return Work {
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
                comment = "load from local file: $objectKey"
                avatar = null
            }
            albums().addBy {
                title = tag?.getFirst(FieldKey.ALBUM).orEmpty()
                kind = "CD"
                releaseDate = null
                comment = "load from local file: $objectKey"
                cover = audioCover
            }
            assets().addBy {
                comment = "load from local file: $objectKey"
                mediaFile {
                    sha256 = "mocked-sha256"
                    this.objectKey = objectKey
                    mimeType = Files.probeContentType(file.toPath()) ?: "application/octet-stream"
                    size = file.length()
                    width = null
                    height = null
                    ossProvider = null
                    fsProvider = provider
                }
            }
        }
    }
}

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
