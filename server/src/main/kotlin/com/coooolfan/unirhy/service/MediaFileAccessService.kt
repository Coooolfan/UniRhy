package com.coooolfan.unirhy.service

import com.coooolfan.unirhy.model.MediaFile
import com.coooolfan.unirhy.model.by
import com.coooolfan.unirhy.model.id
import com.coooolfan.unirhy.model.storage.FileProviderType
import com.coooolfan.unirhy.service.storage.StorageNode
import com.coooolfan.unirhy.service.storage.StorageNodeObjectService
import org.babyfish.jimmer.sql.fetcher.Fetcher
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.babyfish.jimmer.sql.kt.fetcher.newFetcher
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class MediaFileAccessService(
    private val sql: KSqlClient,
    private val storageObjects: StorageNodeObjectService,
) {

    fun load(id: Long): ResolvedMediaFile {
        val mediaFile = findMediaFile(id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Media file not found")

        val node = try {
            resolveStorageNode(mediaFile)
        } catch (_: IllegalStateException) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid storage provider")
        }
        val stat = try {
            storageObjects.stat(node, mediaFile.objectKey)
        } catch (_: RuntimeException) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "File not found")
        }

        return ResolvedMediaFile(
            mediaFile = mediaFile,
            fileName = stat.fileName,
            size = stat.size,
            lastModified = stat.lastModified,
            readAll = { storageObjects.readAll(node, mediaFile.objectKey) },
            readRange = { start, endInclusive ->
                storageObjects.readRange(node, mediaFile.objectKey, start, endInclusive)
            },
        )
    }

    private fun findMediaFile(id: Long): MediaFile? {
        return sql.executeQuery(MediaFile::class) {
            where(table.id eq id)
            select(table.fetch(MEDIA_FILE_FETCHER))
        }.firstOrNull()
    }

    private fun resolveStorageNode(mediaFile: MediaFile): StorageNode {
        mediaFile.fsProvider?.let {
            return storageObjects.resolve(FileProviderType.FILE_SYSTEM, it.id)
        }
        mediaFile.ossProvider?.let {
            return storageObjects.resolve(FileProviderType.OSS, it.id)
        }
        error("Media file has no storage provider")
    }

    companion object {
        private val MEDIA_FILE_FETCHER: Fetcher<MediaFile> = newFetcher(MediaFile::class).by {
            allScalarFields()
            ossProvider {
                allScalarFields()
            }
            fsProvider {
                allScalarFields()
            }
        }
    }
}
