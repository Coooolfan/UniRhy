package com.coooolfan.unirhy.model.dto

data class SystemInitReq(
    val adminAccountName: String,
    val adminPassword: String,
    val adminAccountEmail: String,
    val storageProviderPath: String,
)