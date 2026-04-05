package com.coooolfan.unirhy.sync.model

import com.coooolfan.unirhy.sync.protocol.PlaybackStrategy
import com.coooolfan.unirhy.sync.protocol.StopStrategy

data class AccountCurrentQueueState(
    val accountId: Long,
    val items: MutableList<CurrentQueueEntry>,
    var currentEntryId: Long? = null,
    var playbackStrategy: PlaybackStrategy = PlaybackStrategy.SEQUENTIAL,
    var stopStrategy: StopStrategy = StopStrategy.LIST,
    val shuffleEntryIds: MutableList<Long> = mutableListOf(),
    var nextEntryId: Long = 1L,
    var version: Long = 0L,
    var updatedAtMs: Long,
) {
    companion object {
        fun initial(
            accountId: Long,
            nowMs: Long,
        ): AccountCurrentQueueState = AccountCurrentQueueState(
            accountId = accountId,
            items = mutableListOf(),
            currentEntryId = null,
            nextEntryId = 1L,
            version = 0L,
            updatedAtMs = nowMs,
        )
    }
}
