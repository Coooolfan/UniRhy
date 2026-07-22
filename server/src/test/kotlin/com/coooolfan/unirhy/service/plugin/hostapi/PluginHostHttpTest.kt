package com.coooolfan.unirhy.service.plugin.hostapi

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import com.coooolfan.unirhy.model.storage.FileProviderFileSystem
import com.coooolfan.unirhy.model.storage.FileProviderType
import com.coooolfan.unirhy.service.storage.FileSystemStorageNode
import com.coooolfan.unirhy.service.storage.StorageNodeObjectService
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import tools.jackson.databind.node.ObjectNode
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.security.MessageDigest
import java.util.HexFormat
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class PluginHostHttpTest {
    private val objectMapper = jacksonObjectMapper()
    private var server: HttpServer? = null

    @AfterTest
    fun stopServer() {
        server?.stop(0)
    }

    @Test
    fun `http request sends method headers and body`() {
        val local = startServer()
        local.createContext("/echo") { exchange ->
            val requestBody = exchange.requestBody.use { it.readAllBytes() }.toString(StandardCharsets.UTF_8)
            val responseBody = "${exchange.requestMethod}:${exchange.requestHeaders.getFirst("X-Plugin")}:$requestBody"
                .toByteArray(StandardCharsets.UTF_8)
            exchange.responseHeaders.add("X-Reply", "ok")
            exchange.sendResponseHeaders(201, responseBody.size.toLong())
            exchange.responseBody.use { it.write(responseBody) }
        }

        val request = objectMapper.readTree(
            """
            {
              "method": "POST",
              "url": "${url("/echo")}",
              "headers": {"X-Plugin": "host-api"},
              "bodyBase64": "${Base64.getEncoder().encodeToString("payload".toByteArray())}"
            }
            """.trimIndent(),
        ) as ObjectNode
        val response = executeHttpRequest(request)

        assertEquals(201, response.status)
        assertEquals("ok", response.headers["x-reply"]?.single())
        assertEquals("POST:host-api:payload", String(Base64.getDecoder().decode(response.bodyBase64)))
    }

    @Test
    fun `known oversized response is rejected before body read`() {
        ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1")).use { listener ->
            val responder = thread(isDaemon = true) {
                listener.accept().use { socket ->
                    val reader = socket.getInputStream().bufferedReader()
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) break
                    }
                    socket.getOutputStream().apply {
                        write(
                            "HTTP/1.1 200 OK\r\nContent-Length: 268435457\r\nConnection: close\r\n\r\n"
                                .toByteArray(StandardCharsets.US_ASCII),
                        )
                        flush()
                    }
                    Thread.sleep(500)
                }
            }
            val request = objectMapper.readTree(
                """{"method":"GET","url":"http://127.0.0.1:${listener.localPort}/large"}""",
            ) as ObjectNode

            val ex = assertFailsWith<PluginHostException> { executeHttpRequest(request) }
            assertEquals(PluginHostErrorCode.RESPONSE_TOO_LARGE, ex.errorCode)
            responder.join()
        }
    }

    @Test
    fun `download redirect handling follows five hops and rejects the sixth`() {
        val local = startServer()
        repeat(6) { index ->
            local.createContext("/redirect-$index") { exchange ->
                exchange.responseHeaders.add("Location", "/redirect-${index + 1}")
                exchange.sendResponseHeaders(302, -1)
                exchange.close()
            }
        }
        local.createContext("/redirect-6") { exchange -> respond(exchange, "done") }

        val ex = assertFailsWith<PluginHostException> {
            sendFollowingRedirects(parseHttpUri(url("/redirect-0")), "GET", emptyList())
        }
        assertEquals(PluginHostErrorCode.INTERNAL, ex.errorCode)
    }

    @Test
    fun `download streams chunked response and commits completed file`() {
        val local = startServer()
        local.createContext("/media") { exchange ->
            assertEquals("bytes=10-", exchange.requestHeaders.getFirst("Range"))
            exchange.responseHeaders.add("Content-Type", "audio/opus")
            exchange.sendResponseHeaders(206, 0)
            exchange.responseBody.use { it.write("media-payload".toByteArray()) }
        }
        val storage = mock(StorageNodeObjectService::class.java)
        val node = FileSystemStorageNode(
            FileProviderFileSystem {
                id = 7
                name = "test"
                parentPath = "/tmp"
                readonly = false
            },
        )
        `when`(storage.resolve(FileProviderType.FILE_SYSTEM, 7)).thenReturn(node)
        var committedBytes: ByteArray? = null
        var temporaryFile: File? = null
        doAnswer { invocation ->
            temporaryFile = invocation.getArgument(2)
            committedBytes = temporaryFile!!.readBytes()
            null
        }.`when`(storage).commitDownloadedFile(
            any(FileSystemStorageNode::class.java) ?: node,
            anyString(),
            any(File::class.java) ?: File("."),
            anyString(),
            anyBoolean(),
        )
        val request = objectMapper.readTree(
            """
            {
              "url": "${url("/media")}",
              "headers": {"Range": "bytes=10-"},
              "destination": {"node": {"type": "FS", "id": 7}, "objectKey": "imports/song.opus"}
            }
            """.trimIndent(),
        ) as ObjectNode

        val response = downloadToStorage(request, storage)

        assertEquals(206, response.status)
        assertEquals(13, response.bytesWritten)
        assertEquals("audio/opus", response.contentType)
        assertEquals(
            HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest("media-payload".toByteArray())),
            response.sha256,
        )
        assertEquals("media-payload", committedBytes!!.toString(StandardCharsets.UTF_8))
        assertFalse(temporaryFile!!.exists())
        verify(storage).commitDownloadedFile(node, "imports/song.opus", temporaryFile!!, "audio/opus", false)
    }

    private fun startServer(): HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).also {
        server = it
        it.start()
    }

    private fun url(path: String): String = "http://127.0.0.1:${server!!.address.port}$path"

    private fun respond(exchange: HttpExchange, value: String) {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }
}
