package com.coooolfan.unirhy.sync.service

import com.coooolfan.unirhy.sync.model.DeviceRuntimeState
import com.coooolfan.unirhy.sync.support.TestPlaybackSyncTimeProvider
import com.coooolfan.unirhy.sync.support.TestWebSocketSession
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import kotlin.test.assertEquals

class PlaybackSchedulerServiceTest {
    private lateinit var timeProvider: TestPlaybackSyncTimeProvider
    private lateinit var deviceRuntimeService: DeviceRuntimeService
    private lateinit var schedulerExecutor: ScheduledExecutorService
    private lateinit var schedulerService: PlaybackSchedulerService

    @BeforeEach
    fun setUp() {
        timeProvider = TestPlaybackSyncTimeProvider(1_000L)
        deviceRuntimeService = DeviceRuntimeService(
            lockManager = PlaybackAccountLockManager(),
            timeProvider = timeProvider,
        )
        schedulerExecutor = Executors.newSingleThreadScheduledExecutor()
        schedulerService = PlaybackSchedulerService(
            deviceRuntimeService = deviceRuntimeService,
            timeProvider = timeProvider,
            scheduler = schedulerExecutor,
        )
    }

    @AfterEach
    fun tearDown() {
        schedulerExecutor.shutdownNow()
    }

    @Test
    fun `schedule delay is clamped to minimum and maximum`() {
        registerAndSampleRtt(deviceId = "web-a", rttMs = 20.0)
        assertEquals(400L, schedulerService.calculateScheduleDelayMs(42L))

        registerAndSampleRtt(deviceId = "web-b", rttMs = 5_000.0, sessionId = "session-2")
        assertEquals(3_000L, schedulerService.calculateScheduleDelayMs(42L))
    }

    @Test
    fun `execute at helpers reuse the same clamped delay`() {
        registerAndSampleRtt(deviceId = "web-a", rttMs = 20.0)
        val snapshot = deviceRuntimeService.getActiveRuntimeSnapshot(42L)

        assertEquals(1_400L, schedulerService.calculateExecuteAtMs(42L, 1_000L))
        assertEquals(1_400L, schedulerService.calculateExecuteAtMs(snapshot.runtimeStates, 1_000L))
    }

    @Test
    fun `sync recovery execute at enforces playback buffer`() {
        registerAndSampleRtt(deviceId = "web-a", rttMs = 20.0)

        assertEquals(2_500L, schedulerService.calculateSyncRecoveryExecuteAtMs(42L, 1_000L))
    }

    @Test
    fun `schedule delay can be calculated from runtime states directly`() {
        val runtimeStates = listOf(
            DeviceRuntimeState(
                deviceId = "web-a",
                accountId = 42L,
                rttEmaMs = 20.0,
                lastSeenAtMs = 1_000L,
            ),
            DeviceRuntimeState(
                deviceId = "web-b",
                accountId = 42L,
                rttEmaMs = 200.0,
                lastSeenAtMs = 1_000L,
            ),
        )

        assertEquals(500L, schedulerService.calculateScheduleDelayMs(runtimeStates))
        assertEquals(1_500L, schedulerService.calculateExecuteAtMs(runtimeStates, 1_000L))
    }

    private fun registerAndSampleRtt(
        deviceId: String,
        rttMs: Double,
        sessionId: String = "session-1",
    ) {
        deviceRuntimeService.registerConnection(
            accountId = 42L,
            tokenValue = "token-$sessionId",
            sessionId = sessionId,
            session = ConcurrentWebSocketSessionDecorator(
                TestWebSocketSession(sessionId = sessionId, accountId = 42L),
                10_000,
                512 * 1024,
            ),
        )
        deviceRuntimeService.registerHello(
            accountId = 42L,
            sessionId = sessionId,
            deviceId = deviceId,
            clientVersion = "web@0.1.0",
        )
        deviceRuntimeService.recordNtpResponse(
            accountId = 42L,
            deviceId = deviceId,
            clientRttMs = rttMs,
            nowMs = timeProvider.nowMs(),
        )
    }
}
