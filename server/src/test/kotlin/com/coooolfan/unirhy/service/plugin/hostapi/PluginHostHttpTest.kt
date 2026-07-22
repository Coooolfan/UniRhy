package com.coooolfan.unirhy.service.plugin.hostapi

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import com.coooolfan.unirhy.model.storage.FileProviderFileSystem
import com.coooolfan.unirhy.model.storage.FileProviderOss
import com.coooolfan.unirhy.model.storage.FileProviderType
import com.coooolfan.unirhy.service.storage.FileSystemStorageNode
import com.coooolfan.unirhy.service.storage.OssStorageNode
import com.coooolfan.unirhy.service.storage.StorageNodeObjectService
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
        assertTrue(response.stored)
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

    @Test
    fun `download commits completed file to OSS node`() {
        val local = startServer()
        local.createContext("/oss-media") { exchange ->
            exchange.responseHeaders.add("Content-Type", "audio/mp4")
            respond(exchange, "oss-media")
        }
        val storage = mock(StorageNodeObjectService::class.java)
        val node = OssStorageNode(
            FileProviderOss {
                id = 9
                name = "oss-test"
                host = "https://oss.example.invalid"
                bucket = "music"
                accessKey = "test-access"
                secretKey = "test-secret"
                parentPath = null
                readonly = false
            },
        )
        `when`(storage.resolve(FileProviderType.OSS, 9)).thenReturn(node)
        doAnswer { null }.`when`(storage).commitDownloadedFile(
            any(OssStorageNode::class.java) ?: node,
            anyString(),
            any(File::class.java) ?: File("."),
            anyString(),
            anyBoolean(),
        )
        val request = objectMapper.readTree(
            """
            {
              "url": "${url("/oss-media")}",
              "destination": {"node": {"type": "OSS", "id": 9}, "objectKey": "imports/song.m4a"}
            }
            """.trimIndent(),
        ) as ObjectNode

        val response = downloadToStorage(request, storage)

        assertTrue(response.stored)
        assertEquals(HostStorageNodeReference("OSS", 9), response.destination.node)
        assertEquals("imports/song.m4a", response.destination.objectKey)
        verify(storage).commitDownloadedFile(
            eq(node) ?: node,
            eq("imports/song.m4a") ?: "imports/song.m4a",
            any(File::class.java) ?: File("."),
            eq("audio/mp4") ?: "audio/mp4",
            eq(false),
        )
    }

    @Test
    fun `download does not store non 2xx response`() {
        val local = startServer()
        local.createContext("/forbidden") { exchange ->
            val body = "denied".toByteArray()
            exchange.responseHeaders.add("Content-Type", "text/plain")
            exchange.sendResponseHeaders(403, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        val (storage, node) = writableStorage()
        val request = downloadRequest(url("/forbidden"), maxBytes = 1)

        val response = downloadToStorage(request, storage)

        assertEquals(403, response.status)
        assertFalse(response.stored)
        assertEquals(0, response.bytesWritten)
        assertEquals("text/plain", response.contentType)
        assertNull(response.sha256)
        assertEquals(HostStorageNodeReference("FS", 7), response.destination.node)
        assertEquals("imports/song.opus", response.destination.objectKey)
        verify(storage, never()).commitDownloadedFile(
            any(FileSystemStorageNode::class.java) ?: node,
            anyString(),
            any(File::class.java) ?: File("."),
            anyString(),
            anyBoolean(),
        )
    }

    @Test
    fun `download rejects known content length over max bytes`() {
        val local = startServer()
        local.createContext("/known-large") { exchange -> respond(exchange, "too-large") }
        val (storage, node) = writableStorage()
        val request = downloadRequest(url("/known-large"), maxBytes = 4)

        val ex = assertFailsWith<PluginHostException> { downloadToStorage(request, storage) }

        assertEquals(PluginHostErrorCode.RESPONSE_TOO_LARGE, ex.errorCode)
        verify(storage, never()).commitDownloadedFile(
            any(FileSystemStorageNode::class.java) ?: node,
            anyString(),
            any(File::class.java) ?: File("."),
            anyString(),
            anyBoolean(),
        )
    }

    @Test
    fun `download stops chunked response when it exceeds max bytes`() {
        val local = startServer()
        local.createContext("/chunked-large") { exchange ->
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.use { output ->
                output.write("first".toByteArray())
                output.flush()
                output.write("second".toByteArray())
            }
        }
        val (storage, node) = writableStorage()
        val request = downloadRequest(url("/chunked-large"), maxBytes = 7)

        val ex = assertFailsWith<PluginHostException> { downloadToStorage(request, storage) }

        assertEquals(PluginHostErrorCode.RESPONSE_TOO_LARGE, ex.errorCode)
        verify(storage, never()).commitDownloadedFile(
            any(FileSystemStorageNode::class.java) ?: node,
            anyString(),
            any(File::class.java) ?: File("."),
            anyString(),
            anyBoolean(),
        )
    }

    @Test
    fun `download requires max bytes to be positive`() {
        val storage = mock(StorageNodeObjectService::class.java)
        val request = downloadRequest("http://127.0.0.1/unused", maxBytes = 0)

        val ex = assertFailsWith<PluginHostException> { downloadToStorage(request, storage) }

        assertEquals(PluginHostErrorCode.INVALID_ARGUMENT, ex.errorCode)
    }

    @Test
    fun `download rejects max bytes outside long range`() {
        val storage = mock(StorageNodeObjectService::class.java)
        val request = objectMapper.readTree(
            """
            {
              "url": "http://127.0.0.1/unused",
              "maxBytes": 9223372036854775808,
              "destination": {"node": {"type": "FS", "id": 7}, "objectKey": "imports/song.opus"}
            }
            """.trimIndent(),
        ) as ObjectNode

        val ex = assertFailsWith<PluginHostException> { downloadToStorage(request, storage) }

        assertEquals(PluginHostErrorCode.INVALID_ARGUMENT, ex.errorCode)
    }

    @Test
    fun `same host redirect retains request credentials`() {
        val local = startServer()
        var cookie: String? = null
        var authorization: String? = null
        local.createContext("/same-start") { exchange ->
            exchange.responseHeaders.add("Location", "/same-end")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        local.createContext("/same-end") { exchange ->
            cookie = exchange.requestHeaders.getFirst("Cookie")
            authorization = exchange.requestHeaders.getFirst("Authorization")
            respond(exchange, "done")
        }

        sendFollowingRedirects(
            parseHttpUri(url("/same-start")),
            "GET",
            listOf(
                RequestHeader("Cookie", "session=same-host"),
                RequestHeader("Authorization", "Bearer same-host"),
            ),
        ).body().close()

        assertEquals("session=same-host", cookie)
        assertEquals("Bearer same-host", authorization)
    }

    @Test
    fun `cross host redirect removes credentials and retains media headers`() {
        val local = startServer()
        var cookie: String? = "not-called"
        var authorization: String? = "not-called"
        var proxyAuthorization: String? = "not-called"
        var userAgent: String? = null
        var referer: String? = null
        local.createContext("/cross-start") { exchange ->
            exchange.responseHeaders.add("Location", "http://localhost:${local.address.port}/cross-end")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        local.createContext("/cross-end") { exchange ->
            cookie = exchange.requestHeaders.getFirst("Cookie")
            authorization = exchange.requestHeaders.getFirst("Authorization")
            proxyAuthorization = exchange.requestHeaders.getFirst("Proxy-Authorization")
            userAgent = exchange.requestHeaders.getFirst("User-Agent")
            referer = exchange.requestHeaders.getFirst("Referer")
            respond(exchange, "done")
        }

        sendFollowingRedirects(
            parseHttpUri(url("/cross-start")),
            "GET",
            listOf(
                RequestHeader("Cookie", "session=cross-host"),
                RequestHeader("Authorization", "Bearer cross-host"),
                RequestHeader("Proxy-Authorization", "Basic cross-host"),
                RequestHeader("User-Agent", "UniRhy-Test"),
                RequestHeader("Referer", "https://media.example.invalid/items/1"),
            ),
        ).body().close()

        assertNull(cookie)
        assertNull(authorization)
        assertNull(proxyAuthorization)
        assertEquals("UniRhy-Test", userAgent)
        assertEquals("https://media.example.invalid/items/1", referer)
    }

    @Test
    fun `same hostname redirect to a different port removes credentials`() {
        val source = startServer()
        val destination = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).also { it.start() }
        try {
            var cookie: String? = "not-called"
            var authorization: String? = "not-called"
            var userAgent: String? = null
            source.createContext("/different-port-start") { exchange ->
                exchange.responseHeaders.add(
                    "Location",
                    "http://127.0.0.1:${destination.address.port}/different-port-end",
                )
                exchange.sendResponseHeaders(302, -1)
                exchange.close()
            }
            destination.createContext("/different-port-end") { exchange ->
                cookie = exchange.requestHeaders.getFirst("Cookie")
                authorization = exchange.requestHeaders.getFirst("Authorization")
                userAgent = exchange.requestHeaders.getFirst("User-Agent")
                respond(exchange, "done")
            }

            sendFollowingRedirects(
                parseHttpUri(url("/different-port-start")),
                "GET",
                listOf(
                    RequestHeader("Cookie", "session=different-port"),
                    RequestHeader("Authorization", "Bearer different-port"),
                    RequestHeader("User-Agent", "UniRhy-Test"),
                ),
            ).body().close()

            assertNull(cookie)
            assertNull(authorization)
            assertEquals("UniRhy-Test", userAgent)
        } finally {
            destination.stop(0)
        }
    }

    private fun startServer(): HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).also {
        server = it
        it.start()
    }

    private fun url(path: String): String = "http://127.0.0.1:${server!!.address.port}$path"

    private fun writableStorage(): Pair<StorageNodeObjectService, FileSystemStorageNode> {
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
        return storage to node
    }

    private fun downloadRequest(url: String, maxBytes: Long): ObjectNode = objectMapper.readTree(
        """
        {
          "url": "$url",
          "maxBytes": $maxBytes,
          "destination": {"node": {"type": "FS", "id": 7}, "objectKey": "imports/song.opus"}
        }
        """.trimIndent(),
    ) as ObjectNode

    private fun respond(exchange: HttpExchange, value: String) {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }
}
