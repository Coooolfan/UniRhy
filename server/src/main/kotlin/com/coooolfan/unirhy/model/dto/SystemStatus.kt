package com.coooolfan.unirhy.model.dto

data class SystemStatus(
    val initialized: Boolean,
    val version: String?,
    val buildTime: String?,
    val gitBranch: String?,
    val gitCommit: String?,
    val gitUrl: String?,
)
