package com.coooolfan.unirhy.sync.service

import com.coooolfan.unirhy.sync.log.PlaybackSyncLogWriter
import com.coooolfan.unirhy.sync.log.logScheduledActionSent
import com.coooolfan.unirhy.sync.protocol.ScheduledActionPayload
import org.springframework.stereotype.Service

@Service
class PlaybackSyncScheduledActionDispatcher(
    private val messageSender: PlaybackSyncMessageSender,
    private val deviceRuntimeService: DeviceRuntimeService,
    private val logWriter: PlaybackSyncLogWriter,
) {
    fun broadcastAndLog(
        accountId: Long,
        deviceId: String?,
        payload: ScheduledActionPayload,
        nowMs: Long,
    ) {
        val deviceCount = deviceRuntimeService.listHelloCompletedConnections(accountId).size
        val enrichedPayload =
            if (deviceCount <= 1) payload.copy(skipLateCompensation = true) else payload
        messageSender.broadcastScheduledAction(accountId, enrichedPayload)
        logWriter.logScheduledActionSent(accountId, deviceId, enrichedPayload, nowMs)
    }
}
