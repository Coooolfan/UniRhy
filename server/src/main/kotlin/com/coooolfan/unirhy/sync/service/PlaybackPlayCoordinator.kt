package com.coooolfan.unirhy.sync.service

import com.coooolfan.unirhy.sync.protocol.LoadAudioSourcePayload
import com.coooolfan.unirhy.sync.protocol.ScheduledActionPayload
import org.springframework.stereotype.Service

@Service
class PlaybackPlayCoordinator(
    private val lockManager: PlaybackAccountScope = PlaybackAccountLockManager(),
    private val playbackSessionService: PlaybackSessionService,
    @Suppress("UNUSED_PARAMETER")
    private val deviceRuntimeService: DeviceRuntimeService? = null,
    private val pendingPlayReconciler: PendingPlayReconciler? = null,
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
        positionSeconds: Double,
        nowMs: Long,
        logDeviceId: String? = initiatorDeviceId,
    ) {
        lockManager.withAccountLock(accountId) {
            playbackSessionService.createPendingPlay(
                accountId = accountId,
                commandId = commandId,
                initiatorDeviceId = initiatorDeviceId,
                recordingId = recordingId,
                positionSeconds = positionSeconds,
                nowMs = nowMs,
                timeoutAtMs = nowMs + PlaybackSchedulerService.PENDING_PLAY_TIMEOUT_MS,
            )

            messageSender.broadcastLoadAudioSource(
                accountId = accountId,
                payload = LoadAudioSourcePayload(
                    commandId = commandId,
                    recordingId = recordingId,
                ),
            )

            val scheduledAction = reconcile(accountId, commandId, nowMs) ?: run {
                playbackSchedulerService.schedulePendingPlayTimeout(accountId, commandId)
                return@withAccountLock
            }

            playbackSchedulerService.cancelPendingPlayTimeout(accountId)
            scheduledActionDispatcher.broadcastAndLog(accountId, logDeviceId, scheduledAction, nowMs)
            autoAdvanceService.syncFromScheduledAction(accountId, scheduledAction, nowMs)
        }
    }

    fun completePendingPlayIfReady(
        accountId: Long,
        commandId: String,
        nowMs: Long,
        logDeviceId: String?,
    ): ScheduledActionPayload? {
        val scheduledAction = lockManager.withAccountLock(accountId) {
            reconcile(accountId, commandId, nowMs)
        } ?: return null

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
        lockManager.withAccountLock(accountId) {
            val scheduledAction = reconcile(
                accountId = accountId,
                commandId = commandId,
                nowMs = nowMs,
                forceTimeout = true,
            ) ?: return@withAccountLock

            scheduledActionDispatcher.broadcastAndLog(accountId, null, scheduledAction, nowMs)
            autoAdvanceService.syncFromScheduledAction(accountId, scheduledAction, nowMs)
        }
    }

    private fun reconcile(
        accountId: Long,
        commandId: String,
        nowMs: Long,
        forceTimeout: Boolean = false,
    ): ScheduledActionPayload? {
        pendingPlayReconciler?.let {
            return it.reconcile(
                accountId = accountId,
                commandId = commandId,
                nowMs = nowMs,
                forceTimeout = forceTimeout,
            )
        }

        val pendingPlay = playbackSessionService.getPendingPlay(accountId) ?: return null
        if (pendingPlay.commandId != commandId) {
            return null
        }
        val runtimeSnapshot = deviceRuntimeService?.getActiveRuntimeSnapshot(accountId)
            ?: return null
        if (!forceTimeout && !playbackSessionService.areAllDevicesLoaded(accountId, runtimeSnapshot.deviceIds)) {
            return null
        }
        return playbackSessionService.completePendingPlay(
            accountId = accountId,
            commandId = commandId,
            nowMs = nowMs,
            executeAtMs = playbackSchedulerService.calculateExecuteAtMs(runtimeSnapshot.runtimeStates, nowMs),
        )
    }
}
