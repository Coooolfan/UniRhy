package com.coooolfan.unirhy.sync.ws

import com.coooolfan.unirhy.sync.log.PlaybackSyncLogWriter
import com.coooolfan.unirhy.sync.protocol.DeviceChangeMessage
import com.coooolfan.unirhy.sync.protocol.ErrorMessage
import com.coooolfan.unirhy.sync.protocol.PlaybackSyncErrorCode
import com.coooolfan.unirhy.sync.protocol.PlaybackSyncMessageType
import com.coooolfan.unirhy.sync.protocol.ServerPlaybackSyncMessage
import com.coooolfan.unirhy.sync.protocol.SnapshotMessage
import com.coooolfan.unirhy.sync.service.DeviceRuntimeService
import com.coooolfan.unirhy.sync.service.PlaybackAccountLockManager
import com.coooolfan.unirhy.sync.service.PlaybackSessionService
import com.coooolfan.unirhy.sync.service.PlaybackSyncMessageSender
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaybackSyncWebSocketHandlerTest {
    private val objectMapper = jacksonObjectMapper()

    private lateinit var deviceRuntimeService: DeviceRuntimeService
    private lateinit var handler: PlaybackSyncWebSocketHandler

    @BeforeEach
    fun setUp() {
        val lockManager = PlaybackAccountLockManager()
        deviceRuntimeService = DeviceRuntimeService(lockManager)
        val messageSender = PlaybackSyncMessageSender(
            objectMapper = objectMapper,
            deviceRuntimeService = deviceRuntimeService,
        )
        handler = PlaybackSyncWebSocketHandler(
            objectMapper = objectMapper,
            playbackSessionService = PlaybackSessionService(lockManager),
            deviceRuntimeService = deviceRuntimeService,
            messageSender = messageSender,
            logWriter = PlaybackSyncLogWriter(),
        )
    }

    @Test
    fun `hello registers device and sends snapshot then device change`() {
        val session = TestWebSocketSession(sessionId = "session-1", accountId = 42L)
        handler.afterConnectionEstablished(session)

        handler.handleMessage(session, textMessage(helloPayload(deviceId = "web-7c2f")))

        val messages = session.serverMessages()
        assertEquals(2, messages.size)
        assertTrue(messages[0] is SnapshotMessage)
        assertTrue(messages[1] is DeviceChangeMessage)

        val snapshot = messages[0] as SnapshotMessage
        assertEquals(0L, snapshot.payload.state.version)
        assertEquals(0.0, snapshot.payload.state.positionSeconds)
        assertEquals(null, snapshot.payload.state.recordingId)

        val deviceChange = messages[1] as DeviceChangeMessage
        assertEquals(listOf("web-7c2f"), deviceChange.payload.devices.map { it.deviceId })
        assertEquals(listOf("web-7c2f"), deviceRuntimeService.listDeviceIds(42L))
        assertNotNull(deviceRuntimeService.findConnectionContext("session-1"))
    }

    @Test
    fun `duplicate device id replaces old connection`() {
        val oldSession = TestWebSocketSession(sessionId = "session-1", accountId = 42L)
        val newSession = TestWebSocketSession(sessionId = "session-2", accountId = 42L)
        handler.afterConnectionEstablished(oldSession)
        handler.afterConnectionEstablished(newSession)

        handler.handleMessage(oldSession, textMessage(helloPayload(deviceId = "web-7c2f")))
        handler.handleMessage(newSession, textMessage(helloPayload(deviceId = "web-7c2f")))

        assertFalse(oldSession.isOpen)
        assertEquals("replaced_by_new_connection", oldSession.closeStatus?.reason)
        assertNull(deviceRuntimeService.findConnectionContext("session-1"))
        assertNotNull(deviceRuntimeService.findConnectionContext("session-2"))
        assertEquals(listOf("web-7c2f"), deviceRuntimeService.listDeviceIds(42L))
    }

    @Test
    fun `protocol errors do not pollute state`() {
        val session = TestWebSocketSession(sessionId = "session-1", accountId = 42L)
        handler.afterConnectionEstablished(session)

        handler.handleMessage(session, textMessage("""{"type":"HELLO","payload":}"""))
        assertError(session.lastServerMessage(), PlaybackSyncErrorCode.INVALID_MESSAGE)
        assertEquals(emptyList(), deviceRuntimeService.listDeviceIds(42L))

        handler.handleMessage(session, textMessage(ntpRequestPayload()))
        assertError(session.lastServerMessage(), PlaybackSyncErrorCode.INVALID_MESSAGE)
        assertEquals(emptyList(), deviceRuntimeService.listDeviceIds(42L))

        handler.handleMessage(session, textMessage(helloPayload(deviceId = "web-7c2f")))
        assertEquals(listOf("web-7c2f"), deviceRuntimeService.listDeviceIds(42L))

        handler.handleMessage(session, textMessage(helloPayload(deviceId = "web-7c2f")))
        assertError(session.lastServerMessage(), PlaybackSyncErrorCode.INVALID_MESSAGE)
        assertEquals(listOf("web-7c2f"), deviceRuntimeService.listDeviceIds(42L))

        handler.handleMessage(session, textMessage(playPayload()))
        assertError(session.lastServerMessage(), PlaybackSyncErrorCode.UNSUPPORTED_MESSAGE)
        assertEquals(listOf("web-7c2f"), deviceRuntimeService.listDeviceIds(42L))
    }

    @Test
    fun `closing registered session removes device and broadcasts updated device list`() {
        val sessionA = TestWebSocketSession(sessionId = "session-1", accountId = 42L)
        val sessionB = TestWebSocketSession(sessionId = "session-2", accountId = 42L)
        handler.afterConnectionEstablished(sessionA)
        handler.afterConnectionEstablished(sessionB)

        handler.handleMessage(sessionA, textMessage(helloPayload(deviceId = "web-a")))
        handler.handleMessage(sessionB, textMessage(helloPayload(deviceId = "web-b")))
        sessionA.clearServerMessages()
        sessionB.clearServerMessages()

        handler.afterConnectionClosed(sessionB, CloseStatus.NORMAL)

        assertEquals(listOf("web-a"), deviceRuntimeService.listDeviceIds(42L))
        assertNull(deviceRuntimeService.findConnectionContext("session-2"))
        val remainingMessage = sessionA.lastServerMessage()
        assertTrue(remainingMessage is DeviceChangeMessage)
        assertEquals(listOf("web-a"), remainingMessage.payload.devices.map { it.deviceId })
        assertTrue(sessionB.serverMessages().isEmpty())
    }

    private fun textMessage(payload: String): TextMessage = TextMessage(payload)

    private fun helloPayload(deviceId: String): String = """
        {
          "type": "HELLO",
          "payload": {
            "deviceId": "$deviceId",
            "clientVersion": "web@0.1.0"
          }
        }
    """.trimIndent()

    private fun ntpRequestPayload(): String = """
        {
          "type": "NTP_REQUEST",
          "payload": {
            "t0": 1730844000000
          }
        }
    """.trimIndent()

    private fun playPayload(): String = """
        {
          "type": "PLAY",
          "payload": {
            "commandId": "cmd-play-001",
            "deviceId": "web-7c2f",
            "recordingId": 1001,
            "mediaFileId": 2001,
            "positionSeconds": 12.5
          }
        }
    """.trimIndent()

    private fun TestWebSocketSession.serverMessages(): List<ServerPlaybackSyncMessage> {
        return sentTextMessages.map { objectMapper.readValue(it, ServerPlaybackSyncMessage::class.java) }
    }

    private fun TestWebSocketSession.lastServerMessage(): ServerPlaybackSyncMessage = serverMessages().last()

    private fun assertError(
        message: ServerPlaybackSyncMessage,
        code: PlaybackSyncErrorCode,
    ) {
        assertTrue(message is ErrorMessage)
        assertEquals(PlaybackSyncMessageType.ERROR, message.type)
        assertEquals(code, message.payload.code)
    }
}

private class TestWebSocketSession(
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

    var open: Boolean = true
        private set
    var closeStatus: CloseStatus? = null
        private set
    val sentTextMessages: MutableList<String> = mutableListOf()

    fun clearServerMessages() {
        sentTextMessages.clear()
    }
}
