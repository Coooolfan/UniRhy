package com.coooolfan.unirhy.sync.support

import com.coooolfan.unirhy.sync.model.AccountCurrentQueueState
import com.coooolfan.unirhy.sync.service.CurrentQueueStateStore

class InMemoryCurrentQueueStateStore : CurrentQueueStateStore {
    private val states = linkedMapOf<Long, AccountCurrentQueueState>()
    var loadCount: Int = 0
        private set

    override fun load(accountId: Long): AccountCurrentQueueState? {
        loadCount += 1
        return states[accountId]?.deepCopy()
    }

    override fun upsert(state: AccountCurrentQueueState) {
        states[state.accountId] = state.deepCopy()
    }

    private fun AccountCurrentQueueState.deepCopy(): AccountCurrentQueueState {
        return copy(
            items = items.map { it.copy() }.toMutableList(),
            shuffleEntryIds = shuffleEntryIds.toMutableList(),
        )
    }
}
