package com.coooolfan.unirhy.sync.ws

import com.coooolfan.unirhy.sync.log.PlaybackSyncLogEvents
import com.coooolfan.unirhy.sync.log.PlaybackSyncLogFields
import com.coooolfan.unirhy.sync.log.PlaybackSyncLogWriter
import com.coooolfan.unirhy.sync.model.toProtocolState
import com.coooolfan.unirhy.sync.protocol.ClientPlaybackSyncMessage
import com.coooolfan.unirhy.sync.protocol.ErrorMessage
import com.coooolfan.unirhy.sync.protocol.ErrorPayload
import com.coooolfan.unirhy.sync.protocol.HelloMessage
import com.coooolfan.unirhy.sync.protocol.HelloPayload
import com.coooolfan.unirhy.sync.protocol.PlaybackSyncErrorCode
import com.coooolfan.unirhy.sync.protocol.PlaybackSyncMessageType
import com.coooolfan.unirhy.sync.protocol.SnapshotMessage
import com.coooolfan.unirhy.sync.protocol.SnapshotPayload
import com.coooolfan.unirhy.sync.service.DeviceRuntimeService
import com.coooolfan.unirhy.sync.service.PlaybackConnectionContext
import com.coooolfan.unirhy.sync.service.PlaybackSessionService
import com.coooolfan.unirhy.sync.service.PlaybackSyncMessageSender
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

        if (!context.helloCompleted && clientMessage.type != PlaybackSyncMessageType.HELLO) {
            sendProtocolError(
                context = context,
                code = PlaybackSyncErrorCode.INVALID_MESSAGE,
                message = "HELLO must be the first message",
                reason = "hello_required",
            )
            return
        }

        when (clientMessage) {
            is HelloMessage -> handleHello(context, clientMessage.payload)
            else -> handleUnsupportedMessage(context)
        }
    }

    override fun afterConnectionClosed(
        session: WebSocketSession,
        status: CloseStatus,
    ) {
        val sessionId = session.getPlaybackSyncSessionId()
        val removal = deviceRuntimeService.removeSession(sessionId) ?: return
        logConnectionClosed(removal.context, status.reason ?: "connection_closed")
        if (removal.deviceListChanged) {
            messageSender.broadcastDeviceChange(removal.context.accountId, removal.remainingDeviceIds)
        }
    }

    private fun handleHello(
        context: PlaybackConnectionContext,
        payload: HelloPayload,
    ) {
        if (context.helloCompleted) {
            sendProtocolError(
                context = context,
                code = PlaybackSyncErrorCode.INVALID_MESSAGE,
                message = "HELLO can only be sent once per connection",
                reason = "hello_already_received",
            )
            return
        }

        if (payload.deviceId.isBlank()) {
            sendProtocolError(
                context = context,
                code = PlaybackSyncErrorCode.INVALID_MESSAGE,
                message = "deviceId must not be blank",
                reason = "device_id_blank",
            )
            return
        }

        val registration = deviceRuntimeService.registerHello(
            accountId = context.accountId,
            sessionId = context.sessionId,
            deviceId = payload.deviceId,
            clientVersion = payload.clientVersion,
        )

        registration.replacedContext?.let { replacedContext ->
            logConnectionClosed(replacedContext, "replaced_by_new_connection")
            replacedContext.session.close(REPLACED_BY_NEW_CONNECTION_STATUS)
        }

        logWriter.info(
            event = PlaybackSyncLogEvents.HELLO_RECEIVED,
            PlaybackSyncLogFields.ACCOUNT_ID to registration.context.accountId,
            PlaybackSyncLogFields.DEVICE_ID to registration.context.deviceId,
            PlaybackSyncLogFields.SESSION_ID to registration.context.sessionId,
            PlaybackSyncLogFields.RESULT to "accepted",
        )

        val state = playbackSessionService.getOrCreateState(registration.context.accountId)
        val snapshotMessage = SnapshotMessage(
            payload = SnapshotPayload(
                state = state.toProtocolState(),
                serverNowMs = System.currentTimeMillis(),
            ),
        )
        messageSender.send(registration.context, snapshotMessage)

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

    private fun handleUnsupportedMessage(context: PlaybackConnectionContext) {
        sendProtocolError(
            context = context,
            code = PlaybackSyncErrorCode.UNSUPPORTED_MESSAGE,
            message = "Playback sync phase 1 only supports HELLO",
            reason = "unsupported_message",
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

    private fun logConnectionClosed(
        context: PlaybackConnectionContext,
        reason: String,
    ) {
        logWriter.info(
            event = PlaybackSyncLogEvents.CONNECTION_CLOSED,
            PlaybackSyncLogFields.ACCOUNT_ID to context.accountId,
            PlaybackSyncLogFields.DEVICE_ID to context.deviceId,
            PlaybackSyncLogFields.SESSION_ID to context.sessionId,
            PlaybackSyncLogFields.RESULT to "completed",
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
