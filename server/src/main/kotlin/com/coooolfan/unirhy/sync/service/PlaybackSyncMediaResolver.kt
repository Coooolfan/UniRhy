package com.coooolfan.unirhy.sync.service

import com.coooolfan.unirhy.model.Asset
import com.coooolfan.unirhy.model.MediaFile
import com.coooolfan.unirhy.model.Recording
import com.coooolfan.unirhy.model.id
import com.coooolfan.unirhy.model.mediaFileId
import com.coooolfan.unirhy.model.recordingId
import com.coooolfan.unirhy.sync.protocol.PlaybackSyncErrorCode
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.count
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service

data class PlaybackSyncResolvedMedia(
    val recordingId: Long,
    val mediaFileId: Long,
)

interface PlaybackSyncMediaCatalog {
    fun recordingExists(id: Long): Boolean

    fun mediaFileExists(id: Long): Boolean

    fun recordingHasMediaFile(
        recordingId: Long,
        mediaFileId: Long,
    ): Boolean
}

@Component
class JimmerPlaybackSyncMediaCatalog(
    private val sql: KSqlClient,
) : PlaybackSyncMediaCatalog {
    override fun recordingExists(id: Long): Boolean {
        return sql.createQuery(Recording::class) {
            where(table.id eq id)
            select(count(table.id))
        }.execute().first() > 0
    }

    override fun mediaFileExists(id: Long): Boolean {
        return sql.createQuery(MediaFile::class) {
            where(table.id eq id)
            select(count(table.id))
        }.execute().first() > 0
    }

    override fun recordingHasMediaFile(
        recordingId: Long,
        mediaFileId: Long,
    ): Boolean {
        return sql.createQuery(Asset::class) {
            where(table.recordingId eq recordingId)
            where(table.mediaFileId eq mediaFileId)
            select(count(table.id))
        }.execute().first() > 0
    }
}

@Service
class PlaybackSyncMediaResolver(
    private val mediaCatalog: PlaybackSyncMediaCatalog,
) {
    fun resolve(
        recordingId: Long,
        mediaFileId: Long,
    ): PlaybackSyncResolvedMedia {
        if (mediaCatalog.recordingHasMediaFile(recordingId, mediaFileId)) {
            return PlaybackSyncResolvedMedia(
                recordingId = recordingId,
                mediaFileId = mediaFileId,
            )
        }

        if (!mediaCatalog.recordingExists(recordingId)) {
            throw PlaybackSyncProtocolException(
                code = PlaybackSyncErrorCode.RECORDING_NOT_FOUND,
                message = "Recording $recordingId not found",
                reason = PlaybackSyncErrorReason.RECORDING_NOT_FOUND,
            )
        }

        if (!mediaCatalog.mediaFileExists(mediaFileId)) {
            throw PlaybackSyncProtocolException(
                code = PlaybackSyncErrorCode.MEDIA_FILE_NOT_FOUND,
                message = "Media file $mediaFileId not found",
                reason = PlaybackSyncErrorReason.MEDIA_FILE_NOT_FOUND,
            )
        }
        throw PlaybackSyncProtocolException(
            code = PlaybackSyncErrorCode.RECORDING_NOT_PLAYABLE,
            message = "Recording $recordingId has no playable audio asset for media file $mediaFileId",
            reason = PlaybackSyncErrorReason.RECORDING_NOT_PLAYABLE,
        )
    }
}
