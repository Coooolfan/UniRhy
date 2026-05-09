package com.coooolfan.unirhy.sync.ws

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.server.HandshakeInterceptor
import java.util.UUID

@Component
class PlaybackSyncHandshakeInterceptor(
    private val authenticator: PlaybackSyncAuthenticator,
    @Value("\${sa-token.token-name:unirhy-token}")
    private val tokenName: String,
) : HandshakeInterceptor {
    override fun beforeHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        attributes: MutableMap<String, Any>,
    ): Boolean {
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
