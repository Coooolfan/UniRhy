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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaybackSyncHandshakeInterceptorTest {

    private val interceptor = PlaybackSyncHandshakeInterceptor(
        authenticator = FakePlaybackSyncAuthenticator(mapOf("valid-token" to 42L)),
        tokenName = "unirhy-token",
        allowedOriginsRaw = "http://localhost:5173,http://127.0.0.1:5173",
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

    @Test
    fun `beforeHandshake rejects cross-site origin carrying cookie credentials`() {
        val attributes = mutableMapOf<String, Any>()
        val response = MockHttpServletResponse()
        val request = MockHttpServletRequest("GET", "/ws/playback-sync").apply {
            addHeader("Origin", "https://evil.example")
            addHeader("Cookie", "unirhy-token=valid-token")
        }
        val accepted = interceptor.beforeHandshake(
            request = ServletServerHttpRequest(request),
            response = ServletServerHttpResponse(response),
            wsHandler = TextWebSocketHandler(),
            attributes = attributes,
        )

        assertFalse(accepted)
        assertEquals(HttpStatus.FORBIDDEN.value(), response.status)
        assertNull(attributes[PlaybackSyncSessionAttributes.ACCOUNT_ID])
    }

    @Test
    fun `beforeHandshake rejects opaque null origin`() {
        val attributes = mutableMapOf<String, Any>()
        val response = MockHttpServletResponse()
        val request = MockHttpServletRequest("GET", "/ws/playback-sync").apply {
            addHeader("Origin", "null")
        }
        val accepted = interceptor.beforeHandshake(
            request = ServletServerHttpRequest(request),
            response = ServletServerHttpResponse(response),
            wsHandler = TextWebSocketHandler(),
            attributes = attributes,
        )

        assertFalse(accepted)
        assertEquals(HttpStatus.FORBIDDEN.value(), response.status)
    }

    @Test
    fun `beforeHandshake accepts whitelisted origin with cookie credentials`() {
        val attributes = mutableMapOf<String, Any>()
        val response = MockHttpServletResponse()
        val request = MockHttpServletRequest("GET", "/ws/playback-sync").apply {
            addHeader("Origin", "http://127.0.0.1:5173")
            addHeader("Cookie", "unirhy-token=valid-token")
        }
        val accepted = interceptor.beforeHandshake(
            request = ServletServerHttpRequest(request),
            response = ServletServerHttpResponse(response),
            wsHandler = TextWebSocketHandler(),
            attributes = attributes,
        )

        assertTrue(accepted)
        assertEquals(42L, attributes[PlaybackSyncSessionAttributes.ACCOUNT_ID])
    }

    @Test
    fun `beforeHandshake accepts same-host origin regardless of port and scheme`() {
        val attributes = mutableMapOf<String, Any>()
        val response = MockHttpServletResponse()
        val request = MockHttpServletRequest("GET", "/ws/playback-sync").apply {
            serverName = "music.example.com"
            addHeader("Origin", "https://music.example.com")
            addHeader("Cookie", "unirhy-token=valid-token")
        }
        val accepted = interceptor.beforeHandshake(
            request = ServletServerHttpRequest(request),
            response = ServletServerHttpResponse(response),
            wsHandler = TextWebSocketHandler(),
            attributes = attributes,
        )

        assertTrue(accepted)
        assertEquals(42L, attributes[PlaybackSyncSessionAttributes.ACCOUNT_ID])
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
