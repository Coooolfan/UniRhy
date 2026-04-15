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
        currentIndex: Int?,
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
            currentIndex = currentIndex,
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
            currentIndex = null,
            positionSeconds = 0.0,
            nowMs = nowMs,
            executeAtMs = playbackSchedulerService.calculateExecuteAtMs(accountId, nowMs),
        )
        scheduledActionDispatcher.broadcastAndLog(accountId, null, scheduledAction, nowMs)
        autoAdvanceService.syncFromScheduledAction(accountId, scheduledAction, nowMs)
        return scheduledAction
    }

    fun handleQueueNavigation(
        accountId: Long,
        change: CurrentQueueChangeResult,
        nowMs: Long,
    ) {
        if (!change.changed) {
            return
        }
        val currentIndex = change.currentIndex ?: return
        val currentRecordingId = change.currentRecordingId ?: return
        playbackSchedulerService.cancelPendingPlayTimeout(accountId)
        playbackSessionService.clearPendingPlay(accountId)
        playCoordinator.initiatePlay(
            accountId = accountId,
            commandId = "queue-nav-$nowMs",
            initiatorDeviceId = null,
            currentIndex = currentIndex,
            recordingId = currentRecordingId,
            positionSeconds = 0.0,
            nowMs = nowMs,
            logDeviceId = null,
        )
    }

    fun handleCurrentEntryRemoved(
        accountId: Long,
        change: CurrentQueueChangeResult,
        nowMs: Long,
    ) {
        val playbackState = playbackSessionService.getOrCreateState(accountId)
        val removedWasPlaying =
            playbackState.currentIndex != null && playbackState.currentIndex == change.removedIndex

        val currentIndex = change.currentIndex
        val currentRecordingId = change.currentRecordingId

        when {
            currentIndex == null || currentRecordingId == null -> handleQueueCleared(accountId, nowMs)
            removedWasPlaying && playbackState.status == com.coooolfan.unirhy.sync.protocol.PlaybackStatus.PLAYING -> {
                playbackSchedulerService.cancelPendingPlayTimeout(accountId)
                playbackSessionService.clearPendingPlay(accountId)
                playCoordinator.initiatePlay(
                    accountId = accountId,
                    commandId = "queue-remove-current-$nowMs",
                    initiatorDeviceId = null,
                    currentIndex = currentIndex,
                    recordingId = currentRecordingId,
                    positionSeconds = 0.0,
                    nowMs = nowMs,
                    logDeviceId = null,
                )
            }

            removedWasPlaying || change.previousCurrentIndex != change.currentIndex -> {
                syncPausedPlaybackToCurrentQueue(accountId, currentIndex, nowMs)
            }
        }
    }
}
