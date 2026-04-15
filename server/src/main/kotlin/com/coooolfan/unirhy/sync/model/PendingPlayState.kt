package com.coooolfan.unirhy.sync.model

data class PendingPlayState(
    val commandId: String,
    val initiatorDeviceId: String?,
    val currentIndex: Int,
    val recordingId: Long,
    val positionSeconds: Double,
    val clientsLoaded: MutableSet<String>,
    val createdAtMs: Long,
    val timeoutAtMs: Long,
)
