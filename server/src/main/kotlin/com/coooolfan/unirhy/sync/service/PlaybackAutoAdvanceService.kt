package com.coooolfan.unirhy.sync.service

import com.coooolfan.unirhy.sync.protocol.PlaybackStatus
import com.coooolfan.unirhy.sync.protocol.ScheduledActionPayload
import com.coooolfan.unirhy.sync.protocol.StopStrategy
import org.springframework.stereotype.Service
import java.util.concurrent.ScheduledExecutorService
import kotlin.math.max
import kotlin.math.roundToLong

@Service
class PlaybackAutoAdvanceService(
    private val lockManager: PlaybackAccountScope,
    private val currentQueueService: CurrentQueueService,
    private val playbackSessionService: PlaybackSessionService,
    private val playbackSchedulerService: PlaybackSchedulerService,
    private val scheduledActionDispatcher: PlaybackSyncScheduledActionDispatcher,
    private val messageSender: PlaybackSyncMessageSender,
    private val timeProvider: PlaybackSyncTimeProvider,
    @Suppress("UNUSED_PARAMETER")
    private val scheduler: ScheduledExecutorService? = null,
) {
    fun nowMs(): Long = timeProvider.nowMs()

    fun cancel(accountId: Long) {
        playbackSchedulerService.cancelAutoAdvance(accountId)
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
        playbackSchedulerService.scheduleAutoAdvance(
            accountId = accountId,
            recordingId = action.recordingId,
            positionSeconds = action.positionSeconds,
            executeAtMs = nowMs + totalDelayMs,
        )
    }

    fun advanceToNext(accountId: Long) {
        lockManager.withAccountLock(accountId) {
            val nowMs = timeProvider.nowMs()
            val queue = currentQueueService.getQueue(accountId)
            if (queue.currentEntryId == null) {
                return@withAccountLock
            }
            if (queue.stopStrategy == StopStrategy.TRACK) {
                stopAtCurrentEntry(accountId, nowMs)
                return@withAccountLock
            }

            val queueChange = currentQueueService.navigateToNext(accountId, nowMs)
            if (!queueChange.changed) {
                stopAtCurrentEntry(accountId, nowMs)
                return@withAccountLock
            }
            messageSender.broadcastQueueChange(accountId, queueChange.queue)

            val nextEntry = queueChange.currentEntry ?: return@withAccountLock
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

            playbackSchedulerService.schedulePendingPlayTimeout(accountId, "auto-next-$nowMs")
        }
    }

    private fun stopAtCurrentEntry(
        accountId: Long,
        nowMs: Long,
    ) {
        val currentEntry = currentQueueService.getCurrentEntry(accountId)
        val scheduledAction = playbackSessionService.schedulePause(
            accountId = accountId,
            commandId = "auto-stop-$nowMs",
            recordingId = currentEntry?.recordingId,
            positionSeconds = 0.0,
            nowMs = nowMs,
            executeAtMs = playbackSchedulerService.calculateExecuteAtMs(accountId, nowMs),
        )
        scheduledActionDispatcher.broadcastAndLog(accountId, null, scheduledAction, nowMs)
        syncFromScheduledAction(accountId, scheduledAction, nowMs)
    }
}
