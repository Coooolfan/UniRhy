package com.coooolfan.unirhy.sync.ws

import com.coooolfan.unirhy.sync.log.PlaybackSyncLogEvents
import com.coooolfan.unirhy.sync.log.PlaybackSyncLogFields
import com.coooolfan.unirhy.sync.log.PlaybackSyncLogWriter
import com.coooolfan.unirhy.sync.log.logConnectionClosed
import com.coooolfan.unirhy.sync.log.logScheduledActionSent
import com.coooolfan.unirhy.sync.model.toProtocolState
import com.coooolfan.unirhy.sync.protocol.*
import com.coooolfan.unirhy.sync.service.*
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator
import org.springframework.web.socket.handler.TextWebSocketHandler

@Component
class PlaybackSyncWebSocketHandler(
    private val objectMapper: ObjectMapper,
    private val playbackSessionService: PlaybackSessionService,
    private val deviceRuntimeService: DeviceRuntimeService,
    private val playbackSyncMediaResolver: PlaybackSyncMediaResolver,
    private val playbackSchedulerService: PlaybackSchedulerService,
    private val sessionRemovalCoordinator: PlaybackSyncSessionRemovalCoordinator,
    private val messageSender: PlaybackSyncMessageSender,
    private val logWriter: PlaybackSyncLogWriter,
) : TextWebSocketHandler() {
    override fun afterConnectionEstablished(session: WebSocketSession) {
        val accountId = session.getAccountId()
        val tokenValue = session.attributes[PlaybackSyncSessionAttributes.TOKEN_VALUE] as? String
        val sessionId = session.getPlaybackSyncSessionId()

        if (accountId == null || tokenValue.isNullOrBlank()) {
            session.close(POLICY_VIOLATION_CLOSE_STATUS)
            return
        }

        val decoratedSession = ConcurrentWebSocketSessionDecorator(
            session,
            SEND_TIME_LIMIT_MS,
            SEND_BUFFER_SIZE_LIMIT_BYTES,
        )

        deviceRuntimeService.registerConnection(
            accountId = accountId,
            tokenValue = tokenValue,
            sessionId = sessionId,
            session = decoratedSession,
        )

        logWriter.info(
            event = PlaybackSyncLogEvents.CONNECTION_OPENED,
            PlaybackSyncLogFields.ACCOUNT_ID to accountId,
            PlaybackSyncLogFields.SESSION_ID to sessionId,
            PlaybackSyncLogFields.RESULT to "accepted",
        )
    }

    override fun handleTextMessage(
        session: WebSocketSession,
        message: TextMessage,
    ) {
        val sessionId = session.getPlaybackSyncSessionId()
        val context = deviceRuntimeService.findConnectionContext(sessionId) ?: return
        val nowMs = playbackSchedulerService.nowMs()

        val clientMessage = runCatching {
            objectMapper.readValue(message.payload, ClientPlaybackSyncMessage::class.java)
        }.getOrElse {
            sendProtocolError(
                context = context,
                code = PlaybackSyncErrorCode.INVALID_MESSAGE,
                message = "Invalid playback sync message",
                reason = "invalid_message",
            )
            return
        }

        try {
            if (!context.helloCompleted && clientMessage.type != PlaybackSyncMessageType.HELLO) {
                throw PlaybackSyncProtocolException(
                    code = PlaybackSyncErrorCode.INVALID_MESSAGE,
                    message = "HELLO must be the first message",
                    reason = "hello_required",
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
                reason = "internal_error",
            )
        }
    }

    override fun afterConnectionClosed(
        session: WebSocketSession,
        status: CloseStatus,
    ) {
        val sessionId = session.getPlaybackSyncSessionId()
        val removal = deviceRuntimeService.removeSession(sessionId) ?: return
        sessionRemovalCoordinator.handleRemoval(
            removal = removal,
            reason = status.reason ?: "connection_closed",
            nowMs = playbackSchedulerService.nowMs(),
        )
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
                reason = "hello_already_received",
            )
        }

        if (payload.deviceId.isBlank()) {
            throw PlaybackSyncProtocolException(
                code = PlaybackSyncErrorCode.INVALID_MESSAGE,
                message = "deviceId must not be blank",
                reason = "device_id_blank",
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
            event = PlaybackSyncLogEvents.HELLO_RECEIVED,
            PlaybackSyncLogFields.ACCOUNT_ID to registration.context.accountId,
            PlaybackSyncLogFields.DEVICE_ID to registration.context.deviceId,
            PlaybackSyncLogFields.SESSION_ID to registration.context.sessionId,
            PlaybackSyncLogFields.RESULT to "accepted",
        )

        val state = playbackSessionService.getOrCreateState(registration.context.accountId)
        messageSender.sendSnapshot(
            context = registration.context,
            payload = SnapshotPayload(
                state = state.toProtocolState(),
                serverNowMs = nowMs,
            ),
        )

        logWriter.info(
            event = PlaybackSyncLogEvents.SNAPSHOT_SENT,
            PlaybackSyncLogFields.ACCOUNT_ID to registration.context.accountId,
            PlaybackSyncLogFields.DEVICE_ID to registration.context.deviceId,
            PlaybackSyncLogFields.SESSION_ID to registration.context.sessionId,
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
        deviceRuntimeService.touchDevice(context.accountId, context.deviceId!!, nowMs)

        logWriter.info(
            event = PlaybackSyncLogEvents.NTP_REQUEST_RECEIVED,
            PlaybackSyncLogFields.ACCOUNT_ID to context.accountId,
            PlaybackSyncLogFields.DEVICE_ID to context.deviceId,
            PlaybackSyncLogFields.SESSION_ID to context.sessionId,
            PlaybackSyncLogFields.RTT_MS to payload.clientRttMs,
            PlaybackSyncLogFields.RESULT to "accepted",
        )

        val responseSentAtMs = playbackSchedulerService.nowMs()
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

        logWriter.info(
            event = PlaybackSyncLogEvents.NTP_RESPONSE_SENT,
            PlaybackSyncLogFields.ACCOUNT_ID to context.accountId,
            PlaybackSyncLogFields.DEVICE_ID to context.deviceId,
            PlaybackSyncLogFields.SESSION_ID to context.sessionId,
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
            requireMediaContext = true,
            nowMs = nowMs,
        )
        val resolvedMedia = playbackSyncMediaResolver.resolve(
            recordingId = requireNotNull(payload.recordingId),
            mediaFileId = requireNotNull(payload.mediaFileId),
        )

        logControlRequest(
            event = PlaybackSyncLogEvents.PLAY_REQUEST,
            context = context,
            payload = payload,
        )

        val pendingPlayResult = playbackSessionService.createPendingPlay(
            accountId = context.accountId,
            commandId = payload.commandId,
            initiatorDeviceId = deviceId,
            recordingId = resolvedMedia.recordingId,
            mediaFileId = resolvedMedia.mediaFileId,
            sourceUrl = resolvedMedia.sourceUrl,
            positionSeconds = payload.positionSeconds,
            nowMs = nowMs,
            timeoutAtMs = nowMs + PlaybackSchedulerService.PENDING_PLAY_TIMEOUT_MS,
        )

        logWriter.info(
            event = if (pendingPlayResult.replaced) {
                PlaybackSyncLogEvents.PENDING_PLAY_REPLACED
            } else {
                PlaybackSyncLogEvents.PENDING_PLAY_CREATED
            },
            PlaybackSyncLogFields.ACCOUNT_ID to context.accountId,
            PlaybackSyncLogFields.DEVICE_ID to context.deviceId,
            PlaybackSyncLogFields.SESSION_ID to context.sessionId,
            PlaybackSyncLogFields.COMMAND_ID to payload.commandId,
            PlaybackSyncLogFields.RECORDING_ID to payload.recordingId,
            PlaybackSyncLogFields.MEDIA_FILE_ID to payload.mediaFileId,
            PlaybackSyncLogFields.POSITION_SECONDS to payload.positionSeconds,
            PlaybackSyncLogFields.RESULT to "accepted",
        )

        messageSender.broadcastLoadAudioSource(
            accountId = context.accountId,
            payload = LoadAudioSourcePayload(
                commandId = payload.commandId,
                recordingId = resolvedMedia.recordingId,
                mediaFileId = resolvedMedia.mediaFileId,
                sourceUrl = resolvedMedia.sourceUrl,
            ),
        )

        val scheduledAction = maybeCompletePendingPlay(context.accountId, nowMs)
        if (scheduledAction != null) {
            playbackSchedulerService.cancelPendingPlayTimeout(context.accountId)
            messageSender.broadcastScheduledAction(context.accountId, scheduledAction)
            logWriter.logScheduledActionSent(context.accountId, context.deviceId, scheduledAction, nowMs)
            return
        }

        playbackSchedulerService.schedulePendingPlayTimeout(context.accountId, payload.commandId) {
            handlePendingPlayTimeout(context.accountId, payload.commandId)
        }
    }

    private fun handlePause(
        context: PlaybackConnectionContext,
        payload: PlaybackControlPayload,
        nowMs: Long,
    ) {
        requireValidControlPayload(
            context = context,
            payload = payload,
            requireMediaContext = false,
            allowNullMediaContext = true,
            nowMs = nowMs,
        )

        val sourceUrl = if (payload.recordingId != null && payload.mediaFileId != null) {
            playbackSyncMediaResolver.resolve(
                recordingId = payload.recordingId,
                mediaFileId = payload.mediaFileId,
            ).sourceUrl
        } else {
            null
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
            mediaFileId = payload.mediaFileId,
            sourceUrl = sourceUrl,
            positionSeconds = payload.positionSeconds,
            nowMs = nowMs,
            executeAtMs = playbackSchedulerService.calculateExecuteAtMs(context.accountId, nowMs),
        )
        messageSender.broadcastScheduledAction(context.accountId, scheduledAction)
        logWriter.logScheduledActionSent(context.accountId, context.deviceId, scheduledAction, nowMs)
    }

    private fun handleSeek(
        context: PlaybackConnectionContext,
        payload: PlaybackControlPayload,
        nowMs: Long,
    ) {
        requireValidControlPayload(
            context = context,
            payload = payload,
            requireMediaContext = true,
            nowMs = nowMs,
        )

        val resolvedMedia = playbackSyncMediaResolver.resolve(
            recordingId = requireNotNull(payload.recordingId),
            mediaFileId = requireNotNull(payload.mediaFileId),
        )

        sessionRemovalCoordinator.abandonPendingPlay(context.accountId)

        logControlRequest(
            event = PlaybackSyncLogEvents.SEEK_REQUEST,
            context = context,
            payload = payload,
        )

        val scheduledAction = playbackSessionService.scheduleSeek(
            accountId = context.accountId,
            commandId = payload.commandId,
            recordingId = resolvedMedia.recordingId,
            mediaFileId = resolvedMedia.mediaFileId,
            sourceUrl = resolvedMedia.sourceUrl,
            positionSeconds = payload.positionSeconds,
            nowMs = nowMs,
            executeAtMs = playbackSchedulerService.calculateExecuteAtMs(context.accountId, nowMs),
        )
        messageSender.broadcastScheduledAction(context.accountId, scheduledAction)
        logWriter.logScheduledActionSent(context.accountId, context.deviceId, scheduledAction, nowMs)
    }

    private fun handleAudioSourceLoaded(
        context: PlaybackConnectionContext,
        payload: AudioSourceLoadedPayload,
        nowMs: Long,
    ) {
        requireMatchingDevice(context, payload.deviceId)
        requireNonBlank(payload.commandId, "commandId must not be blank", "command_id_blank")
        deviceRuntimeService.touchDevice(context.accountId, payload.deviceId, nowMs)

        val pendingPlay = playbackSessionService.markAudioSourceLoaded(
            accountId = context.accountId,
            commandId = payload.commandId,
            deviceId = payload.deviceId,
            recordingId = payload.recordingId,
            mediaFileId = payload.mediaFileId,
        ) ?: throw PlaybackSyncProtocolException(
            code = PlaybackSyncErrorCode.INVALID_MESSAGE,
            message = "No matching pending play found for AUDIO_SOURCE_LOADED",
            reason = "pending_play_not_found",
        )

        logWriter.info(
            event = PlaybackSyncLogEvents.AUDIO_SOURCE_LOADED,
            PlaybackSyncLogFields.ACCOUNT_ID to context.accountId,
            PlaybackSyncLogFields.DEVICE_ID to context.deviceId,
            PlaybackSyncLogFields.SESSION_ID to context.sessionId,
            PlaybackSyncLogFields.COMMAND_ID to payload.commandId,
            PlaybackSyncLogFields.RECORDING_ID to payload.recordingId,
            PlaybackSyncLogFields.MEDIA_FILE_ID to payload.mediaFileId,
            PlaybackSyncLogFields.RESULT to "accepted",
        )

        val runtimeSnapshot = deviceRuntimeService.getActiveRuntimeSnapshot(context.accountId)
        if (!playbackSessionService.areAllDevicesLoaded(context.accountId, runtimeSnapshot.deviceIds)) {
            return
        }

        val scheduledAction = playbackSessionService.completePendingPlay(
            accountId = context.accountId,
            commandId = pendingPlay.commandId,
            nowMs = nowMs,
            executeAtMs = playbackSchedulerService.calculateExecuteAtMs(runtimeSnapshot.runtimeStates, nowMs),
        ) ?: return

        playbackSchedulerService.cancelPendingPlayTimeout(context.accountId)
        messageSender.broadcastScheduledAction(context.accountId, scheduledAction)
        logWriter.logScheduledActionSent(context.accountId, context.deviceId, scheduledAction, nowMs)
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
                reason = "sync_not_ready",
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
        logWriter.logScheduledActionSent(context.accountId, context.deviceId, scheduledAction, nowMs)
    }

    internal fun handlePendingPlayTimeout(
        accountId: Long,
        commandId: String,
    ) {
        val nowMs = playbackSchedulerService.nowMs()
        val scheduledAction = playbackSessionService.completePendingPlay(
            accountId = accountId,
            commandId = commandId,
            nowMs = nowMs,
            executeAtMs = playbackSchedulerService.calculateExecuteAtMs(accountId, nowMs),
        ) ?: return

        messageSender.broadcastScheduledAction(accountId, scheduledAction)
        logWriter.logScheduledActionSent(accountId, null, scheduledAction, nowMs)
    }

    private fun maybeCompletePendingPlay(
        accountId: Long,
        nowMs: Long,
    ): ScheduledActionPayload? {
        val pendingPlay = playbackSessionService.getPendingPlay(accountId) ?: return null
        val runtimeSnapshot = deviceRuntimeService.getActiveRuntimeSnapshot(accountId)
        if (!playbackSessionService.areAllDevicesLoaded(accountId, runtimeSnapshot.deviceIds)) {
            return null
        }
        return playbackSessionService.completePendingPlay(
            accountId = accountId,
            commandId = pendingPlay.commandId,
            nowMs = nowMs,
            executeAtMs = playbackSchedulerService.calculateExecuteAtMs(runtimeSnapshot.runtimeStates, nowMs),
        )
    }

    private fun handleReplacedConnection(replacedContext: PlaybackConnectionContext) {
        logWriter.logConnectionClosed(replacedContext, "replaced_by_new_connection")
        replacedContext.session.close(REPLACED_BY_NEW_CONNECTION_STATUS)
    }

    private fun requireValidControlPayload(
        context: PlaybackConnectionContext,
        payload: PlaybackControlPayload,
        requireMediaContext: Boolean,
        allowNullMediaContext: Boolean = false,
        nowMs: Long,
    ): String {
        requireMatchingDevice(context, payload.deviceId)
        requireNonBlank(payload.commandId, "commandId must not be blank", "command_id_blank")
        requireValidPosition(payload.positionSeconds)
        deviceRuntimeService.touchDevice(context.accountId, payload.deviceId, nowMs)

        val hasRecording = payload.recordingId != null
        val hasMediaFile = payload.mediaFileId != null
        when {
            requireMediaContext && (!hasRecording || !hasMediaFile) -> {
                throw PlaybackSyncProtocolException(
                    code = PlaybackSyncErrorCode.INVALID_MESSAGE,
                    message = "recordingId and mediaFileId are required",
                    reason = "media_context_required",
                )
            }

            allowNullMediaContext && hasRecording != hasMediaFile -> {
                throw PlaybackSyncProtocolException(
                    code = PlaybackSyncErrorCode.INVALID_MESSAGE,
                    message = "recordingId and mediaFileId must both be present or both be null",
                    reason = "media_context_mismatch",
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
                reason = "device_id_blank",
            )
        }
        if (context.deviceId != deviceId) {
            throw PlaybackSyncProtocolException(
                code = PlaybackSyncErrorCode.INVALID_MESSAGE,
                message = "deviceId does not match the registered connection",
                reason = "device_id_mismatch",
            )
        }
    }

    private fun requireHelloDevice(context: PlaybackConnectionContext) {
        if (context.deviceId.isNullOrBlank()) {
            throw PlaybackSyncProtocolException(
                code = PlaybackSyncErrorCode.INVALID_MESSAGE,
                message = "HELLO must complete before this message",
                reason = "device_not_registered",
            )
        }
    }

    private fun requireNonBlank(
        value: String,
        message: String,
        reason: String,
    ) {
        if (value.isBlank()) {
            throw PlaybackSyncProtocolException(
                code = PlaybackSyncErrorCode.INVALID_MESSAGE,
                message = message,
                reason = reason,
            )
        }
    }

    private fun requireValidPosition(positionSeconds: Double) {
        if (!positionSeconds.isFinite() || positionSeconds < 0) {
            throw PlaybackSyncProtocolException(
                code = PlaybackSyncErrorCode.INVALID_MESSAGE,
                message = "positionSeconds must be a finite non-negative value",
                reason = "position_seconds_invalid",
            )
        }
    }

    private fun validateClientRtt(clientRttMs: Double?) {
        if (clientRttMs != null && (!clientRttMs.isFinite() || clientRttMs < 0)) {
            throw PlaybackSyncProtocolException(
                code = PlaybackSyncErrorCode.INVALID_MESSAGE,
                message = "clientRttMs must be a finite non-negative value",
                reason = "client_rtt_invalid",
            )
        }
    }

    private fun logControlRequest(
        event: String,
        context: PlaybackConnectionContext,
        payload: PlaybackControlPayload,
    ) {
        logWriter.info(
            event = event,
            PlaybackSyncLogFields.ACCOUNT_ID to context.accountId,
            PlaybackSyncLogFields.DEVICE_ID to context.deviceId,
            PlaybackSyncLogFields.SESSION_ID to context.sessionId,
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
        reason: String,
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
            event = PlaybackSyncLogEvents.PROTOCOL_ERROR,
            PlaybackSyncLogFields.ACCOUNT_ID to context.accountId,
            PlaybackSyncLogFields.DEVICE_ID to context.deviceId,
            PlaybackSyncLogFields.SESSION_ID to context.sessionId,
            PlaybackSyncLogFields.RESULT to "rejected",
            PlaybackSyncLogFields.REASON to reason,
        )
    }

    private fun WebSocketSession.getAccountId(): Long? {
        return when (val value = attributes[PlaybackSyncSessionAttributes.ACCOUNT_ID]) {
            is Long -> value
            is Int -> value.toLong()
            is Number -> value.toLong()
            is String -> value.toLongOrNull()
            else -> null
        }
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
        private val POLICY_VIOLATION_CLOSE_STATUS = CloseStatus.POLICY_VIOLATION.withReason("unauthorized")
        private val REPLACED_BY_NEW_CONNECTION_STATUS =
            CloseStatus.POLICY_VIOLATION.withReason("replaced_by_new_connection")
    }
}
