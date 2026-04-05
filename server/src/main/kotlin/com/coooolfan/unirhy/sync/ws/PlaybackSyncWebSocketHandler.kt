package com.coooolfan.unirhy.sync.ws

import com.coooolfan.unirhy.sync.log.*
import com.coooolfan.unirhy.sync.model.toProtocolState
import com.coooolfan.unirhy.sync.protocol.*
import com.coooolfan.unirhy.sync.service.*
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

@Component
class PlaybackSyncWebSocketHandler(
    private val objectMapper: ObjectMapper,
    private val authenticator: PlaybackSyncAuthenticator,
    private val currentQueueService: CurrentQueueService,
    private val playbackSessionService: PlaybackSessionService,
    private val deviceRuntimeService: DeviceRuntimeService,
    private val playbackSyncMediaResolver: PlaybackSyncMediaResolver,
    private val playbackSchedulerService: PlaybackSchedulerService,
    private val timeProvider: PlaybackSyncTimeProvider,
    private val sessionRemovalCoordinator: PlaybackSyncSessionRemovalCoordinator,
    private val playCoordinator: PlaybackPlayCoordinator,
    private val autoAdvanceService: PlaybackAutoAdvanceService,
    private val scheduledActionDispatcher: PlaybackSyncScheduledActionDispatcher,
    private val messageSender: PlaybackSyncMessageSender,
    private val logWriter: PlaybackSyncLogWriter,
    @Qualifier("playbackSyncScheduledExecutor")
    private val scheduler: ScheduledExecutorService,
) : TextWebSocketHandler() {

    private val pendingSessions = ConcurrentHashMap<String, ConcurrentWebSocketSessionDecorator>()
    private val pendingTimeouts = ConcurrentHashMap<String, ScheduledFuture<*>>()

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val sessionId = session.getPlaybackSyncSessionId()
        val decorated = ConcurrentWebSocketSessionDecorator(
            session,
            SEND_TIME_LIMIT_MS,
            SEND_BUFFER_SIZE_LIMIT_BYTES,
        )
        pendingSessions[sessionId] = decorated
        pendingTimeouts[sessionId] = scheduler.schedule(
            { expirePendingSession(sessionId) },
            HELLO_TIMEOUT_SECONDS,
            TimeUnit.SECONDS,
        )
    }

    override fun handleTextMessage(
        session: WebSocketSession,
        message: TextMessage,
    ) {
        val sessionId = session.getPlaybackSyncSessionId()
        val context = deviceRuntimeService.findConnectionContext(sessionId)
        if (context == null) {
            handleUnauthenticatedMessage(session, sessionId, message)
            return
        }
        val nowMs = timeProvider.nowMs()

        val clientMessage = runCatching {
            objectMapper.readValue(message.payload, ClientPlaybackSyncMessage::class.java)
        }.getOrElse {
            sendProtocolError(
                context = context,
                code = PlaybackSyncErrorCode.INVALID_MESSAGE,
                message = "Invalid playback sync message",
                reason = PlaybackSyncErrorReason.INVALID_MESSAGE,
            )
            return
        }

        try {
            if (!context.helloCompleted && clientMessage.type != PlaybackSyncMessageType.HELLO) {
                throw PlaybackSyncProtocolException(
                    code = PlaybackSyncErrorCode.INVALID_MESSAGE,
                    message = "HELLO must be the first message",
                    reason = PlaybackSyncErrorReason.HELLO_REQUIRED,
                )
            }

            when (clientMessage) {
                is HelloMessage -> handleHello(context, clientMessage.payload, nowMs)
                is NtpRequestMessage -> handleNtpRequest(context, clientMessage.payload, nowMs)
                is PlayMessage -> handlePlay(context, clientMessage.payload, nowMs)
                is PauseMessage -> handlePause(context, clientMessage.payload, nowMs)
                is SeekMessage -> handleSeek(context, clientMessage.payload, nowMs)
                is AudioSourceLoadedMessage -> handleAudioSourceLoaded(context, clientMessage.payload, nowMs)
                is SyncMessage -> handleSync(context, clientMessage.payload, nowMs)
            }
        } catch (ex: PlaybackSyncProtocolException) {
            sendProtocolError(
                context = context,
                code = ex.code,
                message = ex.message,
                reason = ex.reason,
            )
        } catch (_: Exception) {
            sendProtocolError(
                context = context,
                code = PlaybackSyncErrorCode.INTERNAL_ERROR,
                message = "Failed to process playback sync message",
                reason = PlaybackSyncErrorReason.INTERNAL_ERROR,
            )
        }
    }

    override fun afterConnectionClosed(
        session: WebSocketSession,
        status: CloseStatus,
    ) {
        val sessionId = session.getPlaybackSyncSessionId()
        pendingSessions.remove(sessionId)
        cancelPendingTimeout(sessionId)
        val removal = deviceRuntimeService.removeSession(sessionId) ?: return
        sessionRemovalCoordinator.handleRemoval(
            removal = removal,
            reason = status.reason ?: "connection_closed",
            nowMs = timeProvider.nowMs(),
        )
    }

    private fun handleUnauthenticatedMessage(
        session: WebSocketSession,
        sessionId: String,
        message: TextMessage,
    ) {
        val clientMessage = runCatching {
            objectMapper.readValue(message.payload, ClientPlaybackSyncMessage::class.java)
        }.getOrElse {
            session.close(POLICY_VIOLATION_CLOSE_STATUS)
            return
        }
        if (clientMessage !is HelloMessage) {
            session.close(POLICY_VIOLATION_CLOSE_STATUS)
            return
        }
        val token = clientMessage.payload.token
        if (token.isNullOrBlank()) {
            session.close(POLICY_VIOLATION_CLOSE_STATUS)
            return
        }
        val accountId = authenticator.authenticate(token)
        if (accountId == null) {
            session.close(POLICY_VIOLATION_CLOSE_STATUS)
            return
        }
        val decoratedSession = pendingSessions.remove(sessionId)
        if (decoratedSession == null) {
            session.close(POLICY_VIOLATION_CLOSE_STATUS)
            return
        }
        cancelPendingTimeout(sessionId)
        val context = deviceRuntimeService.registerConnection(
            accountId = accountId,
            tokenValue = token,
            sessionId = sessionId,
            session = decoratedSession,
        )
        logWriter.info(
            PlaybackSyncLogEvents.CONNECTION_OPENED,
            *context.toBaseLogFields(),
            PlaybackSyncLogFields.RESULT to "accepted",
        )
        handleHello(context, clientMessage.payload, timeProvider.nowMs())
    }

    private fun handleHello(
        context: PlaybackConnectionContext,
        payload: HelloPayload,
        nowMs: Long,
    ) {
        if (context.helloCompleted) {
            throw PlaybackSyncProtocolException(
                code = PlaybackSyncErrorCode.INVALID_MESSAGE,
                message = "HELLO can only be sent once per connection",
                reason = PlaybackSyncErrorReason.HELLO_ALREADY_RECEIVED,
            )
        }

        if (payload.deviceId.isBlank()) {
            throw PlaybackSyncProtocolException(
                code = PlaybackSyncErrorCode.INVALID_MESSAGE,
                message = "deviceId must not be blank",
                reason = PlaybackSyncErrorReason.DEVICE_ID_BLANK,
            )
        }

        val registration = deviceRuntimeService.registerHello(
            accountId = context.accountId,
            sessionId = context.sessionId,
            deviceId = payload.deviceId,
            clientVersion = payload.clientVersion,
        )

        registration.replacedContext?.let(::handleReplacedConnection)

        logWriter.info(
            PlaybackSyncLogEvents.HELLO_RECEIVED,
            *registration.context.toBaseLogFields(),
            PlaybackSyncLogFields.RESULT to "accepted",
        )

        val state = playbackSessionService.getOrCreateState(registration.context.accountId)
        messageSender.sendSnapshot(
            context = registration.context,
            payload = SnapshotPayload(
                state = state.toProtocolState(),
                queue = currentQueueService.getQueue(registration.context.accountId),
                serverNowMs = nowMs,
            ),
        )

        logWriter.info(
            PlaybackSyncLogEvents.SNAPSHOT_SENT,
            *registration.context.toBaseLogFields(),
            PlaybackSyncLogFields.VERSION to state.version,
            PlaybackSyncLogFields.RESULT to "completed",
        )

        messageSender.broadcastDeviceChange(registration.context.accountId, registration.deviceIds)
    }

    private fun handleNtpRequest(
        context: PlaybackConnectionContext,
        payload: NtpRequestPayload,
        nowMs: Long,
    ) {
        requireHelloDevice(context)
        validateClientRtt(payload.clientRttMs)

        logWriter.debug(
            PlaybackSyncLogEvents.NTP_REQUEST_RECEIVED,
            *context.toBaseLogFields(),
            PlaybackSyncLogFields.RTT_MS to payload.clientRttMs,
            PlaybackSyncLogFields.RESULT to "accepted",
        )

        val responseSentAtMs = timeProvider.nowMs()
        val runtimeState = deviceRuntimeService.recordNtpResponse(
            accountId = context.accountId,
            deviceId = context.deviceId!!,
            clientRttMs = payload.clientRttMs,
            nowMs = responseSentAtMs,
        )

        messageSender.sendNtpResponse(
            context = context,
            payload = NtpResponsePayload(
                t0 = payload.t0,
                t1 = nowMs,
                t2 = responseSentAtMs,
            ),
        )

        logWriter.debug(
            PlaybackSyncLogEvents.NTP_RESPONSE_SENT,
            *context.toBaseLogFields(),
            PlaybackSyncLogFields.RTT_MS to payload.clientRttMs,
            PlaybackSyncLogFields.RTT_EMA_MS to runtimeState.rttEmaMs,
            PlaybackSyncLogFields.RESULT to "completed",
        )
    }

    private fun handlePlay(
        context: PlaybackConnectionContext,
        payload: PlaybackControlPayload,
        nowMs: Long,
    ) {
        val deviceId = requireValidControlPayload(
            context = context,
            payload = payload,
            requireRecordingContext = true,
            nowMs = nowMs,
        )
        val recordingId = requireNotNull(payload.recordingId)
        playbackSyncMediaResolver.validatePlayableRecording(recordingId)
        val queueChange = currentQueueService.syncTrackWithPlayback(
            accountId = context.accountId,
            recording = currentQueueService.resolvePlayableRecording(recordingId),
            nowMs = nowMs,
        )
        if (queueChange != null) {
            messageSender.broadcastQueueChange(context.accountId, queueChange.queue)
        }

        logControlRequest(
            event = PlaybackSyncLogEvents.PLAY_REQUEST,
            context = context,
            payload = payload,
        )

        logWriter.info(
            PlaybackSyncLogEvents.PENDING_PLAY_CREATED,
            *context.toBaseLogFields(),
            PlaybackSyncLogFields.COMMAND_ID to payload.commandId,
            PlaybackSyncLogFields.RECORDING_ID to payload.recordingId,
            PlaybackSyncLogFields.MEDIA_FILE_ID to payload.mediaFileId,
            PlaybackSyncLogFields.POSITION_SECONDS to payload.positionSeconds,
            PlaybackSyncLogFields.RESULT to "accepted",
        )
        playCoordinator.initiatePlay(
            accountId = context.accountId,
            commandId = payload.commandId,
            initiatorDeviceId = deviceId,
            recordingId = recordingId,
            positionSeconds = payload.positionSeconds,
            nowMs = nowMs,
            logDeviceId = context.deviceId,
        )
    }

    private fun handlePause(
        context: PlaybackConnectionContext,
        payload: PlaybackControlPayload,
        nowMs: Long,
    ) {
        requireValidControlPayload(
            context = context,
            payload = payload,
            requireRecordingContext = false,
            allowNullRecordingContext = true,
            nowMs = nowMs,
        )

        if (payload.recordingId != null) {
            playbackSyncMediaResolver.validatePlayableRecording(payload.recordingId)
        }

        sessionRemovalCoordinator.abandonPendingPlay(context.accountId)

        logControlRequest(
            event = PlaybackSyncLogEvents.PAUSE_REQUEST,
            context = context,
            payload = payload,
        )

        val scheduledAction = playbackSessionService.schedulePause(
            accountId = context.accountId,
            commandId = payload.commandId,
            recordingId = payload.recordingId,
            positionSeconds = payload.positionSeconds,
            nowMs = nowMs,
            executeAtMs = playbackSchedulerService.calculateExecuteAtMs(context.accountId, nowMs),
        )
        scheduledActionDispatcher.broadcastAndLog(context.accountId, context.deviceId, scheduledAction, nowMs)
        autoAdvanceService.syncFromScheduledAction(context.accountId, scheduledAction, nowMs)
    }

    private fun handleSeek(
        context: PlaybackConnectionContext,
        payload: PlaybackControlPayload,
        nowMs: Long,
    ) {
        requireValidControlPayload(
            context = context,
            payload = payload,
            requireRecordingContext = true,
            nowMs = nowMs,
        )

        val recordingId = requireNotNull(payload.recordingId)
        playbackSyncMediaResolver.validatePlayableRecording(recordingId)

        sessionRemovalCoordinator.abandonPendingPlay(context.accountId)

        logControlRequest(
            event = PlaybackSyncLogEvents.SEEK_REQUEST,
            context = context,
            payload = payload,
        )

        val scheduledAction = playbackSessionService.scheduleSeek(
            accountId = context.accountId,
            commandId = payload.commandId,
            recordingId = recordingId,
            positionSeconds = payload.positionSeconds,
            nowMs = nowMs,
            executeAtMs = playbackSchedulerService.calculateExecuteAtMs(context.accountId, nowMs),
        )
        scheduledActionDispatcher.broadcastAndLog(context.accountId, context.deviceId, scheduledAction, nowMs)
        autoAdvanceService.syncFromScheduledAction(context.accountId, scheduledAction, nowMs)
    }

    private fun handleAudioSourceLoaded(
        context: PlaybackConnectionContext,
        payload: AudioSourceLoadedPayload,
        nowMs: Long,
    ) {
        requireMatchingDevice(context, payload.deviceId)
        requireNonBlank(payload.commandId)
        deviceRuntimeService.touchDevice(context.accountId, payload.deviceId, nowMs)

        val pendingPlay = playbackSessionService.markAudioSourceLoaded(
            accountId = context.accountId,
            commandId = payload.commandId,
            deviceId = payload.deviceId,
            recordingId = payload.recordingId,
        ) ?: throw PlaybackSyncProtocolException(
            code = PlaybackSyncErrorCode.INVALID_MESSAGE,
            message = "No matching pending play found for AUDIO_SOURCE_LOADED",
            reason = PlaybackSyncErrorReason.PENDING_PLAY_NOT_FOUND,
        )

        logWriter.info(
            PlaybackSyncLogEvents.AUDIO_SOURCE_LOADED,
            *context.toBaseLogFields(),
            PlaybackSyncLogFields.COMMAND_ID to payload.commandId,
            PlaybackSyncLogFields.RECORDING_ID to payload.recordingId,
            PlaybackSyncLogFields.MEDIA_FILE_ID to payload.mediaFileId,
            PlaybackSyncLogFields.RESULT to "accepted",
        )

        playCoordinator.completePendingPlayIfReady(
            accountId = context.accountId,
            commandId = pendingPlay.commandId,
            nowMs = nowMs,
            logDeviceId = context.deviceId,
        )
    }

    private fun handleSync(
        context: PlaybackConnectionContext,
        payload: SyncPayload,
        nowMs: Long,
    ) {
        requireMatchingDevice(context, payload.deviceId)
        deviceRuntimeService.touchDevice(context.accountId, payload.deviceId, nowMs)

        if (!deviceRuntimeService.isSyncReady(context.accountId, payload.deviceId)) {
            throw PlaybackSyncProtocolException(
                code = PlaybackSyncErrorCode.SYNC_NOT_READY,
                message = "SYNC requires at least one successful NTP round-trip",
                reason = PlaybackSyncErrorReason.SYNC_NOT_READY,
            )
        }

        val state = playbackSessionService.getOrCreateState(context.accountId)
        val executeAtMs = if (state.status == PlaybackStatus.PLAYING) {
            playbackSchedulerService.calculateSyncRecoveryExecuteAtMs(context.accountId, nowMs)
        } else {
            playbackSchedulerService.calculateExecuteAtMs(context.accountId, nowMs)
        }
        val scheduledAction = playbackSessionService.buildSyncAction(
            accountId = context.accountId,
            commandId = "sync-${payload.deviceId}-$nowMs",
            nowMs = nowMs,
            executeAtMs = executeAtMs,
        )
        messageSender.sendScheduledAction(context, scheduledAction)
        logWriter.logScheduledActionSent(
            accountId = context.accountId,
            deviceId = context.deviceId,
            payload = scheduledAction,
            nowMs = nowMs,
            useDebugLevel = true,
        )
    }

    internal fun handlePendingPlayTimeout(
        accountId: Long,
        commandId: String,
    ) {
        playCoordinator.handlePendingPlayTimeout(accountId, commandId)
    }

    private fun handleReplacedConnection(replacedContext: PlaybackConnectionContext) {
        logWriter.logConnectionClosed(replacedContext, "replaced_by_new_connection")
        replacedContext.session.close(REPLACED_BY_NEW_CONNECTION_STATUS)
    }

    private fun requireValidControlPayload(
        context: PlaybackConnectionContext,
        payload: PlaybackControlPayload,
        requireRecordingContext: Boolean,
        allowNullRecordingContext: Boolean = false,
        nowMs: Long,
    ): String {
        requireMatchingDevice(context, payload.deviceId)
        requireNonBlank(payload.commandId)
        requireValidPosition(payload.positionSeconds)
        deviceRuntimeService.touchDevice(context.accountId, payload.deviceId, nowMs)

        val hasRecording = payload.recordingId != null
        val hasMediaFile = payload.mediaFileId != null
        when {
            requireRecordingContext && !hasRecording -> {
                throw PlaybackSyncProtocolException(
                    code = PlaybackSyncErrorCode.INVALID_MESSAGE,
                    message = "recordingId is required",
                    reason = PlaybackSyncErrorReason.MEDIA_CONTEXT_REQUIRED,
                )
            }

            !hasRecording && hasMediaFile -> {
                throw PlaybackSyncProtocolException(
                    code = PlaybackSyncErrorCode.INVALID_MESSAGE,
                    message = "mediaFileId requires recordingId",
                    reason = PlaybackSyncErrorReason.MEDIA_CONTEXT_MISMATCH,
                )
            }
        }
        return payload.deviceId
    }

    private fun requireMatchingDevice(
        context: PlaybackConnectionContext,
        deviceId: String,
    ) {
        requireHelloDevice(context)
        if (deviceId.isBlank()) {
            throw PlaybackSyncProtocolException(
                code = PlaybackSyncErrorCode.INVALID_MESSAGE,
                message = "deviceId must not be blank",
                reason = PlaybackSyncErrorReason.DEVICE_ID_BLANK,
            )
        }
        if (context.deviceId != deviceId) {
            throw PlaybackSyncProtocolException(
                code = PlaybackSyncErrorCode.INVALID_MESSAGE,
                message = "deviceId does not match the registered connection",
                reason = PlaybackSyncErrorReason.DEVICE_ID_MISMATCH,
            )
        }
    }

    private fun requireHelloDevice(context: PlaybackConnectionContext) {
        if (context.deviceId.isNullOrBlank()) {
            throw PlaybackSyncProtocolException(
                code = PlaybackSyncErrorCode.INVALID_MESSAGE,
                message = "HELLO must complete before this message",
                reason = PlaybackSyncErrorReason.DEVICE_NOT_REGISTERED,
            )
        }
    }

    private fun requireNonBlank(value: String) {
        if (value.isBlank()) {
            throw PlaybackSyncProtocolException(
                code = PlaybackSyncErrorCode.INVALID_MESSAGE,
                message = "commandId must not be blank",
                reason = PlaybackSyncErrorReason.COMMAND_ID_BLANK,
            )
        }
    }

    private fun requireValidPosition(positionSeconds: Double) {
        requireFiniteNonNegative(
            value = positionSeconds,
            fieldName = "positionSeconds",
            reason = PlaybackSyncErrorReason.POSITION_SECONDS_INVALID,
        )
    }

    private fun validateClientRtt(clientRttMs: Double?) {
        if (clientRttMs != null) {
            requireFiniteNonNegative(
                value = clientRttMs,
                fieldName = "clientRttMs",
                reason = PlaybackSyncErrorReason.CLIENT_RTT_INVALID,
            )
        }
    }

    private fun requireFiniteNonNegative(
        value: Double,
        fieldName: String,
        reason: PlaybackSyncErrorReason,
    ) {
        if (!value.isFinite() || value < 0) {
            throw PlaybackSyncProtocolException(
                code = PlaybackSyncErrorCode.INVALID_MESSAGE,
                message = "$fieldName must be a finite non-negative value",
                reason = reason,
            )
        }
    }

    private fun logControlRequest(
        event: String,
        context: PlaybackConnectionContext,
        payload: PlaybackControlPayload,
    ) {
        logWriter.info(
            event,
            *context.toBaseLogFields(),
            PlaybackSyncLogFields.COMMAND_ID to payload.commandId,
            PlaybackSyncLogFields.RECORDING_ID to payload.recordingId,
            PlaybackSyncLogFields.MEDIA_FILE_ID to payload.mediaFileId,
            PlaybackSyncLogFields.POSITION_SECONDS to payload.positionSeconds,
            PlaybackSyncLogFields.RESULT to "accepted",
        )
    }

    private fun sendProtocolError(
        context: PlaybackConnectionContext,
        code: PlaybackSyncErrorCode,
        message: String,
        reason: PlaybackSyncErrorReason,
    ) {
        messageSender.send(
            context = context,
            message = ErrorMessage(
                payload = ErrorPayload(
                    code = code,
                    message = message,
                ),
            ),
        )

        logWriter.info(
            PlaybackSyncLogEvents.PROTOCOL_ERROR,
            *context.toBaseLogFields(),
            PlaybackSyncLogFields.RESULT to "rejected",
            PlaybackSyncLogFields.REASON to reason.value,
        )
    }

    private fun expirePendingSession(sessionId: String) {
        val session = pendingSessions.remove(sessionId) ?: return
        pendingTimeouts.remove(sessionId)
        runCatching { session.close(POLICY_VIOLATION_CLOSE_STATUS) }
    }

    private fun cancelPendingTimeout(sessionId: String) {
        pendingTimeouts.remove(sessionId)?.cancel(false)
    }

    private fun WebSocketSession.getPlaybackSyncSessionId(): String {
        val existing = attributes[PlaybackSyncSessionAttributes.SESSION_ID] as? String
        if (!existing.isNullOrBlank()) {
            return existing
        }
        attributes[PlaybackSyncSessionAttributes.SESSION_ID] = id
        return id
    }

    private companion object {
        private const val SEND_TIME_LIMIT_MS = 10_000
        private const val SEND_BUFFER_SIZE_LIMIT_BYTES = 512 * 1024
        private const val HELLO_TIMEOUT_SECONDS = 10L
        private val POLICY_VIOLATION_CLOSE_STATUS = CloseStatus.POLICY_VIOLATION.withReason("unauthorized")
        private val REPLACED_BY_NEW_CONNECTION_STATUS =
            CloseStatus.POLICY_VIOLATION.withReason("replaced_by_new_connection")
    }
}
