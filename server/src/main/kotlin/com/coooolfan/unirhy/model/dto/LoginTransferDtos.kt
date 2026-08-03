package com.coooolfan.unirhy.model.dto

import com.coooolfan.unirhy.controller.LoginTransferController
import com.coooolfan.unirhy.model.LoginTransfer
import com.coooolfan.unirhy.model.LoginTransferPlatform
import com.coooolfan.unirhy.model.LoginTransferStatus
import org.babyfish.jimmer.client.FetchBy
import java.time.Instant
import java.util.UUID

data class LoginTransferUpdateRequest(
    val status: LoginTransferStatus,
    val secret: String? = null,
    val deviceName: String? = null,
    val platform: LoginTransferPlatform? = null,
    val clientVersion: String? = null,
)

data class LoginTransferCreateResponse(
    val id: UUID,
    val secret: String,
    val status: LoginTransferStatus,
    val createdAt: Instant,
    val expiresAt: Instant,
)

/**
 * PATCH 的统一响应。
 *
 * [claimAccessToken] 仅在新设备认领成功时下发一次，审批响应不含该字段；
 * 认领响应按新设备投影裁剪，原设备专属字段缺省。
 */
data class LoginTransferUpdateResponse(
    val transfer: @FetchBy(
        "SOURCE_TRANSFER_FETCHER",
        ownerType = LoginTransferController::class,
    ) LoginTransfer,
    val claimAccessToken: String? = null,
)
