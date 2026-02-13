package com.unirhy.e2e.support

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class E2eHttpClientTest {

    @Test
    fun `supports get with query params`() {
        withRequestCaptureServer { baseUrl, captured ->
            val client = E2eHttpClient(baseUrl)
            val response = client.get(
                "/query",
                query = mapOf(
                    "word" to "hello world",
                    "tags" to listOf("a", "b"),
                    "skip" to null,
                ),
            )

            assertEquals(200, response.statusCode())
            val request = requireNotNull(captured.get())
            assertEquals("GET", request.method)
            assertEquals("/query", request.path)
            assertEquals(
                mapOf(
                    "word" to listOf("hello world"),
                    "tags" to listOf("a", "b"),
                ),
                request.query,
            )
        }
    }

    @Test
    fun `supports array style query params`() {
        withRequestCaptureServer { baseUrl, captured ->
            val client = E2eHttpClient(baseUrl)
            val response = client.get(
                "/array-query",
                query = mapOf(
                    "tags" to arrayOf("x", "y"),
                    "ids" to intArrayOf(1, 2),
                ),
            )

            assertEquals(200, response.statusCode())
            val request = requireNotNull(captured.get())
            assertEquals("/array-query", request.path)
            assertEquals(
                mapOf(
                    "tags" to listOf("x", "y"),
                    "ids" to listOf("1", "2"),
                ),
                request.query,
            )
        }
    }

    @Test
    fun `supports post form body`() {
        withRequestCaptureServer { baseUrl, captured ->
            val client = E2eHttpClient(baseUrl)
            val response = client.post(
                "/form",
                form = mapOf(
                    "name" to "tester",
                    "roles" to arrayOf("alpha", "beta"),
                    "skip" to null,
                ),
            )

            assertEquals(200, response.statusCode())
            val request = requireNotNull(captured.get())
            assertEquals("POST", request.method)
            assertEquals("/form", request.path)
            assertEquals("application/x-www-form-urlencoded", request.contentType)
            assertEquals(
                mapOf(
                    "name" to listOf("tester"),
                    "roles" to listOf("alpha", "beta"),
                ),
                decodeForm(request.body),
            )
        }
    }

    @Test
    fun `empty form map still sends form content type`() {
        withRequestCaptureServer { baseUrl, captured ->
            val client = E2eHttpClient(baseUrl)
            val response = client.post("/form-empty", form = emptyMap())

            assertEquals(200, response.statusCode())
            val request = requireNotNull(captured.get())
            assertEquals("application/x-www-form-urlencoded", request.contentType)
            assertEquals("", request.body)
        }
    }

    @Test
    fun `supports put json body`() {
        withRequestCaptureServer { baseUrl, captured ->
            val client = E2eHttpClient(baseUrl)
            val response = client.put(
                "/json",
                json = mapOf("enabled" to true, "count" to 2),
            )

            assertEquals(200, response.statusCode())
            val request = requireNotNull(captured.get())
            assertEquals("PUT", request.method)
            assertEquals("/json", request.path)
            assertEquals("application/json", request.contentType)
            assertContains(request.body, "\"enabled\":true")
            assertContains(request.body, "\"count\":2")
        }
    }

    @Test
    fun `supports delete request`() {
        withRequestCaptureServer { baseUrl, captured ->
            val client = E2eHttpClient(baseUrl)
            val response = client.delete("/resource", query = mapOf("id" to 9))

            assertEquals(200, response.statusCode())
            val request = requireNotNull(captured.get())
            assertEquals("DELETE", request.method)
            assertEquals("/resource", request.path)
            assertEquals(mapOf("id" to listOf("9")), request.query)
        }
    }

    @Test
    fun `json and form cannot be used together`() {
        withRequestCaptureServer { baseUrl, _ ->
            val client = E2eHttpClient(baseUrl)
            assertFailsWith<IllegalArgumentException> {
                client.post(
                    "/invalid",
                    json = mapOf("a" to 1),
                    form = mapOf("b" to 2),
                )
            }
        }
    }

    private fun withRequestCaptureServer(
        testBlock: (baseUrl: String, captured: AtomicReference<CapturedRequest>) -> Unit,
    ) {
        val captured = AtomicReference<CapturedRequest>()
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/") { exchange ->
            captured.set(capture(exchange))
            val responseBody = """{"ok":true}"""
            exchange.sendResponseHeaders(200, responseBody.toByteArray(StandardCharsets.UTF_8).size.toLong())
            exchange.responseBody.use { it.write(responseBody.toByteArray(StandardCharsets.UTF_8)) }
        }
        server.start()

        try {
            val address = server.address
            testBlock("http://127.0.0.1:${address.port}", captured)
        } finally {
            server.stop(0)
        }
    }

    private fun capture(exchange: HttpExchange): CapturedRequest {
        val query = decodeForm(exchange.requestURI.rawQuery.orEmpty())
        val contentType = exchange.requestHeaders.getFirst("Content-Type")
        val body = exchange.requestBody.readAllBytes().toString(StandardCharsets.UTF_8)
        return CapturedRequest(
            method = exchange.requestMethod,
            path = exchange.requestURI.path,
            query = query,
            contentType = contentType,
            body = body,
        )
    }

    private fun decodeForm(rawQuery: String): Map<String, List<String>> {
        if (rawQuery.isBlank()) {
            return emptyMap()
        }
        val result = linkedMapOf<String, MutableList<String>>()
        rawQuery.split("&")
            .filter { it.isNotBlank() }
            .forEach { pair ->
                val parts = pair.split("=", limit = 2)
                val key = E2eHttpClient.urlDecode(parts[0])
                val value = E2eHttpClient.urlDecode(parts.getOrElse(1) { "" })
                result.computeIfAbsent(key) { mutableListOf() }.add(value)
            }
        return result.mapValues { it.value.toList() }
    }

    private data class CapturedRequest(
        val method: String,
        val path: String,
        val query: Map<String, List<String>>,
        val contentType: String?,
        val body: String,
    )
}
