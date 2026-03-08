package com.coooolfan.unirhy.sync.service

import com.coooolfan.unirhy.sync.log.PlaybackSyncLogWriter
import com.coooolfan.unirhy.sync.log.logScheduledActionSent
import com.coooolfan.unirhy.sync.protocol.ScheduledActionPayload
import org.springframework.stereotype.Service

@Service
class PlaybackSyncScheduledActionDispatcher(
    private val messageSender: PlaybackSyncMessageSender,
    private val logWriter: PlaybackSyncLogWriter,
) {
    fun broadcastAndLog(
        accountId: Long,
        deviceId: String?,
        payload: ScheduledActionPayload,
        nowMs: Long,
    ) {
        messageSender.broadcastScheduledAction(accountId, payload)
        logWriter.logScheduledActionSent(accountId, deviceId, payload, nowMs)
    }
}
