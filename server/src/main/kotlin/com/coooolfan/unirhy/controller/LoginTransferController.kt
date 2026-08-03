package com.coooolfan.unirhy.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import cn.dev33.satoken.stp.StpUtil
import com.coooolfan.unirhy.error.CommonException
import com.coooolfan.unirhy.error.LoginTransferException
import com.coooolfan.unirhy.model.LoginTransfer
import com.coooolfan.unirhy.model.by
import com.coooolfan.unirhy.model.dto.LoginTransferCreateResponse
import com.coooolfan.unirhy.model.dto.LoginTransferUpdateRequest
import com.coooolfan.unirhy.model.dto.LoginTransferUpdateResponse
import com.coooolfan.unirhy.service.LoginTransferAuthorization
import com.coooolfan.unirhy.service.LoginTransferService
import jakarta.servlet.http.HttpServletResponse
import org.babyfish.jimmer.client.FetchBy
import org.babyfish.jimmer.sql.fetcher.Fetcher
import org.babyfish.jimmer.sql.kt.fetcher.newFetcher
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/login-transfers")
class LoginTransferController(
    private val service: LoginTransferService,
) {
    @SaCheckLogin
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(response: HttpServletResponse): LoginTransferCreateResponse {
        val result = service.create(StpUtil.getLoginIdAsLong())
        response.setHeader(HttpHeaders.LOCATION, "/api/login-transfers/${result.id}")
        response.setHeader(HttpHeaders.CACHE_CONTROL, NO_STORE)
        return result
    }

    /**
     * 查询登录交接状态
     *
     * 原设备凭账号会话查询，新设备凭认领访问令牌查询，后者只获得完成流程所需的最小状态。
     * 已进入终态（含过期）的交接同样返回 `200`，由调用方读取 `status` 判断。
     */
    @GetMapping("/{id}")
    @Throws(
        CommonException.AuthenticationFailed::class,
        CommonException.Forbidden::class,
        LoginTransferException.NotFound::class,
    )
    fun get(
        @PathVariable id: UUID,
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
        response: HttpServletResponse,
    ): @FetchBy("SOURCE_TRANSFER_FETCHER") LoginTransfer {
        response.setHeader(HttpHeaders.CACHE_CONTROL, NO_STORE)
        return when (val auth = resolveAuthorization(authorization)) {
            is LoginTransferAuthorization.Account ->
                service.getForAccount(id, auth.accountId, SOURCE_TRANSFER_FETCHER)

            is LoginTransferAuthorization.Claim ->
                service.getForClaim(id, auth.token, CLAIM_TRANSFER_FETCHER)

            LoginTransferAuthorization.None -> throw CommonException.AuthenticationFailed()
        }
    }

    @PatchMapping("/{id}")
    @Throws(
        CommonException.AuthenticationFailed::class,
        CommonException.Forbidden::class,
        CommonException.InvalidRequest::class,
        LoginTransferException.NotFound::class,
        LoginTransferException.StatusConflict::class,
        LoginTransferException.Expired::class,
    )
    fun update(
        @PathVariable id: UUID,
        @RequestBody request: LoginTransferUpdateRequest,
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
        response: HttpServletResponse,
    ): LoginTransferUpdateResponse {
        response.setHeader(HttpHeaders.CACHE_CONTROL, NO_STORE)
        return service.applyUpdate(
            id = id,
            auth = resolveAuthorization(authorization),
            request = request,
            sourceFetcher = SOURCE_TRANSFER_FETCHER,
            claimFetcher = CLAIM_TRANSFER_FETCHER,
        )
    }

    @SaCheckLogin
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun cancel(@PathVariable id: UUID) {
        service.cancel(id, StpUtil.getLoginIdAsLong())
    }

    @PostMapping("/{id}/tokens")
    @ResponseStatus(HttpStatus.CREATED)
    fun createToken(
        @PathVariable id: UUID,
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
        response: HttpServletResponse,
    ): TokenLoginResponse {
        val auth = resolveAuthorization(authorization) as? LoginTransferAuthorization.Claim
            ?: throw CommonException.AuthenticationFailed()
        val token = service.createToken(id, auth.token)
        response.setHeader(HttpHeaders.LOCATION, "/api/tokens/current")
        response.setHeader(HttpHeaders.CACHE_CONTROL, NO_STORE)
        return TokenLoginResponse(token)
    }

    private fun resolveAuthorization(authorization: String?): LoginTransferAuthorization {
        val accountLoggedIn = StpUtil.isLogin()
        val claimToken = authorization
            ?.takeIf { it.startsWith(BEARER_PREFIX, ignoreCase = true) }
            ?.substring(BEARER_PREFIX.length)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        if (authorization != null && claimToken == null) throw CommonException.AuthenticationFailed()
        if (accountLoggedIn && claimToken != null) throw CommonException.InvalidRequest()
        return when {
            accountLoggedIn -> LoginTransferAuthorization.Account(StpUtil.getLoginIdAsLong())
            claimToken != null -> LoginTransferAuthorization.Claim(claimToken)
            else -> LoginTransferAuthorization.None
        }
    }

    companion object {
        /** 原设备视图：可以看到认领设备的信息。 */
        val SOURCE_TRANSFER_FETCHER: Fetcher<LoginTransfer> =
            newFetcher(LoginTransfer::class).by {
                deviceName()
                platform()
                clientVersion()
                status()
                createdAt()
                expiresAt()
                claimedAt()
                authorizedAt()
                closedAt()
            }

        /**
         * 新设备视图：只包含推进流程所需的状态与时间。
         *
         * 这里刻意不返回账号或设备信息——今后新增的原设备专属字段，
         * 只有显式加进本 fetcher 才会对新设备可见。
         */
        val CLAIM_TRANSFER_FETCHER: Fetcher<LoginTransfer> =
            newFetcher(LoginTransfer::class).by {
                status()
                createdAt()
                expiresAt()
                authorizedAt()
                closedAt()
            }

        private const val BEARER_PREFIX = "Bearer "
        private const val NO_STORE = "no-store"
    }
}
