package com.unirhy.e2e.support

import java.time.Duration

object E2eAwait {
    fun until(
        timeout: Duration,
        interval: Duration = Duration.ofMillis(500),
        description: String,
        condition: () -> Boolean,
    ) {
        val deadline = System.nanoTime() + timeout.toNanos()
        while (System.nanoTime() < deadline) {
            if (condition()) {
                return
            }
            Thread.sleep(interval.toMillis())
        }
        throw IllegalStateException("$description did not complete within ${timeout.seconds} seconds")
    }
}
