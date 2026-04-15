package com.unirhy.e2e.support

import com.coooolfan.unirhy.sync.protocol.PlaybackSyncMessageType
import com.fasterxml.jackson.databind.JsonNode
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit
import kotlin.test.fail

data class E2eWebSocketMessage(
    val type: PlaybackSyncMessageType,
    val payload: JsonNode,
    val raw: JsonNode,
)

data class E2eWebSocketClose(
    val statusCode: Int,
    val reason: String,
)

class E2eWebSocketClient private constructor(
    private val webSocket: WebSocket,
    private val messages: LinkedBlockingDeque<E2eWebSocketMessage>,
    private val closeFuture: CompletableFuture<E2eWebSocketClose>,
    private val errorFuture: CompletableFuture<Throwable>,
) : AutoCloseable {

    fun send(
        type: PlaybackSyncMessageType,
        payload: Any,
    ) {
        val encoded = E2eJson.mapper.writeValueAsString(
            linkedMapOf(
                "type" to type.name,
                "payload" to payload,
            ),
        )
        webSocket.sendText(encoded, true).join()
    }

    fun awaitMessage(
        type: PlaybackSyncMessageType,
        timeout: Duration = DEFAULT_TIMEOUT,
    ): E2eWebSocketMessage {
        val deadlineNs = System.nanoTime() + timeout.toNanos()
        val skipped = mutableListOf<E2eWebSocketMessage>()

        try {
            while (true) {
                failIfErrored()
                val remainingNs = deadlineNs - System.nanoTime()
                if (remainingNs <= 0L) {
                    fail("Timed out waiting for WebSocket message type=${type.name}")
                }
                val next = messages.poll(remainingNs, TimeUnit.NANOSECONDS)
                if (next == null) {
                    failIfClosed()
                    continue
                }
                if (next.type == type) {
                    return next
                }
                skipped += next
            }
        } finally {
            restoreSkipped(skipped)
        }
    }

    fun drainMessages(timeout: Duration = SHORT_TIMEOUT): List<E2eWebSocketMessage> {
        val drained = mutableListOf<E2eWebSocketMessage>()
        val first = messages.poll(timeout.toMillis(), TimeUnit.MILLISECONDS)
        if (first != null) {
            drained += first
        }
        while (true) {
            val next = messages.poll() ?: break
            drained += next
        }
        return drained
    }

    fun awaitClose(timeout: Duration = DEFAULT_TIMEOUT): E2eWebSocketClose {
        val deadlineNs = System.nanoTime() + timeout.toNanos()
        while (true) {
            errorFuture.getNow(null)?.let { throw AssertionError("WebSocket failed before closing", it) }
            closeFuture.getNow(null)?.let { return it }
            val remainingNs = deadlineNs - System.nanoTime()
            if (remainingNs <= 0L) {
                fail("Timed out waiting for WebSocket close")
            }
            Thread.sleep(minOf(TimeUnit.NANOSECONDS.toMillis(remainingNs), 20L))
        }
    }

    override fun close() {
        runCatching {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "client_close").join()
        }
    }

    private fun restoreSkipped(skipped: List<E2eWebSocketMessage>) {
        skipped.asReversed().forEach(messages::addFirst)
    }

    private fun failIfClosed() {
        closeFuture.getNow(null)?.let {
            fail("WebSocket closed before expected message: code=${it.statusCode} reason=${it.reason}")
        }
    }

    private fun failIfErrored() {
        errorFuture.getNow(null)?.let {
            throw AssertionError("WebSocket failed before expected message", it)
        }
    }

    companion object {
        private val DEFAULT_TIMEOUT: Duration = Duration.ofSeconds(5)
        private val SHORT_TIMEOUT: Duration = Duration.ofMillis(200)

        fun connect(
            baseUrl: String,
            authToken: String? = null,
        ): E2eWebSocketClient {
            val messages = LinkedBlockingDeque<E2eWebSocketMessage>()
            val closeFuture = CompletableFuture<E2eWebSocketClose>()
            val errorFuture = CompletableFuture<Throwable>()
            val listener = Listener(messages, closeFuture, errorFuture)

            val builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build()
                .newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(10))

            authToken?.let { builder.header("unirhy-token", it) }

            val webSocket = builder.buildAsync(
                URI.create(toWebSocketUrl(baseUrl)),
                listener,
            ).join()

            return E2eWebSocketClient(
                webSocket = webSocket,
                messages = messages,
                closeFuture = closeFuture,
                errorFuture = errorFuture,
            )
        }

        private fun toWebSocketUrl(baseUrl: String): String {
            val normalizedBase = when {
                baseUrl.startsWith("https://") -> "wss://${baseUrl.removePrefix("https://")}"
                baseUrl.startsWith("http://") -> "ws://${baseUrl.removePrefix("http://")}"
                else -> error("Unsupported base url: $baseUrl")
            }
            return "$normalizedBase/ws/playback-sync"
        }
    }

    private class Listener(
        private val messages: LinkedBlockingDeque<E2eWebSocketMessage>,
        private val closeFuture: CompletableFuture<E2eWebSocketClose>,
        private val errorFuture: CompletableFuture<Throwable>,
    ) : WebSocket.Listener {
        private val textBuffer = StringBuilder()

        override fun onOpen(webSocket: WebSocket) {
            webSocket.request(1)
        }

        override fun onText(
            webSocket: WebSocket,
            data: CharSequence,
            last: Boolean,
        ): CompletableFuture<*> {
            synchronized(textBuffer) {
                textBuffer.append(data)
                if (last) {
                    val raw = E2eJson.mapper.readTree(textBuffer.toString())
                    textBuffer.setLength(0)
                    messages += E2eWebSocketMessage(
                        type = PlaybackSyncMessageType.valueOf(raw.path("type").asText()),
                        payload = raw.path("payload"),
                        raw = raw,
                    )
                }
            }
            webSocket.request(1)
            return CompletableFuture.completedFuture(null)
        }

        override fun onClose(
            webSocket: WebSocket,
            statusCode: Int,
            reason: String,
        ): CompletableFuture<*> {
            closeFuture.complete(E2eWebSocketClose(statusCode = statusCode, reason = reason))
            return CompletableFuture.completedFuture(null)
        }

        override fun onError(
            webSocket: WebSocket,
            error: Throwable,
        ) {
            errorFuture.complete(error)
        }
    }
}
