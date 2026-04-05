package com.coooolfan.unirhy.config

import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals

class ExponentialBackoffPollingTriggerTest {

    @Test
    fun `idle runs back off exponentially up to one minute`() {
        val trigger = ExponentialBackoffPollingTrigger(
            minDelay = Duration.ofMillis(500),
            maxDelay = Duration.ofMinutes(1),
        )

        val observedDelays = buildList {
            repeat(9) {
                trigger.recordResult(consumedTask = false)
                add(trigger.currentDelayMs())
            }
        }

        assertEquals(
            listOf(500L, 1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 32_000L, 60_000L, 60_000L),
            observedDelays,
        )
    }

    @Test
    fun `consuming a task resets the next delay to minimum`() {
        val trigger = ExponentialBackoffPollingTrigger(
            minDelay = Duration.ofMillis(500),
            maxDelay = Duration.ofMinutes(1),
        )

        repeat(4) {
            trigger.recordResult(consumedTask = false)
        }
        assertEquals(4_000L, trigger.currentDelayMs())

        trigger.recordResult(consumedTask = true)

        assertEquals(500L, trigger.currentDelayMs())
    }
}
