package com.coooolfan.unirhy.sync.support

import com.coooolfan.unirhy.sync.model.AccountPlaybackState
import com.coooolfan.unirhy.sync.service.PlaybackResumeStateStore

class InMemoryPlaybackResumeStateStore : PlaybackResumeStateStore {
    private val states = linkedMapOf<Long, AccountPlaybackState>()
    var loadCount: Int = 0
        private set

    override fun load(accountId: Long): AccountPlaybackState? {
        loadCount += 1
        return states[accountId]
    }

    override fun upsert(state: AccountPlaybackState) {
        states[state.accountId] = state.copy()
    }
}
