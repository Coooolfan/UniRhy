package com.coooolfan.unirhy.service

import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.ScheduledFuture

/**
 * 周期驱动登录交接的过期与清理。
 *
 * 使用独立的单线程 scheduler：任务分发的 `taskScheduler` 只承载 500ms tick，
 * 清理作业的事务性 UPDATE/DELETE 不应与其争用同一条线程。
 */
@Component
class LoginTransferCleanupJob(
    private val loginTransferService: LoginTransferService,
    @param:Value("\${unirhy.login-transfer.cleanup-interval-ms:60000}")
    private val cleanupIntervalMs: Long,
) {
    private val logger = LoggerFactory.getLogger(LoginTransferCleanupJob::class.java)
    private val scheduler = ThreadPoolTaskScheduler().apply {
        poolSize = 1
        setThreadNamePrefix("login-transfer-cleanup-")
    }
    private var future: ScheduledFuture<*>? = null

    @PostConstruct
    fun start() {
        scheduler.initialize()
        val delay = Duration.ofMillis(cleanupIntervalMs)
        future = scheduler.scheduleWithFixedDelay(::runCleanup, delay)
    }

    @PreDestroy
    fun stop() {
        future?.cancel(false)
        scheduler.shutdown()
    }

    private fun runCleanup() {
        runCatching { loginTransferService.cleanup() }
            .onFailure { ex -> logger.warn("Failed to clean up login transfers", ex) }
    }
}
