package com.coooolfan.unirhy.sync.service

import com.coooolfan.unirhy.sync.log.PlaybackSyncLogWriter
import com.coooolfan.unirhy.sync.protocol.DeviceChangeMessage
import com.coooolfan.unirhy.sync.protocol.ScheduledActionMessage
import com.coooolfan.unirhy.sync.protocol.ServerPlaybackSyncMessage
import com.coooolfan.unirhy.sync.protocol.ScheduledActionType
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
    private lateinit var coordinator: PlaybackSyncSessionRemovalCoordinator

    @BeforeEach
    fun setUp() {
        timeProvider = TestPlaybackSyncTimeProvider(1_000L)
        schedulerExecutor = Executors.newSingleThreadScheduledExecutor()
        val lockManager = PlaybackAccountLockManager()
        deviceRuntimeService = DeviceRuntimeService(lockManager, timeProvider)
        playbackSessionService = PlaybackSessionService(lockManager, timeProvider)
        playbackSchedulerService = PlaybackSchedulerService(
            deviceRuntimeService = deviceRuntimeService,
            timeProvider = timeProvider,
            scheduler = schedulerExecutor,
        )
        messageSender = PlaybackSyncMessageSender(objectMapper, deviceRuntimeService)
        coordinator = PlaybackSyncSessionRemovalCoordinator(
            playbackSessionService = playbackSessionService,
            playbackSchedulerService = playbackSchedulerService,
            messageSender = messageSender,
            logWriter = PlaybackSyncLogWriter(),
        )
    }

    @AfterEach
    fun tearDown() {
        schedulerExecutor.shutdownNow()
    }

    @Test
    fun `handle removal clears pending play when no devices remain`() {
        registerHello("session-1", "web-a")
        playbackSessionService.createPendingPlay(
            accountId = 42L,
            commandId = "cmd-play-001",
            initiatorDeviceId = "web-a",
            recordingId = 1001L,
            mediaFileId = 2001L,
            sourceUrl = "/api/media/2001",
            positionSeconds = 12.5,
            nowMs = 1_000L,
            timeoutAtMs = 4_000L,
        )

        val removal = requireNotNull(deviceRuntimeService.removeSession("session-1"))
        coordinator.handleRemoval(removal, "connection_closed", 1_200L)

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
            mediaFileId = 2001L,
            sourceUrl = "/api/media/2001",
            positionSeconds = 12.5,
            nowMs = 1_000L,
            timeoutAtMs = 4_000L,
        )
        playbackSessionService.markAudioSourceLoaded(
            accountId = 42L,
            commandId = "cmd-play-001",
            deviceId = "web-b",
            recordingId = 1001L,
            mediaFileId = 2001L,
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
            mediaFileId = 2001L,
            sourceUrl = "/api/media/2001",
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
}
