package com.coooolfan.unirhy.sync.model

import com.coooolfan.unirhy.sync.protocol.PlaybackStatus

data class AccountPlaybackState(
    val accountId: Long,
    val status: PlaybackStatus,
    val recordingId: Long? = null,
    val positionSeconds: Double,
    val serverTimeToExecuteMs: Long,
    val version: Long,
    val updatedAtMs: Long,
) {
    companion object {
        fun initial(
            accountId: Long,
            nowMs: Long,
        ): AccountPlaybackState = AccountPlaybackState(
            accountId = accountId,
            status = PlaybackStatus.PAUSED,
            recordingId = null,
            positionSeconds = 0.0,
            serverTimeToExecuteMs = 0L,
            version = 0L,
            updatedAtMs = nowMs,
        )
    }
}
