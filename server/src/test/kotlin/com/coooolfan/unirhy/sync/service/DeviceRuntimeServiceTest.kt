package com.coooolfan.unirhy.sync.service

import com.coooolfan.unirhy.sync.support.TestPlaybackSyncTimeProvider
import com.coooolfan.unirhy.sync.support.TestWebSocketSession
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeviceRuntimeServiceTest {
    private lateinit var timeProvider: TestPlaybackSyncTimeProvider
    private lateinit var service: DeviceRuntimeService

    @BeforeEach
    fun setUp() {
        timeProvider = TestPlaybackSyncTimeProvider(1_000L)
        service = DeviceRuntimeService(
            lockManager = PlaybackAccountLockManager(),
            timeProvider = timeProvider,
        )
    }

    @Test
    fun `record ntp response updates ema and sync readiness`() {
        registerHello(
            accountId = 42L,
            sessionId = "session-1",
            deviceId = "web-a",
        )

        val first = service.recordNtpResponse(
            accountId = 42L,
            deviceId = "web-a",
            clientRttMs = 20.0,
            nowMs = 1_100L,
        )
        assertEquals(20.0, first.rttEmaMs)

        val second = service.recordNtpResponse(
            accountId = 42L,
            deviceId = "web-a",
            clientRttMs = 40.0,
            nowMs = 1_200L,
        )

        assertEquals(24.0, second.rttEmaMs)
        assertTrue(service.isSyncReady(42L, "web-a"))
    }

    @Test
    fun `cleanup stale connections removes timed out runtime state`() {
        registerHello(
            accountId = 42L,
            sessionId = "session-1",
            deviceId = "web-a",
        )
        service.recordNtpResponse(
            accountId = 42L,
            deviceId = "web-a",
            clientRttMs = 20.0,
            nowMs = 1_000L,
        )

        val notStale = service.cleanupStaleConnections(
            nowMs = 4_000L,
            staleThresholdMs = 3_750L,
        )
        val stale = service.cleanupStaleConnections(
            nowMs = 5_000L,
            staleThresholdMs = 3_750L,
        )

        assertTrue(notStale.isEmpty())
        assertEquals(1, stale.size)
        assertEquals("web-a", stale.single().context.deviceId)
        assertFalse(service.isSyncReady(42L, "web-a"))
        assertTrue(service.listDeviceIds(42L).isEmpty())
    }

    @Test
    fun `active runtime snapshot returns sorted device ids with matching runtime states`() {
        registerHello(
            accountId = 42L,
            sessionId = "session-1",
            deviceId = "web-b",
        )
        registerHello(
            accountId = 42L,
            sessionId = "session-2",
            deviceId = "web-a",
        )
        service.recordNtpResponse(
            accountId = 42L,
            deviceId = "web-b",
            clientRttMs = 20.0,
            nowMs = 1_100L,
        )
        service.recordNtpResponse(
            accountId = 42L,
            deviceId = "web-a",
            clientRttMs = 40.0,
            nowMs = 1_200L,
        )

        val snapshot = service.getActiveRuntimeSnapshot(42L)

        assertEquals(listOf("web-a", "web-b"), snapshot.deviceIds)
        assertEquals(listOf("web-a", "web-b"), snapshot.runtimeStates.map { it.deviceId })
    }

    private fun registerHello(
        accountId: Long,
        sessionId: String,
        deviceId: String,
    ) {
        service.registerConnection(
            accountId = accountId,
            sessionId = sessionId,
            session = ConcurrentWebSocketSessionDecorator(
                TestWebSocketSession(sessionId = sessionId, accountId = accountId),
                10_000,
                512 * 1024,
            ),
        )
        service.registerHello(
            accountId = accountId,
            sessionId = sessionId,
            deviceId = deviceId,
            clientVersion = "web@0.1.0",
        )
    }
}
