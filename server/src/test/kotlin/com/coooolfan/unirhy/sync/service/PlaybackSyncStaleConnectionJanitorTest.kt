package com.coooolfan.unirhy.sync.service

import com.coooolfan.unirhy.service.MediaUrlSigner
import com.coooolfan.unirhy.sync.log.PlaybackSyncLogWriter
import com.coooolfan.unirhy.sync.protocol.PlaybackStatus
import com.coooolfan.unirhy.sync.support.TestPlaybackSyncTimeProvider
import com.coooolfan.unirhy.sync.support.TestWebSocketSession
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaybackSyncStaleConnectionJanitorTest {
    private lateinit var timeProvider: TestPlaybackSyncTimeProvider
    private lateinit var schedulerExecutor: ScheduledExecutorService
    private lateinit var deviceRuntimeService: DeviceRuntimeService
    private lateinit var playbackSessionService: PlaybackSessionService
    private lateinit var playbackSchedulerService: PlaybackSchedulerService
    private lateinit var scheduledActionDispatcher: PlaybackSyncScheduledActionDispatcher
    private lateinit var autoAdvanceService: PlaybackAutoAdvanceService
    private lateinit var coordinator: PlaybackSyncSessionRemovalCoordinator
    private lateinit var janitor: PlaybackSyncStaleConnectionJanitor
    private lateinit var staleSession: TestWebSocketSession

    @BeforeEach
    fun setUp() {
        timeProvider = TestPlaybackSyncTimeProvider(1_000L)
        schedulerExecutor = Executors.newSingleThreadScheduledExecutor()
        val lockManager = PlaybackAccountLockManager()
        deviceRuntimeService = DeviceRuntimeService(lockManager, timeProvider)
        playbackSessionService = PlaybackSessionService(lockManager, timeProvider)
        playbackSchedulerService = PlaybackSchedulerService(
            deviceRuntimeService = deviceRuntimeService,
            scheduler = schedulerExecutor,
        )
        val messageSender = PlaybackSyncMessageSender(
            objectMapper = jacksonObjectMapper(),
            deviceRuntimeService = deviceRuntimeService,
        )
        scheduledActionDispatcher = PlaybackSyncScheduledActionDispatcher(
            messageSender = messageSender,
            logWriter = PlaybackSyncLogWriter(),
        )
        autoAdvanceService = PlaybackAutoAdvanceService(
            currentQueueService = CurrentQueueService(
                lockManager = lockManager,
                recordingCatalog = FakeCatalog(),
                timeProvider = timeProvider,
                urlSigner = MediaUrlSigner("test-signing-key", 3600),
            ),
            playbackSessionService = playbackSessionService,
            playbackSchedulerService = playbackSchedulerService,
            scheduledActionDispatcher = scheduledActionDispatcher,
            messageSender = messageSender,
            timeProvider = timeProvider,
            scheduler = schedulerExecutor,
        )
        coordinator = PlaybackSyncSessionRemovalCoordinator(
            playbackSessionService = playbackSessionService,
            playbackSchedulerService = playbackSchedulerService,
            scheduledActionDispatcher = scheduledActionDispatcher,
            messageSender = messageSender,
            autoAdvanceService = autoAdvanceService,
            logWriter = PlaybackSyncLogWriter(),
        )
        janitor = PlaybackSyncStaleConnectionJanitor(
            deviceRuntimeService = deviceRuntimeService,
            playbackSchedulerService = playbackSchedulerService,
            timeProvider = timeProvider,
            sessionRemovalCoordinator = coordinator,
        )
        staleSession = registerHello("session-1", "web-a")
    }

    @AfterEach
    fun tearDown() {
        schedulerExecutor.shutdownNow()
    }

    @Test
    fun `stale sweep auto pauses committed state when last device is removed`() {
        deviceRuntimeService.recordNtpResponse(
            accountId = 42L,
            deviceId = "web-a",
            clientRttMs = 20.0,
            nowMs = 1_000L,
        )
        playbackSessionService.createPendingPlay(
            accountId = 42L,
            commandId = "cmd-play-001",
            initiatorDeviceId = "web-a",
            recordingId = 1001L,
            positionSeconds = 12.5,
            nowMs = 1_000L,
            timeoutAtMs = 4_000L,
        )
        playbackSessionService.completePendingPlay(
            accountId = 42L,
            commandId = "cmd-play-001",
            nowMs = 1_100L,
            executeAtMs = 1_500L,
        )
        playbackSessionService.createPendingPlay(
            accountId = 42L,
            commandId = "cmd-play-002",
            initiatorDeviceId = "web-a",
            recordingId = 2002L,
            positionSeconds = 3.0,
            nowMs = 2_000L,
            timeoutAtMs = 5_000L,
        )

        timeProvider.setNowMs(5_000L)
        janitor.sweepStaleConnections()

        val state = playbackSessionService.getOrCreateState(42L)
        assertEquals("stale_connection", staleSession.closeStatus?.reason)
        assertTrue(deviceRuntimeService.listDeviceIds(42L).isEmpty())
        assertEquals(PlaybackStatus.PAUSED, state.status)
        assertEquals(1001L, state.recordingId)
        assertEquals(16.0, state.positionSeconds)
        assertEquals(5_400L, state.serverTimeToExecuteMs)
        assertEquals(2L, state.version)
        assertNull(playbackSessionService.getPendingPlay(42L))
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

    private class FakeCatalog : CurrentQueueRecordingCatalog {
        override fun getExistingRecordingIds(recordingIds: Set<Long>): Set<Long> {
            return recordingIds
        }

        override fun countWorks(): Int = 0

        override fun loadResolvedRecordings(
            recordingIds: Set<Long>,
            requiredMediaFileId: Long?,
        ): List<ResolvedQueueRecording> {
            return emptyList()
        }

        override fun findFirstSimilarRecordingId(
            recordingId: Long,
            excludedWorkIds: Set<Long>,
        ): Long? = null
    }
}
