package com.coooolfan.unirhy.service.task

import com.coooolfan.unirhy.model.AiModelConfig
import com.coooolfan.unirhy.model.AiRequestFormat
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EmbeddingClientTest {

    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `openai embedding format sends compatible request and parses indexed response`() {
        val capturedBodies = mutableListOf<String>()
        val server = startEmbeddingServer(capturedBodies)
        try {
            val client = EmbeddingClient(objectMapper)
            val embeddings = client.embed(
                config = AiModelConfig(
                    endpoint = "http://127.0.0.1:${server.address.port}/v1/embeddings",
                    model = "text-embedding-v4",
                    key = "test-key",
                    requestFormat = AiRequestFormat.OPENAI,
                ),
                texts = listOf("first", "second"),
            )

            val requestJson = objectMapper.readTree(capturedBodies.single())
            assertEquals("text-embedding-v4", requestJson["model"].asText())
            assertEquals("first", requestJson["input"][0].asText())
            assertEquals("second", requestJson["input"][1].asText())
            assertEquals("float", requestJson["encoding_format"].asText())
            assertFalse(requestJson.has("task"))
            assertFalse(requestJson.has("normalized"))

            assertContentEquals(floatArrayOf(1.0f, 1.5f), embeddings[0])
            assertContentEquals(floatArrayOf(2.0f, 2.5f), embeddings[1])
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `openai embedding format truncates input to provider limit`() {
        val capturedBodies = mutableListOf<String>()
        val server = startEmbeddingServer(capturedBodies)
        try {
            val client = EmbeddingClient(objectMapper)
            client.embed(
                config = AiModelConfig(
                    endpoint = "http://127.0.0.1:${server.address.port}/v1/embeddings",
                    model = "text-embedding-v4",
                    key = "test-key",
                    requestFormat = AiRequestFormat.OPENAI,
                ),
                texts = listOf("x".repeat(9000)),
            )

            val requestJson = objectMapper.readTree(capturedBodies.single())
            assertEquals(8192, requestJson["input"][0].asText().length)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `jina embedding format keeps existing request shape`() {
        val capturedBodies = mutableListOf<String>()
        val server = startEmbeddingServer(capturedBodies)
        try {
            val client = EmbeddingClient(objectMapper)
            client.embed(
                config = AiModelConfig(
                    endpoint = "http://127.0.0.1:${server.address.port}/v1/embeddings",
                    model = "jina-embeddings-v3",
                    key = "test-key",
                    requestFormat = AiRequestFormat.JINA,
                ),
                texts = listOf("lyrics"),
            )

            val requestJson = objectMapper.readTree(capturedBodies.single())
            assertEquals("jina-embeddings-v3", requestJson["model"].asText())
            assertEquals("retrieval.query", requestJson["task"].asText())
            assertTrue(requestJson["normalized"].asBoolean())
            assertEquals("lyrics", requestJson["input"][0].asText())
        } finally {
            server.stop(0)
        }
    }

    private fun startEmbeddingServer(capturedBodies: MutableList<String>): HttpServer {
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/v1/embeddings") { exchange ->
            capturedBodies += exchange.requestBody.readBytes().decodeToString()
            val response = """
                {
                  "object": "list",
                  "data": [
                    {"object": "embedding", "index": 1, "embedding": [2.0, 2.5]},
                    {"object": "embedding", "index": 0, "embedding": [1.0, 1.5]}
                  ]
                }
            """.trimIndent()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, response.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(response.toByteArray()) }
        }
        server.start()
        return server
    }
}
