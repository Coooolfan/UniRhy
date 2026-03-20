package com.coooolfan.unirhy.sync.model

import com.coooolfan.unirhy.sync.protocol.AccountPlaybackStateDto

fun AccountPlaybackState.toProtocolState(): AccountPlaybackStateDto = AccountPlaybackStateDto(
    status = status,
    recordingId = recordingId,
    mediaFileId = mediaFileId,
    positionSeconds = positionSeconds,
    serverTimeToExecuteMs = serverTimeToExecuteMs,
    version = version,
    updatedAtMs = updatedAtMs,
)
