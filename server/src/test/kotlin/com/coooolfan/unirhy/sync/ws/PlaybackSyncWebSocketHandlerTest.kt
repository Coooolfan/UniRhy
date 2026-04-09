package com.coooolfan.unirhy.sync.ws

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.coooolfan.unirhy.service.MediaUrlSigner
import com.coooolfan.unirhy.sync.log.PlaybackSyncLogEvents
import com.coooolfan.unirhy.sync.log.PlaybackSyncLogWriter
import com.coooolfan.unirhy.sync.model.AccountPlaybackState
import com.coooolfan.unirhy.sync.protocol.*
import com.coooolfan.unirhy.sync.service.*
import com.coooolfan.unirhy.sync.support.InMemoryCurrentQueueStateStore
import com.coooolfan.unirhy.sync.support.InMemoryPlaybackResumeStateStore
import com.coooolfan.unirhy.sync.support.TestPlaybackSyncTimeProvider
import com.coooolfan.unirhy.sync.support.TestWebSocketSession
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaybackSyncWebSocketHandlerTest {
    private val objectMapper = jacksonObjectMapper()

    private lateinit var timeProvider: TestPlaybackSyncTimeProvider
    private lateinit var schedulerExecutor: ScheduledExecutorService
    private lateinit var lockManager: PlaybackAccountLockManager
    private lateinit var deviceRuntimeService: DeviceRuntimeService
    private lateinit var playbackSessionService: PlaybackSessionService
    private lateinit var playbackSchedulerService: PlaybackSchedulerService
    private lateinit var sessionRemovalCoordinator: PlaybackSyncSessionRemovalCoordinator
    private lateinit var currentQueueService: CurrentQueueService
    private lateinit var currentQueueStateStore: InMemoryCurrentQueueStateStore
    private lateinit var resumeStateStore: InMemoryPlaybackResumeStateStore
    private lateinit var autoAdvanceService: PlaybackAutoAdvanceService
    private lateinit var playCoordinator: PlaybackPlayCoordinator
    private lateinit var scheduledActionDispatcher: PlaybackSyncScheduledActionDispatcher
    private lateinit var mediaResolver: PlaybackSyncMediaResolver
    private lateinit var handler: PlaybackSyncWebSocketHandler
    private lateinit var logWriterLogger: Logger
    private lateinit var logAppender: ListAppender<ILoggingEvent>
    private var originalLogLevel: Level? = null
    private var originalAdditive = true

    @BeforeEach
    fun setUp() {
        logWriterLogger = LoggerFactory.getLogger(PlaybackSyncLogWriter::class.java) as Logger
        originalLogLevel = logWriterLogger.level
        originalAdditive = logWriterLogger.isAdditive
        logAppender = ListAppender<ILoggingEvent>().apply { start() }
        logWriterLogger.level = Level.DEBUG
        logWriterLogger.isAdditive = false
        logWriterLogger.addAppender(logAppender)

        timeProvider = TestPlaybackSyncTimeProvider(1_730_844_000_200)
        schedulerExecutor = Executors.newSingleThreadScheduledExecutor()
        lockManager = PlaybackAccountLockManager()
        currentQueueStateStore = InMemoryCurrentQueueStateStore()
        resumeStateStore = InMemoryPlaybackResumeStateStore()
        deviceRuntimeService = DeviceRuntimeService(lockManager, timeProvider)
        currentQueueService = createCurrentQueueService()
        playbackSessionService = PlaybackSessionService(
            lockManager = lockManager,
            timeProvider = timeProvider,
            currentQueueService = currentQueueService,
            resumeStateStore = resumeStateStore,
        )
        playbackSchedulerService = PlaybackSchedulerService(
            deviceRuntimeService = deviceRuntimeService,
            scheduler = schedulerExecutor,
        )
        mediaResolver = PlaybackSyncMediaResolver(
            mediaCatalog = FakePlaybackSyncMediaCatalog(),
        )
        handler = createHandler()
    }

    @AfterEach
    fun tearDown() {
        logWriterLogger.detachAppender(logAppender)
        logWriterLogger.level = originalLogLevel
        logWriterLogger.isAdditive = originalAdditive
        logAppender.stop()
        schedulerExecutor.shutdownNow()
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
        assertTrue(snapshot.payload.queue.items.isEmpty())

        val deviceChange = messages[1] as DeviceChangeMessage
        assertEquals(listOf("web-7c2f"), deviceChange.payload.devices.map { it.deviceId })
        assertEquals(listOf("web-7c2f"), deviceRuntimeService.listDeviceIds(42L))
        assertNotNull(deviceRuntimeService.findConnectionContext("session-1"))
    }

    @Test
    fun `hello restores persisted playing state as paused snapshot`() {
        resumeStateStore.upsert(
            AccountPlaybackState(
                accountId = 42L,
                status = PlaybackStatus.PLAYING,
                recordingId = 1001L,
                positionSeconds = 27.5,
                serverTimeToExecuteMs = 1_730_844_001_500,
                version = 9L,
                updatedAtMs = 1_730_844_001_200,
            ),
        )
        playbackSessionService = PlaybackSessionService(
            lockManager = lockManager,
            timeProvider = timeProvider,
            currentQueueService = currentQueueService,
            resumeStateStore = resumeStateStore,
        )
        handler = createHandler()

        val session = TestWebSocketSession(sessionId = "session-1", accountId = 42L)
        handler.afterConnectionEstablished(session)
        handler.handleMessage(session, textMessage(helloPayload(deviceId = "web-7c2f")))

        val snapshot = session.serverMessages().first() as SnapshotMessage
        assertEquals(PlaybackStatus.PAUSED, snapshot.payload.state.status)
        assertEquals(1001L, snapshot.payload.state.recordingId)
        assertEquals(27.5, snapshot.payload.state.positionSeconds)
        assertEquals(0L, snapshot.payload.state.serverTimeToExecuteMs)
        assertEquals(9L, snapshot.payload.state.version)
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
    fun `ntp request returns ntp response and marks sync ready`() {
        val session = TestWebSocketSession(sessionId = "session-1", accountId = 42L)
        handler.afterConnectionEstablished(session)
        handler.handleMessage(session, textMessage(helloPayload(deviceId = "web-7c2f")))
        session.clearServerMessages()
        clearCapturedLogs()

        handler.handleMessage(session, textMessage(ntpRequestPayload()))

        val messages = session.serverMessages()
        assertEquals(1, messages.size)
        val response = messages.single() as NtpResponseMessage
        assertEquals(1_730_844_000_000, response.payload.t0)
        assertTrue(response.payload.t1 <= response.payload.t2)
        assertTrue(deviceRuntimeService.isSyncReady(42L, "web-7c2f"))
        assertEquals(18.5, deviceRuntimeService.listActiveRuntimeStates(42L).single().rttEmaMs)
        assertEventLogLevel(PlaybackSyncLogEvents.NTP_REQUEST_RECEIVED, Level.DEBUG)
        assertEventLogLevel(PlaybackSyncLogEvents.NTP_RESPONSE_SENT, Level.DEBUG)
    }

    @Test
    fun `play on single device immediately broadcasts load and scheduled action`() {
        val session = TestWebSocketSession(sessionId = "session-1", accountId = 42L)
        handler.afterConnectionEstablished(session)
        handler.handleMessage(session, textMessage(helloPayload(deviceId = "web-7c2f")))
        session.clearServerMessages()
        clearCapturedLogs()

        handler.handleMessage(session, textMessage(playPayload()))

        val messages = session.serverMessages()
        assertEquals(3, messages.size)
        assertTrue(messages[0] is QueueChangeMessage)
        val loadAudio = messages[1] as LoadAudioSourceMessage
        val scheduledAction = messages[2] as ScheduledActionMessage
        assertEquals(1001L, loadAudio.payload.recordingId)
        assertEquals(ScheduledActionType.PLAY, scheduledAction.payload.scheduledAction.action)
        assertEquals(PlaybackStatus.PLAYING, scheduledAction.payload.scheduledAction.status)
        assertEquals(12.5, scheduledAction.payload.scheduledAction.positionSeconds)
        assertEquals(400L, scheduledAction.payload.serverTimeToExecuteMs - timeProvider.nowMs())
        assertEventLogLevel(PlaybackSyncLogEvents.PLAY_REQUEST, Level.INFO)
        assertEventLogLevel(PlaybackSyncLogEvents.PENDING_PLAY_CREATED, Level.INFO)
        assertEventLogLevel(PlaybackSyncLogEvents.SCHEDULED_ACTION_SENT, Level.INFO)
    }

    @Test
    fun `pause request keeps info logging`() {
        val session = TestWebSocketSession(sessionId = "session-1", accountId = 42L)
        handler.afterConnectionEstablished(session)
        handler.handleMessage(session, textMessage(helloPayload(deviceId = "web-7c2f")))
        session.clearServerMessages()
        clearCapturedLogs()

        handler.handleMessage(
            session,
            textMessage(
                pausePayload(commandId = "cmd-pause-001"),
            ),
        )

        val message = session.lastServerMessage()
        assertTrue(message is ScheduledActionMessage)
        assertEquals(ScheduledActionType.PAUSE, message.payload.scheduledAction.action)
        assertEventLogLevel(PlaybackSyncLogEvents.PAUSE_REQUEST, Level.INFO)
        assertEventLogLevel(PlaybackSyncLogEvents.SCHEDULED_ACTION_SENT, Level.INFO)
    }

    @Test
    fun `seek request keeps info logging`() {
        val session = TestWebSocketSession(sessionId = "session-1", accountId = 42L)
        handler.afterConnectionEstablished(session)
        handler.handleMessage(session, textMessage(helloPayload(deviceId = "web-7c2f")))
        session.clearServerMessages()
        clearCapturedLogs()

        handler.handleMessage(
            session,
            textMessage(
                seekPayload(commandId = "cmd-seek-001"),
            ),
        )

        val message = session.lastServerMessage()
        assertTrue(message is ScheduledActionMessage)
        assertEquals(ScheduledActionType.SEEK, message.payload.scheduledAction.action)
        assertEventLogLevel(PlaybackSyncLogEvents.SEEK_REQUEST, Level.INFO)
        assertEventLogLevel(PlaybackSyncLogEvents.SCHEDULED_ACTION_SENT, Level.INFO)
    }

    @Test
    fun `device id mismatch is rejected`() {
        val session = TestWebSocketSession(sessionId = "session-1", accountId = 42L)
        handler.afterConnectionEstablished(session)
        handler.handleMessage(session, textMessage(helloPayload(deviceId = "web-7c2f")))
        session.clearServerMessages()

        handler.handleMessage(
            session,
            textMessage(
                playPayload(
                    commandId = "cmd-play-002",
                    deviceId = "web-other",
                ),
            ),
        )

        assertError(session.lastServerMessage(), PlaybackSyncErrorCode.INVALID_MESSAGE)
    }

    @Test
    fun `pause without media file context is accepted`() {
        val session = TestWebSocketSession(sessionId = "session-1", accountId = 42L)
        handler.afterConnectionEstablished(session)
        handler.handleMessage(session, textMessage(helloPayload(deviceId = "web-7c2f")))
        session.clearServerMessages()

        handler.handleMessage(
            session,
            textMessage(
                pausePayload(
                    commandId = "cmd-pause-001",
                    recordingId = 1001,
                    mediaFileId = null,
                ),
            ),
        )

        val message = session.lastServerMessage()
        assertTrue(message is ScheduledActionMessage)
        assertEquals(ScheduledActionType.PAUSE, message.payload.scheduledAction.action)
        assertEquals(1001L, message.payload.scheduledAction.recordingId)
        assertEquals(36.25, message.payload.scheduledAction.positionSeconds)
    }

    @Test
    fun `sync before ntp ready is rejected`() {
        val session = TestWebSocketSession(sessionId = "session-1", accountId = 42L)
        handler.afterConnectionEstablished(session)
        handler.handleMessage(session, textMessage(helloPayload(deviceId = "web-7c2f")))
        session.clearServerMessages()

        handler.handleMessage(session, textMessage(syncPayload()))

        assertError(session.lastServerMessage(), PlaybackSyncErrorCode.SYNC_NOT_READY)
    }

    @Test
    fun `sync after ntp ready sends scheduled pause action`() {
        val session = TestWebSocketSession(sessionId = "session-1", accountId = 42L)
        handler.afterConnectionEstablished(session)
        handler.handleMessage(session, textMessage(helloPayload(deviceId = "web-7c2f")))
        handler.handleMessage(session, textMessage(ntpRequestPayload()))
        session.clearServerMessages()
        clearCapturedLogs()

        handler.handleMessage(session, textMessage(syncPayload()))

        val message = session.lastServerMessage()
        assertTrue(message is ScheduledActionMessage)
        assertEquals(ScheduledActionType.PAUSE, message.payload.scheduledAction.action)
        assertEquals(PlaybackStatus.PAUSED, message.payload.scheduledAction.status)
        assertEquals(null, message.payload.scheduledAction.recordingId)
        assertEventLogLevel(PlaybackSyncLogEvents.SCHEDULED_ACTION_SENT, Level.DEBUG)
    }

    @Test
    fun `audio source loaded by remaining device completes pending play`() {
        val sessionA = TestWebSocketSession(sessionId = "session-1", accountId = 42L)
        val sessionB = TestWebSocketSession(sessionId = "session-2", accountId = 42L)
        handler.afterConnectionEstablished(sessionA)
        handler.afterConnectionEstablished(sessionB)
        handler.handleMessage(sessionA, textMessage(helloPayload(deviceId = "web-a")))
        handler.handleMessage(sessionB, textMessage(helloPayload(deviceId = "web-b")))
        sessionA.clearServerMessages()
        sessionB.clearServerMessages()

        handler.handleMessage(
            sessionA,
            textMessage(
                playPayload(
                    commandId = "cmd-play-001",
                    deviceId = "web-a",
                ),
            ),
        )
        assertEquals(2, sessionA.serverMessages().size)
        assertEquals(2, sessionB.serverMessages().size)
        assertTrue(sessionA.serverMessages()[0] is QueueChangeMessage)
        assertTrue(sessionA.serverMessages()[1] is LoadAudioSourceMessage)
        assertTrue(sessionB.serverMessages()[0] is QueueChangeMessage)
        assertTrue(sessionB.serverMessages()[1] is LoadAudioSourceMessage)

        handler.handleMessage(
            sessionB,
            textMessage(
                audioSourceLoadedPayload(
                    deviceId = "web-b",
                ),
            ),
        )

        val scheduledActionA = sessionA.serverMessages().last() as ScheduledActionMessage
        val scheduledActionB = sessionB.serverMessages().last() as ScheduledActionMessage
        assertEquals(ScheduledActionType.PLAY, scheduledActionA.payload.scheduledAction.action)
        assertEquals(scheduledActionA.payload, scheduledActionB.payload)
        assertEquals(400L, scheduledActionA.payload.serverTimeToExecuteMs - timeProvider.nowMs())
    }

    @Test
    fun `pending play timeout sends scheduled action`() {
        val sessionA = TestWebSocketSession(sessionId = "session-1", accountId = 42L)
        val sessionB = TestWebSocketSession(sessionId = "session-2", accountId = 42L)
        handler.afterConnectionEstablished(sessionA)
        handler.afterConnectionEstablished(sessionB)
        handler.handleMessage(sessionA, textMessage(helloPayload(deviceId = "web-a")))
        handler.handleMessage(sessionB, textMessage(helloPayload(deviceId = "web-b")))
        sessionA.clearServerMessages()
        sessionB.clearServerMessages()

        handler.handleMessage(
            sessionA,
            textMessage(
                playPayload(
                    commandId = "cmd-play-timeout",
                    deviceId = "web-a",
                ),
            ),
        )
        sessionA.clearServerMessages()
        sessionB.clearServerMessages()

        timeProvider.advanceBy(3_000)
        handler.handlePendingPlayTimeout(42L, "cmd-play-timeout")

        val scheduledActionA = sessionA.lastServerMessage()
        val scheduledActionB = sessionB.lastServerMessage()
        assertTrue(scheduledActionA is ScheduledActionMessage)
        assertTrue(scheduledActionB is ScheduledActionMessage)
        assertEquals("cmd-play-timeout", scheduledActionA.payload.commandId)
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
    }

    private fun createCurrentQueueService(): CurrentQueueService {
        return CurrentQueueService(
            lockManager = lockManager,
            recordingCatalog = FakeCurrentQueueRecordingCatalog(),
            timeProvider = timeProvider,
            urlSigner = MediaUrlSigner("test-signing-key", 3600),
            stateStore = currentQueueStateStore,
        )
    }

    private fun createHandler(): PlaybackSyncWebSocketHandler {
        val messageSender = PlaybackSyncMessageSender(
            objectMapper = objectMapper,
            deviceRuntimeService = deviceRuntimeService,
        )
        scheduledActionDispatcher = PlaybackSyncScheduledActionDispatcher(
            messageSender = messageSender,
            logWriter = PlaybackSyncLogWriter(),
        )
        autoAdvanceService = PlaybackAutoAdvanceService(
            lockManager = lockManager,
            currentQueueService = currentQueueService,
            playbackSessionService = playbackSessionService,
            playbackSchedulerService = playbackSchedulerService,
            scheduledActionDispatcher = scheduledActionDispatcher,
            messageSender = messageSender,
            timeProvider = timeProvider,
            scheduler = schedulerExecutor,
        )
        playCoordinator = PlaybackPlayCoordinator(
            playbackSessionService = playbackSessionService,
            deviceRuntimeService = deviceRuntimeService,
            playbackSchedulerService = playbackSchedulerService,
            scheduledActionDispatcher = scheduledActionDispatcher,
            messageSender = messageSender,
            autoAdvanceService = autoAdvanceService,
        )
        sessionRemovalCoordinator = PlaybackSyncSessionRemovalCoordinator(
            playbackSessionService = playbackSessionService,
            playbackSchedulerService = playbackSchedulerService,
            scheduledActionDispatcher = scheduledActionDispatcher,
            messageSender = messageSender,
            autoAdvanceService = autoAdvanceService,
            logWriter = PlaybackSyncLogWriter(),
        )
        return PlaybackSyncWebSocketHandler(
            objectMapper = objectMapper,
            authenticator = FakePlaybackSyncAuthenticator(mapOf("valid-token" to 42L)),
            currentQueueService = currentQueueService,
            playbackSessionService = playbackSessionService,
            deviceRuntimeService = deviceRuntimeService,
            playbackSyncMediaResolver = mediaResolver,
            playbackSchedulerService = playbackSchedulerService,
            timeProvider = timeProvider,
            sessionRemovalCoordinator = sessionRemovalCoordinator,
            playCoordinator = playCoordinator,
            autoAdvanceService = autoAdvanceService,
            scheduledActionDispatcher = scheduledActionDispatcher,
            messageSender = messageSender,
            logWriter = PlaybackSyncLogWriter(),
            scheduler = schedulerExecutor,
        )
    }

    private fun textMessage(payload: String): TextMessage = TextMessage(payload)

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

    private fun ntpRequestPayload(): String = """
        {
          "type": "NTP_REQUEST",
          "payload": {
            "t0": 1730844000000,
            "clientRttMs": 18.5
          }
        }
    """.trimIndent()

    private fun playPayload(
        commandId: String = "cmd-play-001",
        deviceId: String = "web-7c2f",
    ): String = """
        {
          "type": "PLAY",
          "payload": {
            "commandId": "$commandId",
            "deviceId": "$deviceId",
            "recordingId": 1001,
            "mediaFileId": 2001,
            "positionSeconds": 12.5
          }
        }
    """.trimIndent()

    private fun pausePayload(
        commandId: String,
        deviceId: String = "web-7c2f",
        recordingId: Long? = null,
        mediaFileId: Long? = null,
    ): String {
        val recordingLiteral = recordingId?.toString() ?: "null"
        val mediaLiteral = mediaFileId?.toString() ?: "null"
        return """
            {
              "type": "PAUSE",
              "payload": {
                "commandId": "$commandId",
                "deviceId": "$deviceId",
                "recordingId": $recordingLiteral,
                "mediaFileId": $mediaLiteral,
                "positionSeconds": 36.25
              }
            }
        """.trimIndent()
    }

    private fun audioSourceLoadedPayload(
        deviceId: String,
        commandId: String = "cmd-play-001",
    ): String = """
        {
          "type": "AUDIO_SOURCE_LOADED",
          "payload": {
            "commandId": "$commandId",
            "deviceId": "$deviceId",
            "recordingId": 1001,
            "mediaFileId": 2001
          }
        }
    """.trimIndent()

    private fun seekPayload(
        commandId: String,
        deviceId: String = "web-7c2f",
    ): String = """
        {
          "type": "SEEK",
          "payload": {
            "commandId": "$commandId",
            "deviceId": "$deviceId",
            "recordingId": 1001,
            "mediaFileId": 2001,
            "positionSeconds": 24.75
          }
        }
    """.trimIndent()

    private fun syncPayload(deviceId: String = "web-7c2f"): String = """
        {
          "type": "SYNC",
          "payload": {
            "deviceId": "$deviceId"
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

    private fun clearCapturedLogs() {
        logAppender.list.clear()
    }

    private fun assertEventLogLevel(
        event: String,
        level: Level,
    ) {
        val logEvent = logAppender.list.single { it.formattedMessage.startsWith("event=$event") }
        assertEquals(level, logEvent.level)
    }
}

private class FakeCurrentQueueRecordingCatalog : CurrentQueueRecordingCatalog {
    override fun getExistingRecordingIds(recordingIds: Set<Long>): Set<Long> = recordingIds

    override fun countWorks(): Int = 0

    override fun loadResolvedRecordings(
        recordingIds: Set<Long>,
        requiredMediaFileId: Long?,
    ): List<ResolvedQueueRecording> {
        return recordingIds.mapNotNull { recordingId ->
            val mediaFileId = when (recordingId) {
                1001L -> 2001L
                1002L -> 2002L
                else -> null
            } ?: return@mapNotNull null
            if (requiredMediaFileId != null && requiredMediaFileId != mediaFileId) {
                return@mapNotNull null
            }
            ResolvedQueueRecording(
                recordingId = recordingId,
                mediaFileId = mediaFileId,
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

private class FakePlaybackSyncMediaCatalog : PlaybackSyncMediaCatalog {
    override fun recordingExists(id: Long): Boolean = id == 1001L

    override fun recordingHasPlayableAudio(id: Long): Boolean = id == 1001L
}

private class FakePlaybackSyncAuthenticator(
    private val tokenToAccountId: Map<String, Long>,
) : PlaybackSyncAuthenticator {
    override fun authenticate(tokenValue: String): Long? = tokenToAccountId[tokenValue]
}
