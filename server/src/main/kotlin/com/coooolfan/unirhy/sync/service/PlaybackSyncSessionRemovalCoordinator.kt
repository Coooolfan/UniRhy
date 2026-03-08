package com.coooolfan.unirhy.sync.service

import com.coooolfan.unirhy.sync.log.PlaybackSyncLogWriter
import com.coooolfan.unirhy.sync.log.logConnectionClosed
import org.springframework.stereotype.Service

@Service
class PlaybackSyncSessionRemovalCoordinator(
    private val playbackSessionService: PlaybackSessionService,
    private val playbackSchedulerService: PlaybackSchedulerService,
    private val scheduledActionDispatcher: PlaybackSyncScheduledActionDispatcher,
    private val messageSender: PlaybackSyncMessageSender,
    private val logWriter: PlaybackSyncLogWriter,
) {
    fun handleRemoval(
        removal: SessionRemovalResult,
        reason: String,
        nowMs: Long,
    ) {
        logWriter.logConnectionClosed(removal.context, reason)
        processAftermath(removal, nowMs)
    }

    fun abandonPendingPlay(accountId: Long) {
        playbackSchedulerService.cancelPendingPlayTimeout(accountId)
        playbackSessionService.clearPendingPlay(accountId)
    }

    internal fun processAftermath(
        removal: SessionRemovalResult,
        nowMs: Long,
    ) {
        val accountId = removal.context.accountId
        val scheduledAction = removal.context.deviceId?.let { deviceId ->
            playbackSessionService.handleDeviceDisconnected(
                accountId = accountId,
                deviceId = deviceId,
                remainingDeviceIds = removal.remainingDeviceIds,
                nowMs = nowMs,
                executeAtMs = playbackSchedulerService.calculateExecuteAtMs(accountId, nowMs),
            )
        }

        if (scheduledAction != null) {
            playbackSchedulerService.cancelPendingPlayTimeout(accountId)
            scheduledActionDispatcher.broadcastAndLog(accountId, removal.context.deviceId, scheduledAction, nowMs)
        } else if (removal.remainingDeviceIds.isEmpty()) {
            abandonPendingPlay(accountId)
        }

        if (removal.deviceListChanged) {
            messageSender.broadcastDeviceChange(accountId, removal.remainingDeviceIds)
        }
    }
}
