package com.coooolfan.unirhy.sync.service

import com.coooolfan.unirhy.sync.protocol.ScheduledActionPayload
import org.springframework.stereotype.Service

@Service
class PlaybackQueueMutationCoordinator(
    private val playbackSessionService: PlaybackSessionService,
    private val playbackSchedulerService: PlaybackSchedulerService,
    private val scheduledActionDispatcher: PlaybackSyncScheduledActionDispatcher,
    private val playCoordinator: PlaybackPlayCoordinator,
    private val autoAdvanceService: PlaybackAutoAdvanceService,
) {
    fun syncPausedPlaybackToCurrentQueue(
        accountId: Long,
        currentEntry: com.coooolfan.unirhy.sync.model.CurrentQueueEntry?,
        nowMs: Long,
    ): ScheduledActionPayload? {
        playbackSchedulerService.cancelPendingPlayTimeout(accountId)
        playbackSessionService.clearPendingPlay(accountId)
        val playbackState = playbackSessionService.getOrCreateState(accountId)
        if (playbackState.status != com.coooolfan.unirhy.sync.protocol.PlaybackStatus.PAUSED) {
            return null
        }

        val scheduledAction = playbackSessionService.schedulePause(
            accountId = accountId,
            commandId = "queue-current-$nowMs",
            recordingId = currentEntry?.recordingId,
            positionSeconds = 0.0,
            nowMs = nowMs,
            executeAtMs = playbackSchedulerService.calculateExecuteAtMs(accountId, nowMs),
        )
        scheduledActionDispatcher.broadcastAndLog(accountId, null, scheduledAction, nowMs)
        autoAdvanceService.syncFromScheduledAction(accountId, scheduledAction, nowMs)
        return scheduledAction
    }

    fun handleQueueCleared(
        accountId: Long,
        nowMs: Long,
    ): ScheduledActionPayload {
        playbackSchedulerService.cancelPendingPlayTimeout(accountId)
        playbackSessionService.clearPendingPlay(accountId)
        val scheduledAction = playbackSessionService.schedulePause(
            accountId = accountId,
            commandId = "queue-clear-$nowMs",
            recordingId = null,
            positionSeconds = 0.0,
            nowMs = nowMs,
            executeAtMs = playbackSchedulerService.calculateExecuteAtMs(accountId, nowMs),
        )
        scheduledActionDispatcher.broadcastAndLog(accountId, null, scheduledAction, nowMs)
        autoAdvanceService.syncFromScheduledAction(accountId, scheduledAction, nowMs)
        return scheduledAction
    }

    fun handleCurrentEntryRemoved(
        accountId: Long,
        change: CurrentQueueChangeResult,
        nowMs: Long,
    ) {
        val removedEntry = change.removedEntry ?: return
        val playbackState = playbackSessionService.getOrCreateState(accountId)
        val removedWasPlaying = playbackState.recordingId == removedEntry.recordingId

        when {
            change.currentEntry == null -> handleQueueCleared(accountId, nowMs)
            removedWasPlaying && playbackState.status == com.coooolfan.unirhy.sync.protocol.PlaybackStatus.PLAYING -> {
                playbackSchedulerService.cancelPendingPlayTimeout(accountId)
                playbackSessionService.clearPendingPlay(accountId)
                playCoordinator.initiatePlay(
                    accountId = accountId,
                    commandId = "queue-remove-current-$nowMs",
                    initiatorDeviceId = null,
                    recordingId = change.currentEntry.recordingId,
                    positionSeconds = 0.0,
                    nowMs = nowMs,
                    logDeviceId = null,
                )
            }

            removedWasPlaying || change.previousCurrentEntry?.entryId != change.currentEntry.entryId -> {
                syncPausedPlaybackToCurrentQueue(accountId, change.currentEntry, nowMs)
            }
        }
    }
}
