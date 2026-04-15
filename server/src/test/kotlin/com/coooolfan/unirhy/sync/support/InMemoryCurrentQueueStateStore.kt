package com.coooolfan.unirhy.sync.support

import com.coooolfan.unirhy.sync.model.AccountPlayQueueState
import com.coooolfan.unirhy.sync.service.CurrentQueueStateStore

class InMemoryCurrentQueueStateStore : CurrentQueueStateStore {
    private val states = linkedMapOf<Long, AccountPlayQueueState>()
    var loadCount: Int = 0
        private set

    override fun load(accountId: Long): AccountPlayQueueState? {
        loadCount += 1
        return states[accountId]?.deepCopy()
    }

    override fun save(
        expectedVersion: Long,
        state: AccountPlayQueueState,
    ): Boolean {
        val existing = states[state.accountId]
        return when {
            existing == null && expectedVersion == 0L -> {
                states[state.accountId] = state.deepCopy()
                true
            }

            existing != null && existing.version == expectedVersion -> {
                states[state.accountId] = state.deepCopy()
                true
            }

            else -> false
        }
    }

    private fun AccountPlayQueueState.deepCopy(): AccountPlayQueueState {
        return copy(
            recordingIds = recordingIds.toMutableList(),
            shuffleIndices = shuffleIndices.toMutableList(),
        )
    }
}
