package com.coooolfan.unirhy.service

import com.coooolfan.unirhy.error.CommonException
import com.coooolfan.unirhy.error.LoginTransferException
import com.coooolfan.unirhy.model.Account
import com.coooolfan.unirhy.model.LoginTransfer
import com.coooolfan.unirhy.model.LoginTransferStatus
import com.coooolfan.unirhy.model.accountId
import com.coooolfan.unirhy.model.admin
import com.coooolfan.unirhy.model.authorizedAt
import com.coooolfan.unirhy.model.claimTokenHash
import com.coooolfan.unirhy.model.claimedAt
import com.coooolfan.unirhy.model.clientVersion
import com.coooolfan.unirhy.model.closedAt
import com.coooolfan.unirhy.model.deviceName
import com.coooolfan.unirhy.model.expiresAt
import com.coooolfan.unirhy.model.id
import com.coooolfan.unirhy.model.platform
import com.coooolfan.unirhy.model.status
import com.coooolfan.unirhy.model.dto.LoginTransferCreateResponse
import com.coooolfan.unirhy.model.dto.LoginTransferUpdateRequest
import com.coooolfan.unirhy.model.dto.LoginTransferUpdateResponse
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.fetcher.Fetcher
import org.babyfish.jimmer.sql.kt.ast.expression.case
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.babyfish.jimmer.sql.kt.ast.expression.le
import org.babyfish.jimmer.sql.kt.ast.expression.valueIn
import org.babyfish.jimmer.sql.kt.ast.mutation.KMutableUpdate
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID

/** 调用方在本次登录交接请求中出示的凭据。 */
sealed interface LoginTransferAuthorization {
    /** 未出示任何凭据，仅二维码密钥可证明身份。 */
    data object None : LoginTransferAuthorization

    /** 已登录账号，即发起交接的原设备。 */
    data class Account(val accountId: Long) : LoginTransferAuthorization

    /** 认领访问令牌，即扫码的新设备。 */
    data class Claim(val token: String) : LoginTransferAuthorization
}

@Service
class LoginTransferService(
    private val sql: KSqlClient,
    private val accountService: AccountService,
    @param:Value("\${unirhy.login-transfer.ttl-seconds:120}")
    private val ttlSeconds: Long,
    @param:Value("\${unirhy.login-transfer.terminal-retention-hours:24}")
    private val terminalRetentionHours: Long,
) {
    private val secureRandom = SecureRandom()
    private val transferTtl: Duration = Duration.ofSeconds(ttlSeconds)
    private val terminalRetention: Duration = Duration.ofHours(terminalRetentionHours)

    @Transactional
    fun create(accountId: Long): LoginTransferCreateResponse {
        sql.createQuery(Account::class) {
            where(table.id eq accountId)
            select(table.id)
        }.forUpdate().execute().firstOrNull() ?: throw CommonException.NotFound()

        val now = Instant.now()
        closePreviousTransfers(accountId, now)

        val secret = newCredential()
        val transfer = LoginTransfer {
            id = UUID.randomUUID()
            this.accountId = accountId
            qrSecretHash = hashCredential(secret)
            claimTokenHash = null
            deviceName = null
            platform = null
            clientVersion = null
            status = LoginTransferStatus.WAITING
            createdAt = now
            expiresAt = now.plus(transferTtl)
            claimedAt = null
            authorizedAt = null
            closedAt = null
        }
        sql.saveCommand(transfer, SaveMode.INSERT_ONLY).execute()

        return LoginTransferCreateResponse(
            id = transfer.id,
            secret = secret,
            status = transfer.status,
            createdAt = transfer.createdAt,
            expiresAt = transfer.expiresAt,
        )
    }

    @Transactional
    fun getForAccount(id: UUID, accountId: Long, fetcher: Fetcher<LoginTransfer>): LoginTransfer {
        val row = findForRead(id, fetcher) ?: throw LoginTransferException.NotFound()
        if (row._2 != accountId) throw CommonException.Forbidden()
        return expireForRead(row._1)
    }

    @Transactional
    fun getForClaim(id: UUID, claimToken: String, fetcher: Fetcher<LoginTransfer>): LoginTransfer {
        val row = findForRead(id, fetcher) ?: throw LoginTransferException.NotFound()
        requireClaimToken(row._3, claimToken)
        return expireForRead(row._1)
    }

    /**
     * PATCH 的唯一入口：由目标状态与所出示的凭据共同决定允许的状态转换。
     *
     * 认领响应使用 [claimFetcher]，审批响应使用 [sourceFetcher]。
     */
    @Transactional(noRollbackFor = [LoginTransferException.Expired::class])
    fun applyUpdate(
        id: UUID,
        auth: LoginTransferAuthorization,
        request: LoginTransferUpdateRequest,
        sourceFetcher: Fetcher<LoginTransfer>,
        claimFetcher: Fetcher<LoginTransfer>,
    ): LoginTransferUpdateResponse = when (request.status) {
        LoginTransferStatus.CLAIMED -> {
            // 认领只能由尚未持有任何凭据的新设备发起，其身份由二维码密钥证明
            if (auth !is LoginTransferAuthorization.None) throw CommonException.InvalidRequest()
            claim(id, request, claimFetcher)
        }

        LoginTransferStatus.AUTHORIZED,
        LoginTransferStatus.REJECTED,
        -> {
            val account = auth as? LoginTransferAuthorization.Account
                ?: throw CommonException.AuthenticationFailed()
            // 审批请求只允许携带目标状态，任何认领侧字段都视为非法请求
            if (request != LoginTransferUpdateRequest(status = request.status)) {
                throw CommonException.InvalidRequest()
            }
            LoginTransferUpdateResponse(
                decide(id, account.accountId, request.status, sourceFetcher),
            )
        }

        else -> throw CommonException.InvalidRequest()
    }

    @Transactional(noRollbackFor = [LoginTransferException.Expired::class])
    fun cancel(id: UUID, accountId: Long) {
        val transfer = findLocked(id) ?: throw LoginTransferException.NotFound()
        if (transfer.accountId != accountId) throw CommonException.Forbidden()
        expireIfNeeded(transfer)
        when (transfer.status) {
            in ACTIVE_STATUSES -> update(id) {
                set(table.status, LoginTransferStatus.CANCELLED)
                set(table.closedAt, Instant.now())
            }

            LoginTransferStatus.CANCELLED -> Unit
            else -> throw LoginTransferException.StatusConflict()
        }
    }

    @Transactional(noRollbackFor = [LoginTransferException.Expired::class])
    fun createToken(id: UUID, claimToken: String): String {
        val transfer = findLocked(id) ?: throw LoginTransferException.NotFound()
        requireClaimToken(transfer.claimTokenHash, claimToken)
        expireIfNeeded(transfer)
        if (transfer.status != LoginTransferStatus.AUTHORIZED) {
            throw LoginTransferException.StatusConflict()
        }

        val admin = sql.createQuery(Account::class) {
            where(table.id eq transfer.accountId)
            select(table.admin)
        }.execute().firstOrNull() ?: throw LoginTransferException.NotFound()
        val token = accountService.startSession(transfer.accountId, admin)
        update(id) {
            set(table.status, LoginTransferStatus.COMPLETED)
            set(table.closedAt, Instant.now())
        }
        return token
    }

    /** 由 [LoginTransferCleanupJob] 周期驱动：迁移已到期的交接，并回收超过保留期的终态记录。 */
    @Transactional
    fun cleanup() {
        val now = Instant.now()
        expireActive(now) { where(table.expiresAt le now) }
        sql.createDelete(LoginTransfer::class) {
            where(table.status valueIn TERMINAL_STATUSES)
            where(table.closedAt le now.minus(terminalRetention))
        }.execute()
    }

    private fun claim(
        id: UUID,
        request: LoginTransferUpdateRequest,
        fetcher: Fetcher<LoginTransfer>,
    ): LoginTransferUpdateResponse {
        val secret = request.secret?.takeIf { it.isNotBlank() }
            ?: throw CommonException.InvalidRequest()
        val normalizedDeviceName = normalizeText(request.deviceName, MAX_DEVICE_NAME_LENGTH)
            ?: throw CommonException.InvalidRequest()
        val platform = request.platform ?: throw CommonException.InvalidRequest()
        val normalizedClientVersion = normalizeText(request.clientVersion, MAX_CLIENT_VERSION_LENGTH)

        val transfer = findLocked(id) ?: throw LoginTransferException.NotFound()
        if (!credentialMatches(secret, transfer.qrSecretHash)) {
            throw LoginTransferException.NotFound()
        }
        expireIfNeeded(transfer)
        if (transfer.status != LoginTransferStatus.WAITING) {
            throw LoginTransferException.StatusConflict()
        }

        val now = Instant.now()
        val claimToken = newCredential()
        update(id) {
            set(table.status, LoginTransferStatus.CLAIMED)
            set(table.claimTokenHash, hashCredential(claimToken))
            set(table.deviceName, normalizedDeviceName)
            set(table.platform, platform)
            set(table.clientVersion, normalizedClientVersion)
            set(table.claimedAt, now)
        }
        val claimed = sql.findById(fetcher, id) ?: throw LoginTransferException.NotFound()
        return LoginTransferUpdateResponse(transfer = claimed, claimAccessToken = claimToken)
    }

    private fun decide(
        id: UUID,
        accountId: Long,
        targetStatus: LoginTransferStatus,
        fetcher: Fetcher<LoginTransfer>,
    ): LoginTransfer {
        val transfer = findLocked(id) ?: throw LoginTransferException.NotFound()
        if (transfer.accountId != accountId) throw CommonException.Forbidden()
        expireIfNeeded(transfer)
        if (transfer.status != LoginTransferStatus.CLAIMED) {
            throw LoginTransferException.StatusConflict()
        }

        val now = Instant.now()
        update(id) {
            set(table.status, targetStatus)
            if (targetStatus == LoginTransferStatus.AUTHORIZED) {
                set(table.authorizedAt, now)
            } else {
                set(table.closedAt, now)
            }
        }
        return sql.findById(fetcher, id) ?: throw LoginTransferException.NotFound()
    }

    /** 关闭该账号的旧活动交接：已到期的记为 EXPIRED，其余记为 CANCELLED。 */
    private fun closePreviousTransfers(accountId: Long, now: Instant) {
        sql.createUpdate(LoginTransfer::class) {
            set(
                table.status,
                case()
                    .match(table.expiresAt le now, LoginTransferStatus.EXPIRED)
                    .otherwise(LoginTransferStatus.CANCELLED),
            )
            set(table.closedAt, now)
            where(table.accountId eq accountId)
            where(table.status valueIn ACTIVE_STATUSES)
        }.execute()
    }

    /** 轮询读取路径：一次查询同时取回响应投影与鉴权所需的列，且不加行锁。 */
    private fun findForRead(id: UUID, fetcher: Fetcher<LoginTransfer>) =
        sql.createQuery(LoginTransfer::class) {
            where(table.id eq id)
            select(table.fetch(fetcher), table.accountId, table.claimTokenHash)
        }.execute().firstOrNull()

    private fun findLocked(id: UUID): LoginTransfer? =
        sql.createQuery(LoginTransfer::class) {
            where(table.id eq id)
            select(table)
        }.forUpdate().execute().firstOrNull()

    private fun update(id: UUID, block: KMutableUpdate<LoginTransfer>.() -> Unit) {
        sql.createUpdate(LoginTransfer::class) {
            block()
            where(table.id eq id)
        }.execute()
    }

    /** 把仍处于活动态的交接迁移为已过期，[scope] 负责限定迁移范围。 */
    private fun expireActive(now: Instant, scope: KMutableUpdate<LoginTransfer>.() -> Unit) {
        sql.createUpdate(LoginTransfer::class) {
            set(table.status, LoginTransferStatus.EXPIRED)
            set(table.closedAt, now)
            where(table.status valueIn ACTIVE_STATUSES)
            scope()
        }.execute()
    }

    /**
     * 读取路径的惰性过期：把到期的活动交接落库为 EXPIRED，并按同一投影返回真实终态。
     *
     * 与会改变状态的接口不同，查询不以 410 表达"已过期"，调用方总能读到终态。
     */
    private fun expireForRead(transfer: LoginTransfer): LoginTransfer {
        val now = Instant.now()
        if (transfer.status !in ACTIVE_STATUSES || now.isBefore(transfer.expiresAt)) return transfer
        expireActive(now) { where(table.id eq transfer.id) }
        return LoginTransfer(transfer) {
            status = LoginTransferStatus.EXPIRED
            closedAt = now
        }
    }

    /** 会改变状态的接口的惰性过期：落库后以 410 中断本次请求。 */
    private fun expireIfNeeded(transfer: LoginTransfer) {
        val now = Instant.now()
        if (transfer.status in ACTIVE_STATUSES && !now.isBefore(transfer.expiresAt)) {
            expireActive(now) { where(table.id eq transfer.id) }
            throw LoginTransferException.Expired()
        }
        if (transfer.status == LoginTransferStatus.EXPIRED) {
            throw LoginTransferException.Expired()
        }
    }

    private fun requireClaimToken(expectedHash: ByteArray?, claimToken: String) {
        if (expectedHash == null || !credentialMatches(claimToken, expectedHash)) {
            throw CommonException.AuthenticationFailed()
        }
    }

    private fun normalizeText(raw: String?, maxLength: Int): String? =
        raw?.trim()?.takeIf { it.isNotEmpty() }
            ?.also { if (it.length > maxLength) throw CommonException.InvalidRequest() }

    private fun newCredential(): String {
        val bytes = ByteArray(CREDENTIAL_BYTES)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun hashCredential(raw: String): ByteArray {
        val decoded = Base64.getUrlDecoder().decode(raw)
        require(decoded.size == CREDENTIAL_BYTES)
        return MessageDigest.getInstance("SHA-256").digest(decoded)
    }

    private fun credentialMatches(raw: String, expectedHash: ByteArray): Boolean {
        val actualHash = runCatching { hashCredential(raw) }.getOrNull() ?: return false
        return MessageDigest.isEqual(expectedHash, actualHash)
    }

    companion object {
        private const val CREDENTIAL_BYTES = 32
        private const val MAX_DEVICE_NAME_LENGTH = 100
        private const val MAX_CLIENT_VERSION_LENGTH = 100
        private val ACTIVE_STATUSES = listOf(
            LoginTransferStatus.WAITING,
            LoginTransferStatus.CLAIMED,
            LoginTransferStatus.AUTHORIZED,
        )
        private val TERMINAL_STATUSES = listOf(
            LoginTransferStatus.COMPLETED,
            LoginTransferStatus.REJECTED,
            LoginTransferStatus.CANCELLED,
            LoginTransferStatus.EXPIRED,
        )
    }
}
