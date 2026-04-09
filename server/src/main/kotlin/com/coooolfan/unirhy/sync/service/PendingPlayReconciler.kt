package com.coooolfan.unirhy.sync.service

import com.coooolfan.unirhy.sync.protocol.ScheduledActionPayload
import org.springframework.stereotype.Service

@Service
class PendingPlayReconciler(
    private val lockManager: PlaybackAccountScope,
    private val playbackSessionService: PlaybackSessionService,
    private val deviceRuntimeService: DeviceRuntimeService,
    private val playbackSchedulerService: PlaybackSchedulerService,
) {
    fun reconcile(
        accountId: Long,
        commandId: String,
        nowMs: Long,
        removedDeviceId: String? = null,
        forceTimeout: Boolean = false,
    ): ScheduledActionPayload? {
        return lockManager.withAccountLock(accountId) {
            val pendingPlay = playbackSessionService.getPendingPlay(accountId) ?: return@withAccountLock null
            if (pendingPlay.commandId != commandId) {
                return@withAccountLock null
            }

            if (removedDeviceId != null) {
                playbackSessionService.unmarkAudioSourceLoaded(accountId, commandId, removedDeviceId)
            }

            val runtimeSnapshot = deviceRuntimeService.getActiveRuntimeSnapshot(accountId)
            if (runtimeSnapshot.deviceIds.isEmpty()) {
                playbackSessionService.clearPendingPlay(accountId)
                return@withAccountLock null
            }

            if (!forceTimeout && !playbackSessionService.areAllDevicesLoaded(accountId, runtimeSnapshot.deviceIds)) {
                return@withAccountLock null
            }

            playbackSessionService.completePendingPlay(
                accountId = accountId,
                commandId = commandId,
                nowMs = nowMs,
                executeAtMs = playbackSchedulerService.calculateExecuteAtMs(runtimeSnapshot.runtimeStates, nowMs),
            )
        }
    }
}
