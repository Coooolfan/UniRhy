package com.coooolfan.unirhy.sync.log

import com.coooolfan.unirhy.sync.protocol.ScheduledActionPayload
import com.coooolfan.unirhy.sync.service.PlaybackConnectionContext

internal fun PlaybackSyncLogWriter.logConnectionClosed(
    context: PlaybackConnectionContext,
    reason: String,
) {
    info(
        event = PlaybackSyncLogEvents.CONNECTION_CLOSED,
        PlaybackSyncLogFields.ACCOUNT_ID to context.accountId,
        PlaybackSyncLogFields.DEVICE_ID to context.deviceId,
        PlaybackSyncLogFields.SESSION_ID to context.sessionId,
        PlaybackSyncLogFields.RESULT to "completed",
        PlaybackSyncLogFields.REASON to reason,
    )
}

internal fun PlaybackSyncLogWriter.logScheduledActionSent(
    accountId: Long,
    deviceId: String?,
    payload: ScheduledActionPayload,
    nowMs: Long,
) {
    info(
        event = PlaybackSyncLogEvents.SCHEDULED_ACTION_SENT,
        PlaybackSyncLogFields.ACCOUNT_ID to accountId,
        PlaybackSyncLogFields.DEVICE_ID to deviceId,
        PlaybackSyncLogFields.COMMAND_ID to payload.commandId,
        PlaybackSyncLogFields.VERSION to payload.scheduledAction.version,
        PlaybackSyncLogFields.POSITION_SECONDS to payload.scheduledAction.positionSeconds,
        PlaybackSyncLogFields.EXECUTE_AT_MS to payload.serverTimeToExecuteMs,
        PlaybackSyncLogFields.SCHEDULE_DELAY_MS to (payload.serverTimeToExecuteMs - nowMs),
        PlaybackSyncLogFields.RESULT to "completed",
    )
}
