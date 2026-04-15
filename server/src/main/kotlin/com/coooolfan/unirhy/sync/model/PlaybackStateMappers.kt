package com.coooolfan.unirhy.sync.model

import com.coooolfan.unirhy.sync.protocol.AccountPlaybackStateDto

fun AccountPlaybackState.toProtocolState(): AccountPlaybackStateDto = AccountPlaybackStateDto(
    status = status,
    currentIndex = currentIndex,
    positionSeconds = positionSeconds,
    serverTimeToExecuteMs = serverTimeToExecuteMs,
    version = version,
    updatedAtMs = updatedAtMs,
)
