package com.coooolfan.unirhy.sync.model

import com.coooolfan.unirhy.sync.protocol.AccountPlaybackState as ProtocolAccountPlaybackState

fun AccountPlaybackState.toProtocolState(): ProtocolAccountPlaybackState = ProtocolAccountPlaybackState(
    status = status,
    recordingId = recordingId,
    mediaFileId = mediaFileId,
    sourceUrl = sourceUrl,
    positionSeconds = positionSeconds,
    serverTimeToExecuteMs = serverTimeToExecuteMs,
    version = version,
    updatedAtMs = updatedAtMs,
)
