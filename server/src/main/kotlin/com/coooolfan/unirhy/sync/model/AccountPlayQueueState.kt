package com.coooolfan.unirhy.sync.model

import com.coooolfan.unirhy.sync.protocol.PlaybackStatus
import com.coooolfan.unirhy.sync.protocol.PlaybackStrategy
import com.coooolfan.unirhy.sync.protocol.StopStrategy

data class AccountPlayQueueState(
    val accountId: Long,
    val recordingIds: MutableList<Long>,
    var currentIndex: Int = 0,
    val shuffleIndices: MutableList<Int> = mutableListOf(),
    var playbackStrategy: PlaybackStrategy = PlaybackStrategy.SEQUENTIAL,
    var stopStrategy: StopStrategy = StopStrategy.LIST,
    var playbackStatus: PlaybackStatus = PlaybackStatus.PAUSED,
    var positionMs: Long = 0L,
    var serverTimeToExecuteMs: Long = 0L,
    var version: Long = 0L,
    var updatedAtMs: Long,
) {
    companion object {
        fun initial(
            accountId: Long,
            nowMs: Long,
        ): AccountPlayQueueState = AccountPlayQueueState(
            accountId = accountId,
            recordingIds = mutableListOf(),
            updatedAtMs = nowMs,
        )
    }
}
