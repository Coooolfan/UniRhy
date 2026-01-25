package com.coooolfan.unirhy.service.task

import com.coooolfan.unirhy.model.Asset
import com.coooolfan.unirhy.model.MediaFile
import com.coooolfan.unirhy.model.Work
import com.coooolfan.unirhy.model.addBy
import com.coooolfan.unirhy.model.storage.FileProviderFileSystem
import com.coooolfan.unirhy.model.storage.FileProviderType
import com.coooolfan.unirhy.utils.sha256
import org.babyfish.jimmer.sql.ast.mutation.AssociatedSaveMode
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
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

        val rootDir = File(provider.parentPath)

        val mediaFiles = findAudioFilesRecursively(rootDir)

        val works = mutableListOf<Work>()

        mediaFiles.forEach { file ->
            val relativePath = file.relativeTo(rootDir).path
            logger.info("Found file: $relativePath")
            works.add(Work {
                title = file.nameWithoutExtension
                recordings().addBy {
                    kind = "CD"
                    label = "CD"
                    title = file.nameWithoutExtension
                    comment = "load from local file: $relativePath"
                    cover = null
                    assets().addBy {
                        comment = "load from local file: $relativePath"
                        mediaFile {
                            sha256 = file.sha256()
                            objectKey = relativePath
                            mimeType = "audio/${file.extension.lowercase()}"
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