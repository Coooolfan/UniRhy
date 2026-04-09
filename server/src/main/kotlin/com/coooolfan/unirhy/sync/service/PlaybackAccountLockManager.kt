package com.coooolfan.unirhy.sync.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

interface PlaybackAccountScope {
    fun <T> withAccountLock(
        accountId: Long,
        action: () -> T,
    ): T
}

class PlaybackAccountLockManager : PlaybackAccountScope {
    private val locks = ConcurrentHashMap<Long, ReentrantLock>()

    override fun <T> withAccountLock(
        accountId: Long,
        action: () -> T,
    ): T {
        val lock = locks.computeIfAbsent(accountId) { ReentrantLock() }
        return lock.withLock(action)
    }
}

@Service
@Primary
class JdbcPlaybackAccountScope(
    private val jdbc: NamedParameterJdbcTemplate,
    private val transactionTemplate: TransactionTemplate,
    @Value("\${unirhy.sync.lock-namespace:20260409}")
    private val lockNamespace: Int,
) : PlaybackAccountScope {
    override fun <T> withAccountLock(
        accountId: Long,
        action: () -> T,
    ): T {
        return transactionTemplate.execute { _ ->
            val params = MapSqlParameterSource()
                .addValue("namespace", lockNamespace)
                .addValue("accountId", accountId)
            jdbc.query(
                "SELECT pg_advisory_xact_lock(:namespace, :accountId)",
                params,
            ) { _, _ -> }
            action()
        } ?: error("Account lock transaction returned null for accountId=$accountId")
    }
}
