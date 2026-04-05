package com.coooolfan.unirhy.config

import org.springframework.scheduling.Trigger
import org.springframework.scheduling.TriggerContext
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

class ExponentialBackoffPollingTrigger(
    private val minDelay: Duration,
    private val maxDelay: Duration,
) : Trigger {

    private val minDelayMs = minDelay.toMillis()
    private val maxDelayMs = maxDelay.toMillis()
    private val consecutiveIdleRuns = AtomicInteger(0)

    init {
        require(minDelayMs > 0) { "minDelay must be positive" }
        require(maxDelayMs >= minDelayMs) { "maxDelay must be greater than or equal to minDelay" }
    }

    override fun nextExecution(triggerContext: TriggerContext): Instant {
        val lastCompletion = triggerContext.lastCompletion() ?: return Instant.now()
        return lastCompletion.plusMillis(currentDelayMs())
    }

    fun recordResult(consumedTask: Boolean) {
        if (consumedTask) {
            consecutiveIdleRuns.set(0)
            return
        }
        consecutiveIdleRuns.updateAndGet { previous ->
            if (previous == Int.MAX_VALUE) Int.MAX_VALUE else previous + 1
        }
    }

    fun currentDelayMs(): Long {
        val exponent = maxOf(0, consecutiveIdleRuns.get() - 1)
        var delayMs = minDelayMs
        repeat(exponent) {
            if (delayMs >= maxDelayMs) {
                return maxDelayMs
            }
            delayMs = (delayMs * 2).coerceAtMost(maxDelayMs)
        }
        return delayMs
    }
}
