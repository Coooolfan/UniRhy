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
    @Value("\${sa-token.token-name}") private val tokenName: String,
) : HandshakeInterceptor {
    override fun beforeHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        attributes: MutableMap<String, Any>,
    ): Boolean {
        val tokenValue = extractCookie(request, tokenName)
        val accountId = tokenValue?.let(authenticator::authenticate)
        if (tokenValue == null || accountId == null) {
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

    private fun extractCookie(
        request: ServerHttpRequest,
        cookieName: String,
    ): String? {
        return request.headers.getOrEmpty(HttpHeaders.COOKIE)
            .asSequence()
            .flatMap { it.split(';').asSequence() }
            .map(String::trim)
            .firstNotNullOfOrNull { cookie ->
                val index = cookie.indexOf('=')
                if (index <= 0) {
                    return@firstNotNullOfOrNull null
                }
                val name = cookie.substring(0, index).trim()
                if (name != cookieName) {
                    return@firstNotNullOfOrNull null
                }
                cookie.substring(index + 1).trim()
            }
    }
}
