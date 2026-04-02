package com.coooolfan.unirhy.sync.model

data class AccountCurrentQueueState(
    val accountId: Long,
    val items: MutableList<CurrentQueueEntry>,
    var currentEntryId: Long? = null,
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
