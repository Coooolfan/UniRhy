package com.coooolfan.unirhy.sync.ws

import org.junit.jupiter.api.Test
import org.springframework.http.server.ServletServerHttpRequest
import org.springframework.http.server.ServletServerHttpResponse
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.socket.handler.TextWebSocketHandler
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PlaybackSyncHandshakeInterceptorTest {

    private val interceptor = PlaybackSyncHandshakeInterceptor()

    @Test
    fun `beforeHandshake accepts all connections and sets sessionId`() {
        val attributes = mutableMapOf<String, Any>()
        val response = MockHttpServletResponse()
        val accepted = interceptor.beforeHandshake(
            request = servletRequest(),
            response = ServletServerHttpResponse(response),
            wsHandler = TextWebSocketHandler(),
            attributes = attributes,
        )

        assertTrue(accepted)
        assertNotNull(attributes[PlaybackSyncSessionAttributes.SESSION_ID] as? String)
    }

    private fun servletRequest(): ServletServerHttpRequest {
        val request = MockHttpServletRequest("GET", "/ws/playback-sync")
        return ServletServerHttpRequest(request)
    }
}
