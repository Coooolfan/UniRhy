package com.coooolfan.unirhy.sync.service

import com.coooolfan.unirhy.sync.protocol.LoadAudioSourcePayload
import com.coooolfan.unirhy.sync.protocol.ScheduledActionPayload
import org.springframework.stereotype.Service

@Service
class PlaybackPlayCoordinator(
    private val playbackSessionService: PlaybackSessionService,
    private val deviceRuntimeService: DeviceRuntimeService,
    private val playbackSchedulerService: PlaybackSchedulerService,
    private val scheduledActionDispatcher: PlaybackSyncScheduledActionDispatcher,
    private val messageSender: PlaybackSyncMessageSender,
    private val autoAdvanceService: PlaybackAutoAdvanceService,
) {
    fun initiatePlay(
        accountId: Long,
        commandId: String,
        initiatorDeviceId: String?,
        recordingId: Long,
        mediaFileId: Long,
        positionSeconds: Double,
        nowMs: Long,
        logDeviceId: String? = initiatorDeviceId,
    ) {
        playbackSessionService.createPendingPlay(
            accountId = accountId,
            commandId = commandId,
            initiatorDeviceId = initiatorDeviceId,
            recordingId = recordingId,
            mediaFileId = mediaFileId,
            positionSeconds = positionSeconds,
            nowMs = nowMs,
            timeoutAtMs = nowMs + PlaybackSchedulerService.PENDING_PLAY_TIMEOUT_MS,
        )

        messageSender.broadcastLoadAudioSource(
            accountId = accountId,
            payload = LoadAudioSourcePayload(
                commandId = commandId,
                recordingId = recordingId,
                mediaFileId = mediaFileId,
                presignedUrl = "",
            ),
        )

        val scheduledAction = maybeCompletePendingPlay(accountId, nowMs) ?: run {
            playbackSchedulerService.schedulePendingPlayTimeout(accountId, commandId) {
                handlePendingPlayTimeout(accountId, commandId)
            }
            return
        }

        playbackSchedulerService.cancelPendingPlayTimeout(accountId)
        scheduledActionDispatcher.broadcastAndLog(accountId, logDeviceId, scheduledAction, nowMs)
        autoAdvanceService.syncFromScheduledAction(accountId, scheduledAction, nowMs)
    }

    fun completePendingPlayIfReady(
        accountId: Long,
        commandId: String,
        nowMs: Long,
        logDeviceId: String?,
    ): ScheduledActionPayload? {
        val runtimeSnapshot = deviceRuntimeService.getActiveRuntimeSnapshot(accountId)
        if (!playbackSessionService.areAllDevicesLoaded(accountId, runtimeSnapshot.deviceIds)) {
            return null
        }

        val scheduledAction = playbackSessionService.completePendingPlay(
            accountId = accountId,
            commandId = commandId,
            nowMs = nowMs,
            executeAtMs = playbackSchedulerService.calculateExecuteAtMs(runtimeSnapshot.runtimeStates, nowMs),
        ) ?: return null

        playbackSchedulerService.cancelPendingPlayTimeout(accountId)
        scheduledActionDispatcher.broadcastAndLog(accountId, logDeviceId, scheduledAction, nowMs)
        autoAdvanceService.syncFromScheduledAction(accountId, scheduledAction, nowMs)
        return scheduledAction
    }

    fun handlePendingPlayTimeout(
        accountId: Long,
        commandId: String,
    ) {
        val nowMs = autoAdvanceService.nowMs()
        val scheduledAction = playbackSessionService.completePendingPlay(
            accountId = accountId,
            commandId = commandId,
            nowMs = nowMs,
            executeAtMs = playbackSchedulerService.calculateExecuteAtMs(accountId, nowMs),
        ) ?: return

        scheduledActionDispatcher.broadcastAndLog(accountId, null, scheduledAction, nowMs)
        autoAdvanceService.syncFromScheduledAction(accountId, scheduledAction, nowMs)
    }

    private fun maybeCompletePendingPlay(
        accountId: Long,
        nowMs: Long,
    ): ScheduledActionPayload? {
        val pendingPlay = playbackSessionService.getPendingPlay(accountId) ?: return null
        val runtimeSnapshot = deviceRuntimeService.getActiveRuntimeSnapshot(accountId)
        if (!playbackSessionService.areAllDevicesLoaded(accountId, runtimeSnapshot.deviceIds)) {
            return null
        }

        return playbackSessionService.completePendingPlay(
            accountId = accountId,
            commandId = pendingPlay.commandId,
            nowMs = nowMs,
            executeAtMs = playbackSchedulerService.calculateExecuteAtMs(runtimeSnapshot.runtimeStates, nowMs),
        )
    }
}
