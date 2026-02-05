package com.coooolfan.unirhy.service.task

import com.coooolfan.unirhy.model.*
import com.coooolfan.unirhy.model.storage.FileProviderFileSystem
import com.coooolfan.unirhy.model.storage.FileProviderType
import com.coooolfan.unirhy.model.storage.readonly
import com.coooolfan.unirhy.service.task.cover.fetchCover
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

@Service
class ScanTaskService(
    private val sql: KSqlClient,
    private val asyncTaskManager: AsyncTaskManager,
) {

    private val logger = LoggerFactory.getLogger(ScanTaskService::class.java)

    fun submit(request: ScanTaskRequest) {
        asyncTaskManager.submit(TaskType.SCAN) {
            executeInternal(request)
        }
    }

    private fun executeInternal(request: ScanTaskRequest) {
        if (request.providerType != FileProviderType.FILE_SYSTEM) {
            error("Unsupported provider type: ${request.providerType}")
        }

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
                    artists().addBy {
                        name = tag?.getFirst(FieldKey.ARTIST).orEmpty()
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

        sql.transaction {
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

    private fun findAudioFilesRecursively(rootDir: File): List<File> {
        if (!rootDir.exists() || !rootDir.isDirectory) {
            logger.warn("Directory not found or is not a directory: ${rootDir.absolutePath}")
            return emptyList()
        }

        return rootDir.walk()
            .filter { it.isFile }
            .filter { file -> file.extension.lowercase() in ACCEPT_FILE_EXTENSIONS }
            .toList()
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
)
