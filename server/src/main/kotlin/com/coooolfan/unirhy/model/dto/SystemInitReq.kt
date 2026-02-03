package com.coooolfan.unirhy.model.dto

import com.coooolfan.unirhy.model.storage.dto.FileProviderFileSystemCreate

data class SystemInitReq(
    val adminAccount: AccountCreate,
    val storageProvider: FileProviderFileSystemCreate,
)