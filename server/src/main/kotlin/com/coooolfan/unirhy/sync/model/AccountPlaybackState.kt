package com.coooolfan.unirhy.sync.model

import com.coooolfan.unirhy.sync.protocol.PlaybackStatus

data class AccountPlaybackState(
    val accountId: Long,
    val status: PlaybackStatus,
    val currentIndex: Int? = null,
    val positionSeconds: Double,
    val serverTimeToExecuteMs: Long,
    val version: Long,
    val updatedAtMs: Long,
)
