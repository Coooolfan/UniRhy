package com.coooolfan.unirhy.sync.log

import com.coooolfan.unirhy.sync.protocol.ScheduledActionPayload
import com.coooolfan.unirhy.sync.service.PlaybackConnectionContext

internal fun PlaybackConnectionContext.toBaseLogFields(): Array<Pair<String, Any?>> {
    return arrayOf(
        PlaybackSyncLogFields.ACCOUNT_ID to accountId,
        PlaybackSyncLogFields.DEVICE_ID to deviceId,
        PlaybackSyncLogFields.SESSION_ID to sessionId,
    )
}

internal fun PlaybackSyncLogWriter.logConnectionClosed(
    context: PlaybackConnectionContext,
    reason: String,
) {
    info(
        PlaybackSyncLogEvents.CONNECTION_CLOSED,
        *context.toBaseLogFields(),
        PlaybackSyncLogFields.RESULT to "completed",
        PlaybackSyncLogFields.REASON to reason,
    )
}

internal fun PlaybackSyncLogWriter.logScheduledActionSent(
    accountId: Long,
    deviceId: String?,
    payload: ScheduledActionPayload,
    nowMs: Long,
    useDebugLevel: Boolean = false,
) {
    val fields = arrayOf(
        PlaybackSyncLogFields.ACCOUNT_ID to accountId,
        PlaybackSyncLogFields.DEVICE_ID to deviceId,
        PlaybackSyncLogFields.COMMAND_ID to payload.commandId,
        PlaybackSyncLogFields.VERSION to payload.scheduledAction.version,
        PlaybackSyncLogFields.POSITION_SECONDS to payload.scheduledAction.positionSeconds,
        PlaybackSyncLogFields.EXECUTE_AT_MS to payload.serverTimeToExecuteMs,
        PlaybackSyncLogFields.SCHEDULE_DELAY_MS to (payload.serverTimeToExecuteMs - nowMs),
        PlaybackSyncLogFields.RESULT to "completed",
    )
    if (useDebugLevel) {
        debug(
            PlaybackSyncLogEvents.SCHEDULED_ACTION_SENT,
            *fields,
        )
        return
    }

    info(
        PlaybackSyncLogEvents.SCHEDULED_ACTION_SENT,
        *fields,
    )
}
