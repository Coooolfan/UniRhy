package com.coooolfan.unirhy.model

import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.Id
import org.babyfish.jimmer.sql.IdView
import org.babyfish.jimmer.sql.ManyToOne
import java.time.Instant
import java.util.UUID

@Entity
interface LoginTransfer {
    @Id
    val id: UUID

    @ManyToOne
    val account: Account

    @IdView
    val accountId: Long

    val qrSecretHash: ByteArray

    val claimTokenHash: ByteArray?

    val deviceName: String?

    val platform: LoginTransferPlatform?

    val clientVersion: String?

    val status: LoginTransferStatus

    val createdAt: Instant

    val expiresAt: Instant

    val claimedAt: Instant?

    val authorizedAt: Instant?

    val closedAt: Instant?
}

enum class LoginTransferPlatform {
    ANDROID,
    IOS,
    MACOS,
    WINDOWS,
    LINUX,
    WEB,
}

enum class LoginTransferStatus {
    WAITING,
    CLAIMED,
    AUTHORIZED,
    COMPLETED,
    REJECTED,
    CANCELLED,
    EXPIRED,
}
