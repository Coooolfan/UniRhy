package com.coooolfan.unirhy.sync.service

import com.coooolfan.unirhy.sync.protocol.PlaybackSyncErrorCode

class PlaybackSyncProtocolException(
    val code: PlaybackSyncErrorCode,
    override val message: String,
    val reason: PlaybackSyncErrorReason,
) : RuntimeException(message)
