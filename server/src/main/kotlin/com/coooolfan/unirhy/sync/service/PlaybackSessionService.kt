package com.coooolfan.unirhy.sync.service

import com.coooolfan.unirhy.sync.model.AccountPlaybackState
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class PlaybackSessionService(
    private val lockManager: PlaybackAccountLockManager,
) {
    private val states = ConcurrentHashMap<Long, AccountPlaybackState>()

    fun getOrCreateState(accountId: Long): AccountPlaybackState {
        return lockManager.withAccountLock(accountId) {
            states.computeIfAbsent(accountId) {
                AccountPlaybackState.initial(accountId = accountId, nowMs = System.currentTimeMillis())
            }
        }
    }
}
