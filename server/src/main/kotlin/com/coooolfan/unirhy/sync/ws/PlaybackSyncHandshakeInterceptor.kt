package com.coooolfan.unirhy.sync.ws

import com.coooolfan.unirhy.config.AllowedOrigins
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.server.HandshakeInterceptor
import java.net.URI
import java.util.UUID

@Component
class PlaybackSyncHandshakeInterceptor(
    private val authenticator: PlaybackSyncAuthenticator,
    @Value("\${sa-token.token-name:unirhy-token}")
    private val tokenName: String,
    @Value(AllowedOrigins.VALUE_EXPRESSION)
    allowedOriginsRaw: String,
) : HandshakeInterceptor {
    private val allowedOrigins = AllowedOrigins.parse(allowedOriginsRaw)

    override fun beforeHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        attributes: MutableMap<String, Any>,
    ): Boolean {
        if (!isTrustedOrigin(request)) {
            response.setStatusCode(HttpStatus.FORBIDDEN)
            return false
        }

        val tokenValue = resolveToken(request)
        if (tokenValue == null) {
            attributes[PlaybackSyncSessionAttributes.SESSION_ID] = UUID.randomUUID().toString()
            return true
        }

        val accountId = authenticator.authenticate(tokenValue)
        if (accountId == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED)
            return false
        }

        attributes[PlaybackSyncSessionAttributes.ACCOUNT_ID] = accountId
        attributes[PlaybackSyncSessionAttributes.TOKEN_VALUE] = tokenValue
        attributes[PlaybackSyncSessionAttributes.SESSION_ID] = UUID.randomUUID().toString()
        return true
    }

    override fun afterHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        exception: Exception?,
    ) {
    }

    /**
     * 浏览器发起的握手都会携带 Origin，且 Cookie 会被自动附带，因此不可信 Origin 必须整体拒绝，
     * 否则任意网页可借用户 Cookie 建立连接（Cross-Site WebSocket Hijacking）。
     * 信任规则：无 Origin（非浏览器客户端，凭 HELLO 消息内的 token 认证）、CORS 白名单命中、
     * 或 Origin 主机名与请求主机名一致（同源部署；不比较端口与协议，因为 TLS 终止代理后端
     * 看到的端口/协议与公网侧不一致）。
     */
    private fun isTrustedOrigin(request: ServerHttpRequest): Boolean {
        val origin = request.headers.origin?.trim()?.takeIf { it.isNotEmpty() } ?: return true
        if (origin in allowedOrigins) {
            return true
        }
        val originHost = runCatching { URI(origin).host }.getOrNull() ?: return false
        val requestHost = request.uri.host ?: return false
        return originHost.equals(requestHost, ignoreCase = true)
    }

    private fun resolveToken(request: ServerHttpRequest): String? {
        request.headers.getFirst(tokenName)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }

        return request.headers[HttpHeaders.COOKIE]
            ?.asSequence()
            ?.flatMap { it.split(';').asSequence() }
            ?.map { it.trim() }
            ?.firstNotNullOfOrNull { cookie ->
                cookie.substringBefore('=', missingDelimiterValue = "")
                    .takeIf { it == tokenName }
                    ?.let { cookie.substringAfter('=', missingDelimiterValue = "").trim() }
                    ?.takeIf { it.isNotEmpty() }
            }
    }
}
