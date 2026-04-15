package com.coooolfan.unirhy.sync.ws

import com.coooolfan.unirhy.service.MediaUrlSigner
import com.coooolfan.unirhy.sync.protocol.ErrorMessage
import com.coooolfan.unirhy.sync.protocol.LoadAudioSourceMessage
import com.coooolfan.unirhy.sync.protocol.PlaybackStatus
import com.coooolfan.unirhy.sync.protocol.PlaybackSyncErrorCode
import com.coooolfan.unirhy.sync.protocol.QueueChangeMessage
import com.coooolfan.unirhy.sync.protocol.ScheduledActionMessage
import com.coooolfan.unirhy.sync.protocol.SnapshotMessage
import com.coooolfan.unirhy.sync.protocol.ServerPlaybackSyncMessage
import com.coooolfan.unirhy.sync.service.CurrentQueueRecordingCatalog
import com.coooolfan.unirhy.sync.service.CurrentQueueService
import com.coooolfan.unirhy.sync.service.DeviceRuntimeService
import com.coooolfan.unirhy.sync.service.PlaybackAccountLockManager
import com.coooolfan.unirhy.sync.service.PlaybackAutoAdvanceService
import com.coooolfan.unirhy.sync.service.PlaybackPlayCoordinator
import com.coooolfan.unirhy.sync.service.PlaybackSchedulerService
import com.coooolfan.unirhy.sync.service.PlaybackSessionService
import com.coooolfan.unirhy.sync.service.PlaybackSyncMessageSender
import com.coooolfan.unirhy.sync.service.PlaybackSyncScheduledActionDispatcher
import com.coooolfan.unirhy.sync.service.PlaybackSyncSessionRemovalCoordinator
import com.coooolfan.unirhy.sync.service.ResolvedQueueRecording
import com.coooolfan.unirhy.sync.support.InMemoryCurrentQueueStateStore
import com.coooolfan.unirhy.sync.support.TestPlaybackSyncTimeProvider
import com.coooolfan.unirhy.sync.support.TestWebSocketSession
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.socket.TextMessage
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlaybackSyncWebSocketHandlerTest {
    private val objectMapper = jacksonObjectMapper()

    private lateinit var timeProvider: TestPlaybackSyncTimeProvider
    private lateinit var schedulerExecutor: ScheduledExecutorService
    private lateinit var lockManager: PlaybackAccountLockManager
    private lateinit var deviceRuntimeService: DeviceRuntimeService
    private lateinit var currentQueueService: CurrentQueueService
    private lateinit var playbackSessionService: PlaybackSessionService
    private lateinit var playbackSchedulerService: PlaybackSchedulerService
    private lateinit var handler: PlaybackSyncWebSocketHandler

    @BeforeEach
    fun setUp() {
        timeProvider = TestPlaybackSyncTimeProvider(1_730_844_000_200)
        schedulerExecutor = Executors.newSingleThreadScheduledExecutor()
        lockManager = PlaybackAccountLockManager()
        deviceRuntimeService = DeviceRuntimeService(lockManager, timeProvider)
        currentQueueService = CurrentQueueService(
            lockManager = lockManager,
            recordingCatalog = FakeCurrentQueueRecordingCatalog(),
            timeProvider = timeProvider,
            urlSigner = MediaUrlSigner("test-signing-key", 3600),
            stateStore = InMemoryCurrentQueueStateStore(),
        )
        playbackSessionService = PlaybackSessionService(
            lockManager = lockManager,
            currentQueueService = currentQueueService,
        )
        playbackSchedulerService = PlaybackSchedulerService(
            deviceRuntimeService = deviceRuntimeService,
            scheduler = schedulerExecutor,
        )
        handler = createHandler()
    }

    @AfterEach
    fun tearDown() {
        schedulerExecutor.shutdownNow()
    }

    @Test
    fun `hello sends empty snapshot`() {
        val session = connectAndHello()

        val snapshot = session.serverMessages().first() as SnapshotMessage
        assertEquals(PlaybackStatus.PAUSED, snapshot.payload.state.status)
        assertEquals(null, snapshot.payload.state.currentIndex)
        assertTrue(snapshot.payload.queue.recordingIds.isEmpty())
    }

    @Test
    fun `play switches current index and broadcasts queue load and scheduled action`() {
        val queue = currentQueueService.replaceQueue(
            accountId = 42L,
            recordingIds = listOf(1001L, 1002L),
            currentIndex = 0,
            expectedVersion = 0L,
        ).queue
        val session = connectAndHello()
        session.clearServerMessages()

        handler.handleMessage(
            session,
            TextMessage(playPayload(currentIndex = 1, version = queue.version)),
        )

        val messages = session.serverMessages()
        assertTrue(messages[0] is QueueChangeMessage)
        assertTrue(messages[1] is LoadAudioSourceMessage)
        assertTrue(messages[2] is ScheduledActionMessage)

        val loadAudio = messages[1] as LoadAudioSourceMessage
        val scheduledAction = messages[2] as ScheduledActionMessage
        assertEquals(1, loadAudio.payload.currentIndex)
        assertEquals(1002L, loadAudio.payload.recordingId)
        assertEquals(1, scheduledAction.payload.scheduledAction.currentIndex)
    }

    @Test
    fun `play with stale version returns version conflict`() {
        currentQueueService.replaceQueue(
            accountId = 42L,
            recordingIds = listOf(1001L),
            currentIndex = 0,
            expectedVersion = 0L,
        )
        val session = connectAndHello()
        session.clearServerMessages()

        handler.handleMessage(session, TextMessage(playPayload(currentIndex = 0, version = 0L)))

        val error = session.serverMessages().single() as ErrorMessage
        assertEquals(PlaybackSyncErrorCode.VERSION_CONFLICT, error.payload.code)
    }

    private fun connectAndHello(): TestWebSocketSession {
        val session = TestWebSocketSession(sessionId = "session-1", accountId = 42L)
        handler.afterConnectionEstablished(session)
        handler.handleMessage(session, TextMessage(helloPayload("web-7c2f")))
        return session
    }

    private fun createHandler(): PlaybackSyncWebSocketHandler {
        val messageSender = PlaybackSyncMessageSender(
            objectMapper = objectMapper,
            deviceRuntimeService = deviceRuntimeService,
        )
        val scheduledActionDispatcher = PlaybackSyncScheduledActionDispatcher(
            messageSender = messageSender,
            logWriter = com.coooolfan.unirhy.sync.log.PlaybackSyncLogWriter(),
        )
        val autoAdvanceService = PlaybackAutoAdvanceService(
            currentQueueService = currentQueueService,
            playbackSessionService = playbackSessionService,
            playbackSchedulerService = playbackSchedulerService,
            scheduledActionDispatcher = scheduledActionDispatcher,
            messageSender = messageSender,
            timeProvider = timeProvider,
            scheduler = schedulerExecutor,
        )
        val playCoordinator = PlaybackPlayCoordinator(
            playbackSessionService = playbackSessionService,
            deviceRuntimeService = deviceRuntimeService,
            playbackSchedulerService = playbackSchedulerService,
            scheduledActionDispatcher = scheduledActionDispatcher,
            messageSender = messageSender,
            autoAdvanceService = autoAdvanceService,
        )
        val sessionRemovalCoordinator = PlaybackSyncSessionRemovalCoordinator(
            playbackSessionService = playbackSessionService,
            playbackSchedulerService = playbackSchedulerService,
            scheduledActionDispatcher = scheduledActionDispatcher,
            messageSender = messageSender,
            autoAdvanceService = autoAdvanceService,
            logWriter = com.coooolfan.unirhy.sync.log.PlaybackSyncLogWriter(),
        )
        return PlaybackSyncWebSocketHandler(
            objectMapper = objectMapper,
            authenticator = FakePlaybackSyncAuthenticator(mapOf("valid-token" to 42L)),
            currentQueueService = currentQueueService,
            playbackSessionService = playbackSessionService,
            deviceRuntimeService = deviceRuntimeService,
            playbackSchedulerService = playbackSchedulerService,
            timeProvider = timeProvider,
            sessionRemovalCoordinator = sessionRemovalCoordinator,
            playCoordinator = playCoordinator,
            autoAdvanceService = autoAdvanceService,
            scheduledActionDispatcher = scheduledActionDispatcher,
            messageSender = messageSender,
            logWriter = com.coooolfan.unirhy.sync.log.PlaybackSyncLogWriter(),
            scheduler = schedulerExecutor,
        )
    }

    private fun helloPayload(deviceId: String): String = """
        {
          "type": "HELLO",
          "payload": {
            "deviceId": "$deviceId",
            "clientVersion": "web@0.1.0",
            "token": "valid-token"
          }
        }
    """.trimIndent()

    private fun playPayload(
        currentIndex: Int,
        version: Long,
    ): String = """
        {
          "type": "PLAY",
          "payload": {
            "commandId": "cmd-play-001",
            "deviceId": "web-7c2f",
            "currentIndex": $currentIndex,
            "positionSeconds": 12.5,
            "version": $version
          }
        }
    """.trimIndent()

    private fun TestWebSocketSession.serverMessages(): List<ServerPlaybackSyncMessage> {
        return sentTextMessages.map { objectMapper.readValue(it, ServerPlaybackSyncMessage::class.java) }
    }
}

private class FakeCurrentQueueRecordingCatalog : CurrentQueueRecordingCatalog {
    override fun getExistingRecordingIds(recordingIds: Set<Long>): Set<Long> = recordingIds

    override fun countWorks(): Int = 2

    override fun loadResolvedRecordings(recordingIds: Set<Long>): List<ResolvedQueueRecording> {
        return recordingIds.map { recordingId ->
            ResolvedQueueRecording(
                recordingId = recordingId,
                workId = recordingId,
                title = "Track $recordingId",
                artistLabel = "Artist $recordingId",
                coverMediaFileId = null,
                durationMs = 180_000,
            )
        }
    }

    override fun findFirstSimilarRecordingId(
        recordingId: Long,
        excludedWorkIds: Set<Long>,
    ): Long? = null
}

private class FakePlaybackSyncAuthenticator(
    private val tokenToAccountId: Map<String, Long>,
) : PlaybackSyncAuthenticator {
    override fun authenticate(tokenValue: String): Long? = tokenToAccountId[tokenValue]
}
