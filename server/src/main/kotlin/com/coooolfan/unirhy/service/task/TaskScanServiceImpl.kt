package com.coooolfan.unirhy.service.task

import com.coooolfan.unirhy.model.Asset
import com.coooolfan.unirhy.model.MediaFile
import com.coooolfan.unirhy.model.Work
import com.coooolfan.unirhy.model.addBy
import com.coooolfan.unirhy.model.storage.FileProviderFileSystem
import com.coooolfan.unirhy.model.storage.FileProviderType
import com.coooolfan.unirhy.model.storage.readonly
import com.coooolfan.unirhy.utils.sha256
import org.babyfish.jimmer.sql.ast.mutation.AssociatedSaveMode
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.reflect.KClass

@Service
class TaskScanServiceImpl(private val sql: KSqlClient) : TaskService<ScanTaskRequest> {

    private val logger = LoggerFactory.getLogger(TaskScanServiceImpl::class.java)

    override val requestClass: KClass<ScanTaskRequest> = ScanTaskRequest::class

    override fun executeTask(request: ScanTaskRequest) {
        if (request.providerType != FileProviderType.FILE_SYSTEM) {
            error("Unsupported provider type: ${request.providerType}")
        }

        val provider = sql.findOneById(FileProviderFileSystem::class, request.providerId)
        val writeableProvider = sql.executeQuery(FileProviderFileSystem::class) {
            where(table.readonly eq false)
            select(table)
        }.first()

        val rootDir = File(provider.parentPath)

        val mediaFiles = findAudioFilesRecursively(rootDir)

        val works = mutableListOf<Work>()

        for (file in mediaFiles) {
            val relativePath = file.relativeTo(rootDir).path
            val audioTag = AudioFileIO.read(file)

            val audioCover = fetchCover(file, provider, writeableProvider)

            works.add(Work {
                title = audioTag.tag.getFirst(FieldKey.TITLE)
                recordings().addBy {
                    kind = "CD"
                    label = "CD"
                    title = audioTag.tag.getFirst(FieldKey.TITLE)
                    comment = audioTag.tag.getFirst(FieldKey.COMMENT)
                    cover = audioCover
                    artists().addBy {
                        name = audioTag.tag.getFirst(FieldKey.ARTIST)
                        comment = "load from local file: $relativePath"
                        avatar = null
                    }
                    albums().addBy {
                        title = audioTag.tag.getFirst(FieldKey.ALBUM)
                        kind = "CD"
                        releaseDate = null
                        comment = "load from local file: $relativePath"
                        cover = audioCover
                    }
                    assets().addBy {
                        comment = "load from local file: $relativePath"
                        mediaFile {
                            sha256 = file.sha256()
                            objectKey = relativePath
                            mimeType = Files.probeContentType(file.toPath())
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

        sql.transaction {
            sql.saveEntities(works) {
                setMode(SaveMode.INSERT_ONLY)
                setAssociatedModeAll(AssociatedSaveMode.APPEND)
                setAssociatedMode(Asset::mediaFile, AssociatedSaveMode.APPEND_IF_ABSENT)
                setAssociatedMode(MediaFile::fsProvider, AssociatedSaveMode.APPEND_IF_ABSENT)
            }
        }
    }

    private fun findAudioFilesRecursively(rootDir: File): List<File> {
        if (!rootDir.exists() || !rootDir.isDirectory) {
            logger.warn("Directory not found or is not a directory: ${rootDir.absolutePath}")
            return emptyList()
        }

        return rootDir.walk() // 自顶向下遍历
            .filter { it.isFile }
            .filter { file ->
                file.extension.lowercase() in ACCEPT_FILE_EXTENSIONS
            }
            .toList()
    }

    private fun fetchCover(
        file: File,
        provider: FileProviderFileSystem,
        writeableProvider: FileProviderFileSystem
    ): MediaFile? {
        // 先查找同路径下的同名图片
        val coverExtension = listOf("jpg", "jpeg", "png", "gif")
        for (ext in coverExtension) {
            var coverFile = File(file.parentFile, "${file.nameWithoutExtension}.${ext}")
            if (!coverFile.exists()) coverFile =
                File(file.parentFile, "${file.nameWithoutExtension}.${ext.uppercase()}")

            if (!coverFile.exists()) continue

            return MediaFile {
                sha256 = coverFile.sha256()
                objectKey = coverFile.relativeTo(file.parentFile).path
                mimeType = Files.probeContentType(coverFile.toPath())
                size = coverFile.length()
                width = null
                height = null
                ossProvider = null
                fsProvider = provider
            }
        }

        // 尝试从文件中解析
        // TODO))需要验证
        val audioTag = AudioFileIO.read(file)

        val artwork = audioTag.tag?.firstArtwork ?: return null
        val binaryData = artwork.binaryData
        if (binaryData == null || binaryData.isEmpty()) {
            return null
        }

        val mimeType = artwork.mimeType?.trim().takeIf { !it.isNullOrBlank() } ?: "image/jpeg"
        val extension = when (mimeType.lowercase()) {
            "image/jpeg", "image/jpg" -> "jpg"
            "image/png" -> "png"
            "image/gif" -> "gif"
            "image/webp" -> "webp"
            "image/bmp" -> "bmp"
            else -> "jpg"
        }

        val sha256 = MessageDigest.getInstance("SHA-256").digest(binaryData)
            .joinToString("") { "%02x".format(it) }

        val targetProvider = if (provider.readonly) writeableProvider else provider
        val objectKey = "covers/$sha256.$extension"
        val coverFile = File(targetProvider.parentPath, objectKey)

        if (!coverFile.exists()) {
            coverFile.parentFile?.mkdirs()
            coverFile.writeBytes(binaryData)
        }

        val width = artwork.width.takeIf { it > 0 }
        val height = artwork.height.takeIf { it > 0 }

        return MediaFile {
            this.sha256 = sha256
            this.objectKey = objectKey
            this.mimeType = Files.probeContentType(coverFile.toPath())
            this.size = binaryData.size.toLong()
            this.width = width
            this.height = height
            this.ossProvider = null
            this.fsProvider = targetProvider
        }
    }

    companion object {
        private val ACCEPT_FILE_EXTENSIONS = hashSetOf(
            "mp3", "wav", "ogg", "flac", "aac", "wma", "m4a"
        )
    }
}

data class ScanTaskRequest(
    val providerType: FileProviderType,
    val providerId: Long,
) : TaskRequest
