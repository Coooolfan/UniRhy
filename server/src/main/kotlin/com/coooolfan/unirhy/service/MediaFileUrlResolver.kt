package com.coooolfan.unirhy.service

import com.coooolfan.unirhy.model.MediaFile
import com.coooolfan.unirhy.model.id
import com.coooolfan.unirhy.model.storage.FileProviderOss
import com.coooolfan.unirhy.model.storage.id
import com.coooolfan.unirhy.service.storage.OssStorageNode
import com.coooolfan.unirhy.service.storage.StorageNodeObjectService
import org.babyfish.jimmer.sql.TransientResolver
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.valueIn
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class MediaFileUrlResolver(
    private val urlSigner: MediaUrlSigner,
    private val sql: KSqlClient,
    private val storageObjects: StorageNodeObjectService,
    @Value("\${unirhy.media.url-ttl-seconds:7200}")
    private val ttlSeconds: Long,
) : TransientResolver<Long, String> {

    override fun resolve(ids: Collection<Long>): Map<Long, String> {
        if (ids.isEmpty()) {
            return emptyMap()
        }

        // Jimmer 不允许在Resolver中使用fetcher，这里手动拼一下
        val mediaFiles = sql.executeQuery(MediaFile::class) {
            where(table.id valueIn ids)
            select(table)
        }

        val ossProviderIds = mediaFiles.mapNotNull { it.ossProviderId }.toSet()
        val ossProviders = if (ossProviderIds.isEmpty()) {
            emptyMap()
        } else {
            sql.executeQuery(FileProviderOss::class) {
                where(table.id valueIn ossProviderIds)
                select(table)
            }.associateBy { it.id }
        }

        val resolved = mediaFiles.associate { mediaFile ->
            mediaFile.id to directUrlOrBackendPath(mediaFile, ossProviders[mediaFile.ossProviderId])
        }
        return ids.associateWith { id -> resolved[id] ?: urlSigner.generatePresignedPath(id) }
    }

    private fun directUrlOrBackendPath(mediaFile: MediaFile, ossProvider: FileProviderOss?): String {
        if (ossProvider != null) {
            return storageObjects.directReadUrl(OssStorageNode(ossProvider), mediaFile.objectKey, ttlSeconds)
        }
        return urlSigner.generatePresignedPath(mediaFile.id)
    }
}
