package com.coooolfan.unirhy.service

import com.coooolfan.unirhy.error.CommonException
import com.coooolfan.unirhy.error.MediaFileException
import com.coooolfan.unirhy.error.RecordingException
import com.coooolfan.unirhy.model.Artist
import com.coooolfan.unirhy.model.MediaFile
import com.coooolfan.unirhy.model.Recording
import com.coooolfan.unirhy.model.SystemConfig
import com.coooolfan.unirhy.model.by
import com.coooolfan.unirhy.service.SystemConfigService.Companion.SYSTEM_CONFIG_ID
import com.coooolfan.unirhy.service.storage.StorageNodeObjectService
import com.coooolfan.unirhy.service.storage.bindProvider
import com.coooolfan.unirhy.service.storage.resolveWriteableStorageNode
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.fetcher.Fetcher
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.fetcher.newFetcher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@Service
class ArtworkService(
    private val sql: KSqlClient,
    private val storageObjects: StorageNodeObjectService,
) {
    @Transactional
    fun updateRecordingCover(id: Long, file: MultipartFile, fetcher: Fetcher<Recording>): Recording {
        if (sql.findById(Recording::class, id) == null) {
            throw RecordingException.NotFound()
        }

        return withStoredImage("recordings/$id", file) { mediaFile ->
            sql.saveCommand(
                Recording {
                    this.id = id
                    coverId = mediaFile.id
                },
                SaveMode.UPDATE_ONLY,
            ).execute()
            sql.findById(fetcher, id) ?: throw RecordingException.NotFound()
        }
    }

    @Transactional
    fun removeRecordingCover(id: Long, fetcher: Fetcher<Recording>): Recording {
        if (sql.findById(Recording::class, id) == null) {
            throw RecordingException.NotFound()
        }
        sql.saveCommand(
            Recording {
                this.id = id
                coverId = null
            },
            SaveMode.UPDATE_ONLY,
        ).execute()
        return sql.findById(fetcher, id) ?: throw RecordingException.NotFound()
    }

    @Transactional
    fun updateArtistAvatar(id: Long, file: MultipartFile, fetcher: Fetcher<Artist>): Artist {
        if (sql.findById(Artist::class, id) == null) {
            throw CommonException.NotFound()
        }

        return withStoredImage("artists/$id", file) { mediaFile ->
            sql.saveCommand(
                Artist {
                    this.id = id
                    avatarId = mediaFile.id
                },
                SaveMode.UPDATE_ONLY,
            ).execute()
            sql.findById(fetcher, id) ?: throw CommonException.NotFound()
        }
    }

    @Transactional
    fun removeArtistAvatar(id: Long, fetcher: Fetcher<Artist>): Artist {
        if (sql.findById(Artist::class, id) == null) {
            throw CommonException.NotFound()
        }
        sql.saveCommand(
            Artist {
                this.id = id
                avatarId = null
            },
            SaveMode.UPDATE_ONLY,
        ).execute()
        return sql.findById(fetcher, id) ?: throw CommonException.NotFound()
    }

    private fun <T> withStoredImage(
        ownerPath: String,
        file: MultipartFile,
        block: (MediaFile) -> T,
    ): T {
        val image = readImage(file)
        val node = sql.findOneById(SYSTEM_CONFIG_FETCHER, SYSTEM_CONFIG_ID)
            .resolveWriteableStorageNode(storageObjects)
        if (node.readonly) {
            throw MediaFileException.InvalidStorageProvider("System storage provider is readonly")
        }

        val objectKey = "artwork/$ownerPath/${UUID.randomUUID()}"
        storageObjects.write(node, objectKey, image.bytes, image.mimeType)

        return try {
            val mediaFile = sql.saveCommand(
                MediaFile {
                    this.objectKey = objectKey
                    mimeType = image.mimeType
                    size = image.bytes.size.toLong()
                    width = image.width
                    height = image.height
                    bindProvider(node)
                },
                SaveMode.INSERT_ONLY,
            ).execute().modifiedEntity
            block(mediaFile)
        } catch (ex: Exception) {
            runCatching { storageObjects.delete(node, objectKey) }
            throw ex
        }
    }

    private fun readImage(file: MultipartFile): UploadedImage {
        if (file.isEmpty) {
            throw MediaFileException.InvalidImage("Image file is empty")
        }
        if (file.size > MAX_IMAGE_SIZE_BYTES) {
            throw MediaFileException.ImageTooLarge("Image file exceeds 10 MiB")
        }

        val bytes = try {
            file.bytes
        } catch (ex: Exception) {
            throw MediaFileException.InvalidImage("Image file could not be read", ex)
        }
        if (bytes.isEmpty()) {
            throw MediaFileException.InvalidImage("Image file is empty")
        }
        if (bytes.size > MAX_IMAGE_SIZE_BYTES) {
            throw MediaFileException.ImageTooLarge("Image file exceeds 10 MiB")
        }

        return UploadedImage(
            bytes = bytes,
            mimeType = file.contentType.orEmpty().ifBlank { "application/octet-stream" },
            width = null,
            height = null,
        )
    }

    private data class UploadedImage(
        val bytes: ByteArray,
        val mimeType: String,
        val width: Int?,
        val height: Int?,
    )

    private companion object {
        const val MAX_IMAGE_SIZE_BYTES = 10 * 1024 * 1024

        val SYSTEM_CONFIG_FETCHER: Fetcher<SystemConfig> = newFetcher(SystemConfig::class).by {
            fsProvider {
                allScalarFields()
            }
            ossProvider {
                allScalarFields()
            }
        }
    }
}
