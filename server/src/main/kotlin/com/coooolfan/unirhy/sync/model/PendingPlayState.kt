package com.coooolfan.unirhy.sync.model

data class PendingPlayState(
    val commandId: String,
    val initiatorDeviceId: String,
    val recordingId: Long,
    val mediaFileId: Long,
    val sourceUrl: String,
    val positionSeconds: Double,
    val clientsLoaded: MutableSet<String>,
    val createdAtMs: Long,
    val timeoutAtMs: Long,
)
