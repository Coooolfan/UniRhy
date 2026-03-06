package com.coooolfan.unirhy.sync.model

class DeviceRuntimeState(
    val deviceId: String,
    val accountId: Long,
    var rttEmaMs: Double = 0.0,
    var lastNtpResponseAtMs: Long = 0L,
    var lastSeenAtMs: Long,
)
