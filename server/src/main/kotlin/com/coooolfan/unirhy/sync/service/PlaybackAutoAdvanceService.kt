package com.coooolfan.unirhy.sync.service

import com.coooolfan.unirhy.sync.protocol.PlaybackStatus
import com.coooolfan.unirhy.sync.protocol.ScheduledActionPayload
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.roundToLong

@Service
class PlaybackAutoAdvanceService(
    private val currentQueueService: CurrentQueueService,
    private val playbackSessionService: PlaybackSessionService,
    private val playbackSchedulerService: PlaybackSchedulerService,
    private val scheduledActionDispatcher: PlaybackSyncScheduledActionDispatcher,
    private val messageSender: PlaybackSyncMessageSender,
    private val timeProvider: PlaybackSyncTimeProvider,
    @Qualifier("playbackSyncScheduledExecutor")
    private val scheduler: ScheduledExecutorService,
) {
    private val autoAdvanceTasks = ConcurrentHashMap<Long, ScheduledFuture<*>>()

    fun nowMs(): Long = timeProvider.nowMs()

    fun cancel(accountId: Long) {
        autoAdvanceTasks.remove(accountId)?.cancel(false)
    }

    fun syncFromScheduledAction(
        accountId: Long,
        scheduledAction: ScheduledActionPayload,
        nowMs: Long,
    ) {
        cancel(accountId)

        val action = scheduledAction.scheduledAction
        if (
            action.status != PlaybackStatus.PLAYING ||
            action.recordingId == null
        ) {
            return
        }

        val currentEntry = currentQueueService.getCurrentEntry(accountId) ?: return
        if (currentEntry.recordingId != action.recordingId) {
            return
        }

        val remainingTrackMs = max(0L, currentEntry.durationMs - (action.positionSeconds * 1000.0).roundToLong())
        val waitUntilPlayMs = max(0L, scheduledAction.serverTimeToExecuteMs - nowMs)
        val totalDelayMs = waitUntilPlayMs + remainingTrackMs

        autoAdvanceTasks[accountId] = scheduler.schedule(
            {
                autoAdvanceTasks.remove(accountId)
                advanceToNext(accountId)
            },
            totalDelayMs,
            TimeUnit.MILLISECONDS,
        )
    }

    private fun advanceToNext(accountId: Long) {
        val nowMs = timeProvider.nowMs()
        val queueChange = currentQueueService.advanceToNext(accountId, nowMs) ?: return
        messageSender.broadcastQueueChange(accountId, queueChange.queue)

        val nextEntry = queueChange.currentEntry ?: return
        playbackSessionService.createPendingPlay(
            accountId = accountId,
            commandId = "auto-next-$nowMs",
            initiatorDeviceId = null,
            recordingId = nextEntry.recordingId,
            positionSeconds = 0.0,
            nowMs = nowMs,
            timeoutAtMs = nowMs + PlaybackSchedulerService.PENDING_PLAY_TIMEOUT_MS,
        )

        messageSender.broadcastLoadAudioSource(
            accountId = accountId,
            payload = com.coooolfan.unirhy.sync.protocol.LoadAudioSourcePayload(
                commandId = "auto-next-$nowMs",
                recordingId = nextEntry.recordingId,
            ),
        )

        playbackSchedulerService.schedulePendingPlayTimeout(accountId, "auto-next-$nowMs") {
            val timeoutNowMs = timeProvider.nowMs()
            val scheduledAction = playbackSessionService.completePendingPlay(
                accountId = accountId,
                commandId = "auto-next-$nowMs",
                nowMs = timeoutNowMs,
                executeAtMs = playbackSchedulerService.calculateExecuteAtMs(accountId, timeoutNowMs),
            ) ?: return@schedulePendingPlayTimeout

            scheduledActionDispatcher.broadcastAndLog(accountId, null, scheduledAction, timeoutNowMs)
            syncFromScheduledAction(accountId, scheduledAction, timeoutNowMs)
        }
    }
}
