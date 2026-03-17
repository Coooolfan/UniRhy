package com.coooolfan.unirhy.config

import cn.dev33.satoken.servlet.model.SaRequestForServlet
import cn.dev33.satoken.servlet.model.SaResponseForServlet
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SaTokenConfigTest {

    @Test
    fun `allowed origin returns exact origin and credentials headers`() {
        val request = MockHttpServletRequest().apply {
            addHeader("Origin", "http://localhost:5173")
        }
        val response = MockHttpServletResponse()

        SaTokenConfig("http://localhost:5173,http://127.0.0.1:5173")
            .applyCorsHeaders(SaRequestForServlet(request), SaResponseForServlet(response))

        assertEquals("http://localhost:5173", response.getHeader("Access-Control-Allow-Origin"))
        assertEquals("true", response.getHeader("Access-Control-Allow-Credentials"))
        assertEquals(
            "GET,POST,PUT,PATCH,DELETE,HEAD,OPTIONS",
            response.getHeader("Access-Control-Allow-Methods"),
        )
        assertEquals(
            "content-type, tenant, unirhy-token, range, if-none-match, if-modified-since, if-range",
            response.getHeader("Access-Control-Allow-Headers"),
        )
        assertEquals("3600", response.getHeader("Access-Control-Max-Age"))
        assertEquals(
            listOf("Origin", "Access-Control-Request-Method", "Access-Control-Request-Headers"),
            response.getHeaders("Vary"),
        )
    }

    @Test
    fun `preflight request echoes requested headers for allowed origin`() {
        val request = MockHttpServletRequest("OPTIONS", "/api/tokens").apply {
            addHeader("Origin", "http://127.0.0.1:5173")
            addHeader("Access-Control-Request-Method", "POST")
            addHeader("Access-Control-Request-Headers", "content-type,unirhy-token,range")
        }
        val response = MockHttpServletResponse()

        SaTokenConfig("http://localhost:5173,http://127.0.0.1:5173")
            .applyCorsHeaders(SaRequestForServlet(request), SaResponseForServlet(response))

        assertEquals("http://127.0.0.1:5173", response.getHeader("Access-Control-Allow-Origin"))
        assertEquals(
            "content-type,unirhy-token,range",
            response.getHeader("Access-Control-Allow-Headers"),
        )
    }

    @Test
    fun `disallowed origin does not receive cors allow headers`() {
        val request = MockHttpServletRequest().apply {
            addHeader("Origin", "http://malicious.example")
        }
        val response = MockHttpServletResponse()

        SaTokenConfig("http://localhost:5173,http://127.0.0.1:5173")
            .applyCorsHeaders(SaRequestForServlet(request), SaResponseForServlet(response))

        assertNull(response.getHeader("Access-Control-Allow-Origin"))
        assertNull(response.getHeader("Access-Control-Allow-Credentials"))
        assertEquals(emptyList(), response.getHeaders("Vary"))
    }
}
