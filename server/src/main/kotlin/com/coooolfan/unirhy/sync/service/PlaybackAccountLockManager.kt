package com.coooolfan.unirhy.sync.service

import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Service
class PlaybackAccountLockManager {
    private val locks = ConcurrentHashMap<Long, ReentrantLock>()

    fun <T> withAccountLock(
        accountId: Long,
        action: () -> T,
    ): T {
        val lock = locks.computeIfAbsent(accountId) { ReentrantLock() }
        return lock.withLock(action)
    }
}
