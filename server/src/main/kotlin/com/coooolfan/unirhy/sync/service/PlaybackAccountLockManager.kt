package com.coooolfan.unirhy.sync.service

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

internal data class AccountLockExecution<T>(
    val value: T,
)

internal fun <T> unwrapAccountLockExecution(
    accountId: Long,
    execution: AccountLockExecution<T>?,
): T {
    if (execution == null) {
        error("Account lock transaction returned null for accountId=$accountId")
    }
    return execution.value
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
) : PlaybackAccountScope {
    override fun <T> withAccountLock(
        accountId: Long,
        action: () -> T,
    ): T {
        val execution = transactionTemplate.execute<AccountLockExecution<T>> { _ ->
            val params = MapSqlParameterSource()
                .addValue("accountId", accountId)
            jdbc.query(
                "SELECT pg_advisory_xact_lock(CAST(:accountId AS bigint))",
                params,
            ) { _, _ -> }
            AccountLockExecution(action())
        }
        return unwrapAccountLockExecution(accountId, execution)
    }
}
