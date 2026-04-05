package com.coooolfan.unirhy.sync.service

import com.coooolfan.unirhy.model.Asset
import com.coooolfan.unirhy.model.Recording
import com.coooolfan.unirhy.model.by
import com.coooolfan.unirhy.model.id
import com.coooolfan.unirhy.model.recordingId
import com.coooolfan.unirhy.sync.protocol.PlaybackSyncErrorCode
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.count
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.babyfish.jimmer.sql.kt.fetcher.newFetcher
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service

interface PlaybackSyncMediaCatalog {
    fun recordingExists(id: Long): Boolean

    fun recordingHasPlayableAudio(id: Long): Boolean
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

    override fun recordingHasPlayableAudio(id: Long): Boolean {
        return sql.createQuery(Asset::class) {
            where(table.recordingId eq id)
            select(table.fetch(ASSET_MEDIA_FETCHER))
        }.execute().any { asset ->
            asset.mediaFile.mimeType.startsWith("audio/")
        }
    }

    private companion object {
        private val ASSET_MEDIA_FETCHER = newFetcher(Asset::class).by {
            mediaFile {
                mimeType()
            }
        }
    }
}

@Service
class PlaybackSyncMediaResolver(
    private val mediaCatalog: PlaybackSyncMediaCatalog,
) {
    fun validatePlayableRecording(recordingId: Long) {
        if (!mediaCatalog.recordingExists(recordingId)) {
            throw PlaybackSyncProtocolException(
                code = PlaybackSyncErrorCode.RECORDING_NOT_FOUND,
                message = "Recording $recordingId not found",
                reason = PlaybackSyncErrorReason.RECORDING_NOT_FOUND,
            )
        }

        if (mediaCatalog.recordingHasPlayableAudio(recordingId)) {
            return
        }
        throw PlaybackSyncProtocolException(
            code = PlaybackSyncErrorCode.RECORDING_NOT_PLAYABLE,
            message = "Recording $recordingId is not playable",
            reason = PlaybackSyncErrorReason.RECORDING_NOT_PLAYABLE,
        )
    }
}
