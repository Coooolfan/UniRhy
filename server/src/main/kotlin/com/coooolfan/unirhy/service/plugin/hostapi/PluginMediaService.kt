package com.coooolfan.unirhy.service.plugin.hostapi

import com.coooolfan.unirhy.model.*
import com.coooolfan.unirhy.service.storage.FileSystemStorageNode
import com.coooolfan.unirhy.service.storage.OssStorageNode
import com.coooolfan.unirhy.service.storage.StorageNode
import com.coooolfan.unirhy.service.storage.StorageNodeObjectService
import com.coooolfan.unirhy.service.storage.bindProvider
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.fetcher.Fetcher
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.babyfish.jimmer.sql.kt.fetcher.newFetcher
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

internal data class HostStorageNodeView(
    val type: String,
    val id: Long,
)

internal data class HostMediaFileView(
    val id: Long,
    val objectKey: String,
    val mimeType: String,
    val size: Long,
    val width: Int?,
    val height: Int?,
    val node: HostStorageNodeView,
)

internal data class HostAssetView(
    val id: Long,
    val recordingId: Long,
    val mediaFile: HostMediaFileView,
    val comment: String,
)

@Service
class PluginMediaService(
    private val sql: KSqlClient,
    private val jdbc: NamedParameterJdbcTemplate,
    private val storageObjects: StorageNodeObjectService,
) {
    internal fun getMediaFile(id: Long): HostMediaFileView = findMediaFile(id)?.toHostView()
        ?: notFound("Media file $id was not found")

    @Transactional
    internal fun createMediaFile(node: StorageNode, objectKey: String, mimeType: String): HostMediaFileView {
        val stat = storageObjects.statOrNull(node, objectKey)
            ?: notFound("Storage object '$objectKey' was not found")
        val created = sql.saveCommand(
            MediaFile {
                this.objectKey = objectKey
                this.mimeType = mimeType
                size = stat.size
                width = null
                height = null
                bindProvider(node)
            },
            SaveMode.INSERT_ONLY,
        ).execute(MEDIA_FILE_FETCHER).modifiedEntity
        return created.toHostView()
    }

    @Transactional
    internal fun deleteMediaFile(id: Long) {
        if (findMediaFile(id) == null) notFound("Media file $id was not found")
        val referenced = jdbc.queryForObject(
            """
            SELECT EXISTS (SELECT 1 FROM public.asset WHERE media_file_id = :id)
                OR EXISTS (SELECT 1 FROM public.recording WHERE cover_id = :id)
                OR EXISTS (SELECT 1 FROM public.album WHERE cover_id = :id)
                OR EXISTS (SELECT 1 FROM public.artist WHERE avatar_id = :id)
                OR EXISTS (SELECT 1 FROM public.account WHERE avatar_id = :id)
            """.trimIndent(),
            MapSqlParameterSource("id", id),
            Boolean::class.java,
        ) == true
        if (referenced) conflict("Media file $id is still referenced")
        sql.deleteById(MediaFile::class, id)
    }

    internal fun listAssets(recordingId: Long): List<HostAssetView> {
        if (sql.findById(Recording::class, recordingId) == null) {
            notFound("Recording $recordingId was not found")
        }
        return sql.createQuery(Asset::class) {
            where(table.recording.id eq recordingId)
            orderBy(table.id)
            select(table.fetch(ASSET_FETCHER))
        }.execute().map { it.toHostView() }
    }

    @Transactional
    internal fun createAsset(recordingId: Long, mediaFileId: Long, comment: String): HostAssetView {
        if (sql.findById(Recording::class, recordingId) == null) {
            notFound("Recording $recordingId was not found")
        }
        if (findMediaFile(mediaFileId) == null) {
            notFound("Media file $mediaFileId was not found")
        }
        val created = sql.saveCommand(
            Asset {
                recording = Recording { id = recordingId }
                mediaFile = MediaFile { id = mediaFileId }
                this.comment = comment
            },
            SaveMode.INSERT_ONLY,
        ).execute(ASSET_FETCHER).modifiedEntity
        return created.toHostView()
    }

    @Transactional
    internal fun deleteAsset(id: Long) {
        val exists = sql.createQuery(Asset::class) {
            where(table.id eq id)
            selectCount()
        }.execute().first() > 0L
        if (!exists) notFound("Asset $id was not found")
        sql.deleteById(Asset::class, id)
    }

    private fun findMediaFile(id: Long): MediaFile? =
        sql.findById(MEDIA_FILE_FETCHER, id)

    private fun MediaFile.toHostView(): HostMediaFileView {
        val node = when {
            fsProvider != null -> HostStorageNodeView("FS", fsProvider!!.id)
            ossProvider != null -> HostStorageNodeView("OSS", ossProvider!!.id)
            else -> throw IllegalStateException("Media file $id has no storage node")
        }
        return HostMediaFileView(id, objectKey, mimeType, size, width, height, node)
    }

    private fun Asset.toHostView(): HostAssetView =
        HostAssetView(id, recording.id, mediaFile.toHostView(), comment)

    companion object {
        private val MEDIA_FILE_FETCHER: Fetcher<MediaFile> = newFetcher(MediaFile::class).by {
            allScalarFields()
            fsProvider()
            ossProvider()
        }

        private val ASSET_FETCHER: Fetcher<Asset> = newFetcher(Asset::class).by {
            allScalarFields()
            recording()
            mediaFile(MEDIA_FILE_FETCHER)
        }
    }
}
