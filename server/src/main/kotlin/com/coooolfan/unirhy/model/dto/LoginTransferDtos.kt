package com.coooolfan.unirhy.model.dto

import com.coooolfan.unirhy.controller.LoginTransferController
import com.coooolfan.unirhy.model.LoginTransfer
import com.coooolfan.unirhy.model.LoginTransferPlatform
import com.coooolfan.unirhy.model.LoginTransferStatus
import org.babyfish.jimmer.client.FetchBy
import java.time.Instant
import java.util.UUID

/** 原设备的审批请求，只允许携带目标状态。 */
data class LoginTransferUpdateRequest(
    val status: LoginTransferStatus,
)

/**
 * 新设备的认领请求。
 *
 * 二维码密钥同时充当查询索引与凭据，因此请求里不需要交接 id。
 */
data class LoginTransferClaimRequest(
    val secret: String,
    val deviceName: String,
    val platform: LoginTransferPlatform,
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
 * 认领与审批的统一响应。
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
