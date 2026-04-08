package com.coooolfan.unirhy.sync.service

import com.coooolfan.unirhy.service.MediaUrlSigner
import com.coooolfan.unirhy.sync.log.PlaybackSyncLogWriter
import com.coooolfan.unirhy.sync.protocol.DeviceChangeMessage
import com.coooolfan.unirhy.sync.protocol.PlaybackStatus
import com.coooolfan.unirhy.sync.protocol.ScheduledActionMessage
import com.coooolfan.unirhy.sync.protocol.ScheduledActionType
import com.coooolfan.unirhy.sync.protocol.ServerPlaybackSyncMessage
import com.coooolfan.unirhy.sync.support.InMemoryCurrentQueueStateStore
import com.coooolfan.unirhy.sync.support.InMemoryPlaybackResumeStateStore
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaybackSyncSessionRemovalCoordinatorTest {
    private val objectMapper = jacksonObjectMapper()

    private lateinit var timeProvider: TestPlaybackSyncTimeProvider
    private lateinit var schedulerExecutor: ScheduledExecutorService
    private lateinit var deviceRuntimeService: DeviceRuntimeService
    private lateinit var playbackSessionService: PlaybackSessionService
    private lateinit var playbackSchedulerService: PlaybackSchedulerService
    private lateinit var messageSender: PlaybackSyncMessageSender
    private lateinit var scheduledActionDispatcher: PlaybackSyncScheduledActionDispatcher
    private lateinit var currentQueueService: CurrentQueueService
    private lateinit var autoAdvanceService: PlaybackAutoAdvanceService
    private lateinit var coordinator: PlaybackSyncSessionRemovalCoordinator

    @BeforeEach
    fun setUp() {
        timeProvider = TestPlaybackSyncTimeProvider(1_000L)
        schedulerExecutor = Executors.newSingleThreadScheduledExecutor()
        val lockManager = PlaybackAccountLockManager()
        deviceRuntimeService = DeviceRuntimeService(lockManager, timeProvider)
        currentQueueService = CurrentQueueService(
            lockManager = lockManager,
            recordingCatalog = FakeCatalog(),
            timeProvider = timeProvider,
            urlSigner = MediaUrlSigner("test-signing-key", 3600),
            stateStore = InMemoryCurrentQueueStateStore(),
        )
        playbackSessionService = PlaybackSessionService(
            lockManager = lockManager,
            timeProvider = timeProvider,
            currentQueueService = currentQueueService,
            resumeStateStore = InMemoryPlaybackResumeStateStore(),
        )
        playbackSchedulerService = PlaybackSchedulerService(
            deviceRuntimeService = deviceRuntimeService,
            scheduler = schedulerExecutor,
        )
        messageSender = PlaybackSyncMessageSender(objectMapper, deviceRuntimeService)
        scheduledActionDispatcher = PlaybackSyncScheduledActionDispatcher(
            messageSender = messageSender,
            logWriter = PlaybackSyncLogWriter(),
        )
        autoAdvanceService = PlaybackAutoAdvanceService(
            currentQueueService = currentQueueService,
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
    }

    @AfterEach
    fun tearDown() {
        schedulerExecutor.shutdownNow()
    }

    @Test
    fun `handle removal auto pauses playing state when no devices remain`() {
        registerHello("session-1", "web-a")
        preparePlayingState()

        val removal = requireNotNull(deviceRuntimeService.removeSession("session-1"))
        coordinator.handleRemoval(removal, "connection_closed", 2_500L)

        val state = playbackSessionService.getOrCreateState(42L)
        assertEquals(PlaybackStatus.PAUSED, state.status)
        assertEquals(1001L, state.recordingId)
        assertEquals(13.5, state.positionSeconds)
        assertEquals(2_900L, state.serverTimeToExecuteMs)
        assertEquals(2L, state.version)
        assertNull(playbackSessionService.getPendingPlay(42L))
    }

    @Test
    fun `handle removal auto pauses paused state when no devices remain`() {
        registerHello("session-1", "web-a")
        playbackSessionService.schedulePause(
            accountId = 42L,
            commandId = "cmd-pause-001",
            recordingId = 1001L,
            positionSeconds = 12.5,
            nowMs = 1_100L,
            executeAtMs = 1_500L,
        )

        val removal = requireNotNull(deviceRuntimeService.removeSession("session-1"))
        coordinator.handleRemoval(removal, "connection_closed", 2_500L)

        val state = playbackSessionService.getOrCreateState(42L)
        assertEquals(PlaybackStatus.PAUSED, state.status)
        assertEquals(1001L, state.recordingId)
        assertEquals(12.5, state.positionSeconds)
        assertEquals(2_900L, state.serverTimeToExecuteMs)
        assertEquals(2L, state.version)
    }

    @Test
    fun `handle removal clears pending play and pauses committed state when no devices remain`() {
        registerHello("session-1", "web-a")
        preparePlayingState()
        playbackSessionService.createPendingPlay(
            accountId = 42L,
            commandId = "cmd-play-002",
            initiatorDeviceId = "web-a",
            recordingId = 2002L,
            positionSeconds = 3.0,
            nowMs = 2_000L,
            timeoutAtMs = 5_000L,
        )

        val removal = requireNotNull(deviceRuntimeService.removeSession("session-1"))
        coordinator.handleRemoval(removal, "connection_closed", 2_500L)

        val state = playbackSessionService.getOrCreateState(42L)
        assertEquals(PlaybackStatus.PAUSED, state.status)
        assertEquals(1001L, state.recordingId)
        assertEquals(13.5, state.positionSeconds)
        assertEquals(2L, state.version)
        assertNull(playbackSessionService.getPendingPlay(42L))
    }

    @Test
    fun `handle removal broadcasts scheduled action when remaining devices are loaded`() {
        registerHello("session-1", "web-a")
        val sessionB = registerHello("session-2", "web-b")
        playbackSessionService.createPendingPlay(
            accountId = 42L,
            commandId = "cmd-play-001",
            initiatorDeviceId = "web-a",
            recordingId = 1001L,
            positionSeconds = 12.5,
            nowMs = 1_000L,
            timeoutAtMs = 4_000L,
        )
        playbackSessionService.markAudioSourceLoaded(
            accountId = 42L,
            commandId = "cmd-play-001",
            deviceId = "web-b",
            recordingId = 1001L,
        )

        val removal = requireNotNull(deviceRuntimeService.removeSession("session-1"))
        coordinator.handleRemoval(removal, "connection_closed", 1_200L)

        val messages = sessionB.serverMessages()
        assertEquals(2, messages.size)
        val scheduledAction = messages[0] as ScheduledActionMessage
        assertEquals(ScheduledActionType.PLAY, scheduledAction.payload.scheduledAction.action)
        assertTrue(messages[1] is DeviceChangeMessage)
        assertNull(playbackSessionService.getPendingPlay(42L))
    }

    @Test
    fun `handle removal without all devices loaded only broadcasts device change`() {
        registerHello("session-1", "web-a")
        val sessionB = registerHello("session-2", "web-b")
        playbackSessionService.createPendingPlay(
            accountId = 42L,
            commandId = "cmd-play-001",
            initiatorDeviceId = "web-a",
            recordingId = 1001L,
            positionSeconds = 12.5,
            nowMs = 1_000L,
            timeoutAtMs = 4_000L,
        )

        val removal = requireNotNull(deviceRuntimeService.removeSession("session-1"))
        coordinator.handleRemoval(removal, "connection_closed", 1_200L)

        val messages = sessionB.serverMessages()
        assertEquals(1, messages.size)
        assertTrue(messages.single() is DeviceChangeMessage)
        assertNotNull(playbackSessionService.getPendingPlay(42L))
    }

    private fun preparePlayingState() {
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

    private fun TestWebSocketSession.serverMessages(): List<ServerPlaybackSyncMessage> {
        return sentTextMessages.map { objectMapper.readValue(it, ServerPlaybackSyncMessage::class.java) }
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
