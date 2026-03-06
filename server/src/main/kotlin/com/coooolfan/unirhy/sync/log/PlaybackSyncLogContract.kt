package com.coooolfan.unirhy.sync.log

object PlaybackSyncLogFields {
    const val EVENT = "event"
    const val ACCOUNT_ID = "accountId"
    const val DEVICE_ID = "deviceId"
    const val SESSION_ID = "sessionId"
    const val COMMAND_ID = "commandId"
    const val RECORDING_ID = "recordingId"
    const val MEDIA_FILE_ID = "mediaFileId"
    const val VERSION = "version"
    const val POSITION_SECONDS = "positionSeconds"
    const val SERVER_NOW_MS = "serverNowMs"
    const val EXECUTE_AT_MS = "executeAtMs"
    const val SCHEDULE_DELAY_MS = "scheduleDelayMs"
    const val RTT_MS = "rttMs"
    const val RTT_EMA_MS = "rttEmaMs"
    const val RESULT = "result"
    const val REASON = "reason"
}

object PlaybackSyncLogEvents {
    const val CONNECTION_OPENED = "playback_sync_connection_opened"
    const val CONNECTION_CLOSED = "playback_sync_connection_closed"
    const val HELLO_RECEIVED = "playback_sync_hello_received"
    const val NTP_REQUEST_RECEIVED = "playback_sync_ntp_request_received"
    const val NTP_RESPONSE_SENT = "playback_sync_ntp_response_sent"
    const val PLAY_REQUEST = "playback_sync_play_request"
    const val PAUSE_REQUEST = "playback_sync_pause_request"
    const val SEEK_REQUEST = "playback_sync_seek_request"
    const val PENDING_PLAY_CREATED = "playback_sync_pending_play_created"
    const val PENDING_PLAY_REPLACED = "playback_sync_pending_play_replaced"
    const val AUDIO_SOURCE_LOADED = "playback_sync_audio_source_loaded"
    const val SCHEDULED_ACTION_SENT = "playback_sync_scheduled_action_sent"
    const val SNAPSHOT_SENT = "playback_sync_snapshot_sent"
    const val PROTOCOL_ERROR = "playback_sync_protocol_error"
}
