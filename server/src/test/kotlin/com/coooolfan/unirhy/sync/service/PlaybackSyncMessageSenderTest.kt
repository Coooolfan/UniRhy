package com.coooolfan.unirhy.sync.service

import com.coooolfan.unirhy.sync.protocol.ServerPlaybackSyncMessage
import com.coooolfan.unirhy.sync.support.TestPlaybackSyncTimeProvider
import com.coooolfan.unirhy.sync.support.TestWebSocketSession
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlaybackSyncMessageSenderTest {
    private lateinit var objectMapper: CountingObjectMapper
    private lateinit var deviceRuntimeService: DeviceRuntimeService
    private lateinit var messageSender: PlaybackSyncMessageSender
    private lateinit var sessionA: TestWebSocketSession
    private lateinit var sessionB: TestWebSocketSession

    @BeforeEach
    fun setUp() {
        objectMapper = CountingObjectMapper()
        deviceRuntimeService = DeviceRuntimeService(
            lockManager = PlaybackAccountLockManager(),
            timeProvider = TestPlaybackSyncTimeProvider(1_000L),
        )
        messageSender = PlaybackSyncMessageSender(
            objectMapper = objectMapper,
            deviceRuntimeService = deviceRuntimeService,
        )
        sessionA = registerHello("session-1", "web-a")
        sessionB = registerHello("session-2", "web-b")
    }

    @Test
    fun `broadcast serializes once and reaches every hello completed connection`() {
        messageSender.broadcastDeviceChange(42L, listOf("web-a", "web-b"))

        assertEquals(1, objectMapper.writeCount)
        assertEquals(1, sessionA.sentTextMessages.size)
        assertEquals(1, sessionB.sentTextMessages.size)
        assertEquals(sessionA.sentTextMessages.single(), sessionB.sentTextMessages.single())

        val message = objectMapper.decode(sessionA.sentTextMessages.single())
        assertTrue(message.payload is com.coooolfan.unirhy.sync.protocol.DeviceChangePayload)
    }

    private fun registerHello(
        sessionId: String,
        deviceId: String,
    ): TestWebSocketSession {
        val session = TestWebSocketSession(sessionId = sessionId, accountId = 42L)
        deviceRuntimeService.registerConnection(
            accountId = 42L,
            tokenValue = "token-$sessionId",
            sessionId = sessionId,
            session = ConcurrentWebSocketSessionDecorator(session, 10_000, 512 * 1024),
        )
        deviceRuntimeService.registerHello(
            accountId = 42L,
            sessionId = sessionId,
            deviceId = deviceId,
            clientVersion = "web@0.1.0",
        )
        return session
    }
}

private class CountingObjectMapper : ObjectMapper() {
    private val delegate = jacksonObjectMapper()
    var writeCount: Int = 0
        private set

    override fun writeValueAsString(value: Any?): String {
        writeCount += 1
        return delegate.writeValueAsString(value)
    }

    fun decode(payload: String): ServerPlaybackSyncMessage {
        return delegate.readValue(payload, ServerPlaybackSyncMessage::class.java)
    }
}
