package com.coooolfan.unirhy.sync.service

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

@Component
class PlaybackSyncJobWorker(
    private val jobStore: PlaybackSyncJobStore,
    private val playCoordinator: PlaybackPlayCoordinator,
    private val autoAdvanceService: PlaybackAutoAdvanceService,
    private val objectMapper: ObjectMapper,
    @Qualifier("playbackSyncScheduledExecutor")
    private val scheduler: ScheduledExecutorService,
) {
    private val logger = LoggerFactory.getLogger(PlaybackSyncJobWorker::class.java)
    private var future: ScheduledFuture<*>? = null

    @PostConstruct
    fun start() {
        future = scheduler.scheduleWithFixedDelay(
            ::drain,
            JOB_POLL_INTERVAL_MS,
            JOB_POLL_INTERVAL_MS,
            TimeUnit.MILLISECONDS,
        )
    }

    @PreDestroy
    fun stop() {
        future?.cancel(false)
    }

    fun drain() {
        val nowMs = System.currentTimeMillis()
        jobStore.claimDueJobs(nowMs, CLAIM_LIMIT).forEach { job ->
            runCatching {
                when (job.jobType) {
                    PlaybackSyncJobType.PENDING_PLAY_TIMEOUT -> {
                        val payload = objectMapper.readTree(job.payload)
                        playCoordinator.handlePendingPlayTimeout(
                            accountId = job.accountId,
                            commandId = payload.required("commandId").asText(),
                        )
                    }

                    PlaybackSyncJobType.AUTO_ADVANCE -> autoAdvanceService.advanceToNext(job.accountId)
                }
                jobStore.markCompleted(job.id)
            }.onFailure { ex ->
                logger.warn("Failed to execute playback sync job id={}, type={}", job.id, job.jobType, ex)
                jobStore.markFailed(job.id)
            }
        }
    }

    private companion object {
        private const val CLAIM_LIMIT = 32
        private const val JOB_POLL_INTERVAL_MS = 200L
    }
}
