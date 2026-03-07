package com.coooolfan.unirhy.sync.support

import com.coooolfan.unirhy.sync.ws.PlaybackSyncSessionAttributes
import org.springframework.http.HttpHeaders
import org.springframework.web.socket.BinaryMessage
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.PingMessage
import org.springframework.web.socket.PongMessage
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketExtension
import org.springframework.web.socket.WebSocketMessage
import org.springframework.web.socket.WebSocketSession
import java.net.InetSocketAddress
import java.net.URI
import java.security.Principal

class TestWebSocketSession(
    private val sessionId: String,
    accountId: Long,
) : WebSocketSession {
    private val openUri = URI.create("ws://localhost/ws/playback-sync")
    private val attributes = mutableMapOf<String, Any>(
        PlaybackSyncSessionAttributes.ACCOUNT_ID to accountId,
        PlaybackSyncSessionAttributes.TOKEN_VALUE to "token-$sessionId",
        PlaybackSyncSessionAttributes.SESSION_ID to sessionId,
    )

    override fun getId(): String = sessionId

    override fun getUri(): URI = openUri

    override fun getHandshakeHeaders(): HttpHeaders = HttpHeaders()

    override fun getAttributes(): MutableMap<String, Any> = attributes

    override fun getPrincipal(): Principal? = null

    override fun getLocalAddress(): InetSocketAddress? = null

    override fun getRemoteAddress(): InetSocketAddress? = null

    override fun getAcceptedProtocol(): String? = null

    override fun setTextMessageSizeLimit(messageSizeLimit: Int) {
    }

    override fun getTextMessageSizeLimit(): Int = 64 * 1024

    override fun setBinaryMessageSizeLimit(messageSizeLimit: Int) {
    }

    override fun getBinaryMessageSizeLimit(): Int = 64 * 1024

    override fun getExtensions(): MutableList<WebSocketExtension> = mutableListOf()

    override fun isOpen(): Boolean = open

    override fun sendMessage(message: WebSocketMessage<*>) {
        when (message) {
            is TextMessage -> sentTextMessages += message.payload
            is BinaryMessage -> error("BinaryMessage is not expected in playback sync tests")
            is PingMessage -> error("PingMessage is not expected in playback sync tests")
            is PongMessage -> error("PongMessage is not expected in playback sync tests")
            else -> error("Unsupported WebSocketMessage type: ${message.javaClass.name}")
        }
    }

    override fun close() {
        close(CloseStatus.NORMAL)
    }

    override fun close(status: CloseStatus) {
        open = false
        closeStatus = status
    }

    fun clearServerMessages() {
        sentTextMessages.clear()
    }

    var open: Boolean = true
        private set
    var closeStatus: CloseStatus? = null
        private set
    val sentTextMessages: MutableList<String> = mutableListOf()
}
