package com.coooolfan.unirhy.sync.model

class DeviceRuntimeState(
    val deviceId: String,
    val accountId: Long,
    val clientVersion: String? = null,
    var rttEmaMs: Double = 0.0,
    var lastNtpResponseAtMs: Long = 0L,
    var lastPongAtMs: Long,
    var lastSeenAtMs: Long,
)
