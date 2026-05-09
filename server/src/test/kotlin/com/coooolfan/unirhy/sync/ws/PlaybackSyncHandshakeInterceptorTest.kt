package com.coooolfan.unirhy.sync.ws

import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.server.ServletServerHttpRequest
import org.springframework.http.server.ServletServerHttpResponse
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.socket.handler.TextWebSocketHandler
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackSyncHandshakeInterceptorTest {

    private val interceptor = PlaybackSyncHandshakeInterceptor(
        authenticator = FakePlaybackSyncAuthenticator(mapOf("valid-token" to 42L)),
        tokenName = "unirhy-token",
    )

    @Test
    fun `beforeHandshake accepts authenticated connection and sets session attributes`() {
        val attributes = mutableMapOf<String, Any>()
        val response = MockHttpServletResponse()
        val accepted = interceptor.beforeHandshake(
            request = servletRequest("valid-token"),
            response = ServletServerHttpResponse(response),
            wsHandler = TextWebSocketHandler(),
            attributes = attributes,
        )

        assertTrue(accepted)
        assertEquals(42L, attributes[PlaybackSyncSessionAttributes.ACCOUNT_ID])
        assertEquals("valid-token", attributes[PlaybackSyncSessionAttributes.TOKEN_VALUE])
        assertNotNull(attributes[PlaybackSyncSessionAttributes.SESSION_ID] as? String)
    }

    @Test
    fun `beforeHandshake accepts missing token as pending connection`() {
        val attributes = mutableMapOf<String, Any>()
        val response = MockHttpServletResponse()
        val accepted = interceptor.beforeHandshake(
            request = servletRequest(null),
            response = ServletServerHttpResponse(response),
            wsHandler = TextWebSocketHandler(),
            attributes = attributes,
        )

        assertTrue(accepted)
        assertNotNull(attributes[PlaybackSyncSessionAttributes.SESSION_ID] as? String)
    }

    @Test
    fun `beforeHandshake rejects invalid token`() {
        val attributes = mutableMapOf<String, Any>()
        val response = MockHttpServletResponse()
        val accepted = interceptor.beforeHandshake(
            request = servletRequest("invalid-token"),
            response = ServletServerHttpResponse(response),
            wsHandler = TextWebSocketHandler(),
            attributes = attributes,
        )

        assertFalse(accepted)
        assertEquals(HttpStatus.UNAUTHORIZED.value(), response.status)
    }

    private fun servletRequest(token: String?): ServletServerHttpRequest {
        val request = MockHttpServletRequest("GET", "/ws/playback-sync")
        token?.let { request.addHeader("unirhy-token", it) }
        return ServletServerHttpRequest(request)
    }
}

private class FakePlaybackSyncAuthenticator(
    private val tokenToAccountId: Map<String, Long>,
) : PlaybackSyncAuthenticator {
    override fun authenticate(tokenValue: String): Long? = tokenToAccountId[tokenValue]
}
