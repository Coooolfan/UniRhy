package com.coooolfan.unirhy.sync.ws

import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.server.ServletServerHttpRequest
import org.springframework.http.server.ServletServerHttpResponse
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.socket.handler.TextWebSocketHandler
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PlaybackSyncHandshakeInterceptorTest {

    private val authenticator = FakePlaybackSyncAuthenticator(
        tokenToAccountId = mapOf("valid-token" to 42L),
    )
    private val interceptor = PlaybackSyncHandshakeInterceptor(
        authenticator = authenticator,
        tokenName = "unirhy-token",
    )

    @Test
    fun `beforeHandshake rejects request without auth cookie`() {
        val attributes = mutableMapOf<String, Any>()
        val response = MockHttpServletResponse()
        val accepted = interceptor.beforeHandshake(
            request = servletRequest(),
            response = ServletServerHttpResponse(response),
            wsHandler = TextWebSocketHandler(),
            attributes = attributes,
        )

        assertFalse(accepted)
        assertEquals(HttpStatus.UNAUTHORIZED.value(), response.status)
        assertTrue(attributes.isEmpty())
    }

    @Test
    fun `beforeHandshake rejects invalid token`() {
        val response = MockHttpServletResponse()
        val attributes = mutableMapOf<String, Any>()

        val accepted = interceptor.beforeHandshake(
            request = servletRequest("unirhy-token=invalid-token"),
            response = ServletServerHttpResponse(response),
            wsHandler = TextWebSocketHandler(),
            attributes = attributes,
        )

        assertFalse(accepted)
        assertEquals(HttpStatus.UNAUTHORIZED.value(), response.status)
        assertTrue(attributes.isEmpty())
    }

    @Test
    fun `beforeHandshake accepts valid token and writes session attributes`() {
        val response = MockHttpServletResponse()
        val attributes = mutableMapOf<String, Any>()

        val accepted = interceptor.beforeHandshake(
            request = servletRequest("foo=bar; unirhy-token=valid-token"),
            response = ServletServerHttpResponse(response),
            wsHandler = TextWebSocketHandler(),
            attributes = attributes,
        )

        assertTrue(accepted)
        assertEquals(42L, attributes[PlaybackSyncSessionAttributes.ACCOUNT_ID])
        assertEquals("valid-token", attributes[PlaybackSyncSessionAttributes.TOKEN_VALUE])
        assertNotNull(attributes[PlaybackSyncSessionAttributes.SESSION_ID] as? String)
    }

    private fun servletRequest(cookieHeader: String? = null): ServletServerHttpRequest {
        val request = MockHttpServletRequest("GET", "/ws/playback-sync")
        if (cookieHeader != null) {
            request.addHeader(HttpHeaders.COOKIE, cookieHeader)
        }
        return ServletServerHttpRequest(request)
    }
}

private class FakePlaybackSyncAuthenticator(
    private val tokenToAccountId: Map<String, Long>,
) : PlaybackSyncAuthenticator {
    override fun authenticate(tokenValue: String): Long? = tokenToAccountId[tokenValue]
}
