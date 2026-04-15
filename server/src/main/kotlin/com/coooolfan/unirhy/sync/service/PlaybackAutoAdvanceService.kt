package com.coooolfan.unirhy.sync.service

import com.coooolfan.unirhy.sync.protocol.PlaybackStatus
import com.coooolfan.unirhy.sync.protocol.ScheduledActionPayload
import com.coooolfan.unirhy.sync.protocol.StopStrategy
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
            action.currentIndex == null
        ) {
            return
        }

        val currentEntry = currentQueueService.getCurrentEntry(accountId) ?: return
        val currentState = playbackSessionService.getOrCreateState(accountId)
        if (currentState.currentIndex != action.currentIndex) {
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
        val queue = currentQueueService.getQueue(accountId)
        if (queue.recordingIds.isEmpty()) {
            return
        }
        if (queue.stopStrategy == StopStrategy.TRACK) {
            stopAtCurrentEntry(accountId, nowMs)
            return
        }

        val queueChange = currentQueueService.navigateToNext(accountId = accountId, nowMs = nowMs)
        if (!queueChange.changed) {
            stopAtCurrentEntry(accountId, nowMs)
            return
        }
        messageSender.broadcastQueueChange(accountId, queueChange.queue)

        val nextIndex = queueChange.currentIndex ?: return
        val nextRecordingId = queueChange.currentRecordingId ?: return
        playbackSessionService.createPendingPlay(
            accountId = accountId,
            commandId = "auto-next-$nowMs",
            initiatorDeviceId = null,
            currentIndex = nextIndex,
            recordingId = nextRecordingId,
            positionSeconds = 0.0,
            nowMs = nowMs,
            timeoutAtMs = nowMs + PlaybackSchedulerService.PENDING_PLAY_TIMEOUT_MS,
        )

        messageSender.broadcastLoadAudioSource(
            accountId = accountId,
            payload = com.coooolfan.unirhy.sync.protocol.LoadAudioSourcePayload(
                commandId = "auto-next-$nowMs",
                currentIndex = nextIndex,
                recordingId = nextRecordingId,
            ),
        )

        playbackSchedulerService.schedulePendingPlayTimeout(accountId) {
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

    private fun stopAtCurrentEntry(
        accountId: Long,
        nowMs: Long,
    ) {
        val scheduledAction = playbackSessionService.schedulePause(
            accountId = accountId,
            commandId = "auto-stop-$nowMs",
            currentIndex = playbackSessionService.getOrCreateState(accountId).currentIndex,
            positionSeconds = 0.0,
            nowMs = nowMs,
            executeAtMs = playbackSchedulerService.calculateExecuteAtMs(accountId, nowMs),
        )
        scheduledActionDispatcher.broadcastAndLog(accountId, null, scheduledAction, nowMs)
        syncFromScheduledAction(accountId, scheduledAction, nowMs)
    }
}
