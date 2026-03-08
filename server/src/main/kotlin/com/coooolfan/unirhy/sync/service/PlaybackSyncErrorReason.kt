package com.coooolfan.unirhy.sync.service

enum class PlaybackSyncErrorReason(
    val value: String,
) {
    INVALID_MESSAGE("invalid_message"),
    HELLO_REQUIRED("hello_required"),
    INTERNAL_ERROR("internal_error"),
    HELLO_ALREADY_RECEIVED("hello_already_received"),
    DEVICE_ID_BLANK("device_id_blank"),
    COMMAND_ID_BLANK("command_id_blank"),
    PENDING_PLAY_NOT_FOUND("pending_play_not_found"),
    SYNC_NOT_READY("sync_not_ready"),
    MEDIA_CONTEXT_REQUIRED("media_context_required"),
    MEDIA_CONTEXT_MISMATCH("media_context_mismatch"),
    DEVICE_ID_MISMATCH("device_id_mismatch"),
    DEVICE_NOT_REGISTERED("device_not_registered"),
    POSITION_SECONDS_INVALID("position_seconds_invalid"),
    CLIENT_RTT_INVALID("client_rtt_invalid"),
    RECORDING_NOT_FOUND("recording_not_found"),
    MEDIA_FILE_NOT_FOUND("media_file_not_found"),
    RECORDING_NOT_PLAYABLE("recording_not_playable"),
}
