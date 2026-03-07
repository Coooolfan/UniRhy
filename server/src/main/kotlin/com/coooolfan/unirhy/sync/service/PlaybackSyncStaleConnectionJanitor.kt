package com.coooolfan.unirhy.sync.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import java.util.concurrent.ScheduledFuture

@Component
class PlaybackSyncStaleConnectionJanitor(
    private val deviceRuntimeService: DeviceRuntimeService,
    private val playbackSchedulerService: PlaybackSchedulerService,
    private val sessionRemovalCoordinator: PlaybackSyncSessionRemovalCoordinator,
) {
    private val logger = LoggerFactory.getLogger(PlaybackSyncStaleConnectionJanitor::class.java)
    private var future: ScheduledFuture<*>? = null

    @PostConstruct
    fun start() {
        future = playbackSchedulerService.scheduleWithFixedDelay(
            initialDelayMs = PlaybackSchedulerService.STALE_SWEEP_INTERVAL_MS,
            delayMs = PlaybackSchedulerService.STALE_SWEEP_INTERVAL_MS,
            task = ::sweepStaleConnections,
        )
    }

    @PreDestroy
    fun stop() {
        future?.cancel(false)
    }

    fun sweepStaleConnections() {
        runCatching {
            val nowMs = playbackSchedulerService.nowMs()
            deviceRuntimeService.cleanupStaleConnections(
                nowMs = nowMs,
                staleThresholdMs = PlaybackSchedulerService.STALE_THRESHOLD_MS,
            ).forEach { removal ->
                removal.context.session.close(STALE_CONNECTION_CLOSE_STATUS)
                sessionRemovalCoordinator.handleRemoval(
                    removal = removal,
                    reason = "stale_connection",
                    nowMs = nowMs,
                )
            }
        }.onFailure { ex ->
            logger.warn("Failed to sweep stale playback sync connections", ex)
        }
    }

    private companion object {
        private val STALE_CONNECTION_CLOSE_STATUS =
            CloseStatus.POLICY_VIOLATION.withReason("stale_connection")
    }
}
