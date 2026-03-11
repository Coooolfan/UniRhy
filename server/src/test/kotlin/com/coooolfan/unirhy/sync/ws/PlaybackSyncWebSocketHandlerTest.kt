package com.coooolfan.unirhy.sync.ws

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.coooolfan.unirhy.controller.MediaFileRoutes
import com.coooolfan.unirhy.sync.log.PlaybackSyncLogEvents
import com.coooolfan.unirhy.sync.log.PlaybackSyncLogWriter
import com.coooolfan.unirhy.sync.protocol.*
import com.coooolfan.unirhy.sync.service.*
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
    private lateinit var deviceRuntimeService: DeviceRuntimeService
    private lateinit var playbackSessionService: PlaybackSessionService
    private lateinit var playbackSchedulerService: PlaybackSchedulerService
    private lateinit var sessionRemovalCoordinator: PlaybackSyncSessionRemovalCoordinator
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
        val lockManager = PlaybackAccountLockManager()
        deviceRuntimeService = DeviceRuntimeService(lockManager, timeProvider)
        playbackSessionService = PlaybackSessionService(lockManager, timeProvider)
        playbackSchedulerService = PlaybackSchedulerService(
            deviceRuntimeService = deviceRuntimeService,
            scheduler = schedulerExecutor,
        )
        mediaResolver = PlaybackSyncMediaResolver(
            mediaCatalog = FakePlaybackSyncMediaCatalog(),
        )
        val messageSender = PlaybackSyncMessageSender(
            objectMapper = objectMapper,
            deviceRuntimeService = deviceRuntimeService,
        )
        scheduledActionDispatcher = PlaybackSyncScheduledActionDispatcher(
            messageSender = messageSender,
            logWriter = PlaybackSyncLogWriter(),
        )
        sessionRemovalCoordinator = PlaybackSyncSessionRemovalCoordinator(
            playbackSessionService = playbackSessionService,
            playbackSchedulerService = playbackSchedulerService,
            scheduledActionDispatcher = scheduledActionDispatcher,
            messageSender = messageSender,
            logWriter = PlaybackSyncLogWriter(),
        )
        handler = PlaybackSyncWebSocketHandler(
            objectMapper = objectMapper,
            playbackSessionService = playbackSessionService,
            deviceRuntimeService = deviceRuntimeService,
            playbackSyncMediaResolver = mediaResolver,
            playbackSchedulerService = playbackSchedulerService,
            timeProvider = timeProvider,
            sessionRemovalCoordinator = sessionRemovalCoordinator,
            scheduledActionDispatcher = scheduledActionDispatcher,
            messageSender = messageSender,
            logWriter = PlaybackSyncLogWriter(),
        )
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
        assertEquals(2, messages.size)
        val loadAudio = messages[0] as LoadAudioSourceMessage
        val scheduledAction = messages[1] as ScheduledActionMessage
        assertEquals(MediaFileRoutes.mediaFilePath(2001L), loadAudio.payload.sourceUrl)
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
    fun `pause with mixed media context is rejected`() {
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

        assertError(session.lastServerMessage(), PlaybackSyncErrorCode.INVALID_MESSAGE)
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
        assertEquals(1, sessionA.serverMessages().size)
        assertEquals(1, sessionB.serverMessages().size)

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

private class FakePlaybackSyncMediaCatalog : PlaybackSyncMediaCatalog {
    override fun recordingExists(id: Long): Boolean = id == 1001L

    override fun mediaFileExists(id: Long): Boolean = id == 2001L

    override fun recordingHasMediaFile(
        recordingId: Long,
        mediaFileId: Long,
    ): Boolean = recordingId == 1001L && mediaFileId == 2001L
}
