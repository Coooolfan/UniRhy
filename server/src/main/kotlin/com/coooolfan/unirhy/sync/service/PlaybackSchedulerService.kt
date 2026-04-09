package com.coooolfan.unirhy.sync.service

import com.coooolfan.unirhy.sync.model.DeviceRuntimeState
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.roundToLong

@Service
class PlaybackSchedulerService(
    private val deviceRuntimeService: DeviceRuntimeService,
    private val timeProvider: PlaybackSyncTimeProvider = SystemPlaybackSyncTimeProvider(),
    private val jobStore: PlaybackSyncJobStore = InMemoryPlaybackSyncJobStore(),
    @Qualifier("playbackSyncScheduledExecutor")
    private val scheduler: ScheduledExecutorService,
) {
    fun calculateScheduleDelayMs(accountId: Long): Long {
        return calculateScheduleDelayMs(
            deviceRuntimeService.getActiveRuntimeSnapshot(accountId).runtimeStates,
        )
    }

    fun calculateScheduleDelayMs(runtimeStates: Collection<DeviceRuntimeState>): Long {
        val maxRttMs = runtimeStates
            .maxOfOrNull { it.rttEmaMs }
            ?: 0.0
        val rawDelayMs = maxRttMs * RTT_MULTIPLIER + BASE_SCHEDULE_BUFFER_MS
        return rawDelayMs.roundToLong().coerceIn(MIN_SCHEDULE_DELAY_MS, MAX_SCHEDULE_DELAY_MS)
    }

    fun calculateExecuteAtMs(
        accountId: Long,
        nowMs: Long,
    ): Long {
        return calculateExecuteAtMs(
            deviceRuntimeService.getActiveRuntimeSnapshot(accountId).runtimeStates,
            nowMs,
        )
    }

    fun calculateExecuteAtMs(
        runtimeStates: Collection<DeviceRuntimeState>,
        nowMs: Long,
    ): Long {
        return nowMs + calculateScheduleDelayMs(runtimeStates)
    }

    fun calculateSyncRecoveryDelayMs(accountId: Long): Long {
        return max(
            calculateScheduleDelayMs(accountId),
            SYNC_PLAY_BUFFER_MS,
        )
    }

    fun calculateSyncRecoveryExecuteAtMs(
        accountId: Long,
        nowMs: Long,
    ): Long {
        return nowMs + calculateSyncRecoveryDelayMs(accountId)
    }

    fun schedulePendingPlayTimeout(
        accountId: Long,
        commandId: String,
    ) {
        jobStore.schedule(
            jobType = PlaybackSyncJobType.PENDING_PLAY_TIMEOUT,
            accountId = accountId,
            dedupeKey = pendingTimeoutJobKey(accountId),
            payload = """{"commandId":"$commandId"}""",
            executeAtMs = timeProvider.nowMs() + PENDING_PLAY_TIMEOUT_MS,
            nowMs = timeProvider.nowMs(),
        )
    }

    fun cancelPendingPlayTimeout(accountId: Long) {
        jobStore.cancel(pendingTimeoutJobKey(accountId))
    }

    fun scheduleAutoAdvance(
        accountId: Long,
        recordingId: Long,
        positionSeconds: Double,
        executeAtMs: Long,
    ) {
        jobStore.schedule(
            jobType = PlaybackSyncJobType.AUTO_ADVANCE,
            accountId = accountId,
            dedupeKey = autoAdvanceJobKey(accountId),
            payload = """{"recordingId":$recordingId,"positionSeconds":$positionSeconds,"executeAtMs":$executeAtMs}""",
            executeAtMs = executeAtMs,
            nowMs = timeProvider.nowMs(),
        )
    }

    fun cancelAutoAdvance(accountId: Long) {
        jobStore.cancel(autoAdvanceJobKey(accountId))
    }

    fun scheduleWithFixedDelay(
        initialDelayMs: Long,
        delayMs: Long,
        task: () -> Unit,
    ): ScheduledFuture<*> {
        return scheduler.scheduleWithFixedDelay(task, initialDelayMs, delayMs, TimeUnit.MILLISECONDS)
    }

    companion object {
        const val PENDING_PLAY_TIMEOUT_MS = 3_000L
        const val MIN_SCHEDULE_DELAY_MS = 400L
        const val MAX_SCHEDULE_DELAY_MS = 3_000L
        const val SYNC_PLAY_BUFFER_MS = 1_500L
        const val STALE_THRESHOLD_MS = 3_750L
        const val STALE_SWEEP_INTERVAL_MS = 1_000L

        private const val RTT_MULTIPLIER = 1.5
        private const val BASE_SCHEDULE_BUFFER_MS = 200.0

        fun pendingTimeoutJobKey(accountId: Long): String = "pending-play-timeout:$accountId"

        fun autoAdvanceJobKey(accountId: Long): String = "auto-advance:$accountId"
    }
}
