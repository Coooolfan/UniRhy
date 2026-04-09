package com.coooolfan.unirhy.sync.service

import com.coooolfan.unirhy.sync.protocol.DeviceChangeMessage
import com.coooolfan.unirhy.sync.protocol.DeviceChangePayload
import com.coooolfan.unirhy.sync.protocol.LoadAudioSourceMessage
import com.coooolfan.unirhy.sync.protocol.LoadAudioSourcePayload
import com.coooolfan.unirhy.sync.protocol.NtpResponseMessage
import com.coooolfan.unirhy.sync.protocol.NtpResponsePayload
import com.coooolfan.unirhy.sync.protocol.CurrentQueueDto
import com.coooolfan.unirhy.sync.protocol.PlaybackSyncDevice
import com.coooolfan.unirhy.sync.protocol.QueueChangeMessage
import com.coooolfan.unirhy.sync.protocol.QueueChangePayload
import com.coooolfan.unirhy.sync.protocol.ScheduledActionMessage
import com.coooolfan.unirhy.sync.protocol.ScheduledActionPayload
import com.coooolfan.unirhy.sync.protocol.ServerPlaybackSyncMessage
import com.coooolfan.unirhy.sync.protocol.SnapshotMessage
import com.coooolfan.unirhy.sync.protocol.SnapshotPayload
import com.coooolfan.unirhy.service.MediaUrlSigner
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.socket.TextMessage

@Service
class PlaybackSyncMessageSender(
    private val objectMapper: ObjectMapper,
    private val deviceRuntimeService: DeviceRuntimeService,
    @Suppress("UNUSED_PARAMETER")
    private val urlSigner: MediaUrlSigner? = null,
    private val broadcastPublisher: PlaybackSyncBroadcastPublisher? = null,
) {
    private val logger = LoggerFactory.getLogger(PlaybackSyncMessageSender::class.java)

    fun send(
        context: PlaybackConnectionContext,
        message: ServerPlaybackSyncMessage,
    ) {
        sendSerialized(context, serialize(message))
    }

    fun sendNtpResponse(
        context: PlaybackConnectionContext,
        payload: NtpResponsePayload,
    ) {
        send(context, NtpResponseMessage(payload = payload))
    }

    fun sendSnapshot(
        context: PlaybackConnectionContext,
        payload: SnapshotPayload,
    ) {
        send(context, SnapshotMessage(payload = payload))
    }

    fun sendScheduledAction(
        context: PlaybackConnectionContext,
        payload: ScheduledActionPayload,
    ) {
        send(context, ScheduledActionMessage(payload = payload))
    }

    fun broadcastLoadAudioSource(
        accountId: Long,
        payload: LoadAudioSourcePayload,
    ) {
        broadcast(
            accountId = accountId,
            eventType = PlaybackSyncEventType.LOAD_AUDIO_SOURCE,
            dedupeKey = "load-audio-source:$accountId:${payload.commandId}",
            message = LoadAudioSourceMessage(payload = payload),
        )
    }

    fun broadcastQueueChange(
        accountId: Long,
        queue: CurrentQueueDto,
    ) {
        broadcast(
            accountId = accountId,
            eventType = PlaybackSyncEventType.QUEUE_CHANGE,
            dedupeKey = "queue-change:$accountId:${queue.version}",
            message = QueueChangeMessage(
                payload = QueueChangePayload(queue = queue),
            ),
        )
    }

    fun broadcastScheduledAction(
        accountId: Long,
        payload: ScheduledActionPayload,
    ) {
        broadcast(
            accountId = accountId,
            eventType = PlaybackSyncEventType.SCHEDULED_ACTION,
            dedupeKey = "scheduled-action:$accountId:${payload.commandId}",
            message = ScheduledActionMessage(payload = payload),
        )
    }

    fun broadcastDeviceChange(
        accountId: Long,
        deviceIds: List<String>,
    ) {
        broadcast(
            accountId = accountId,
            eventType = PlaybackSyncEventType.DEVICE_CHANGE,
            dedupeKey = "device-change:$accountId:${deviceIds.joinToString(",")}",
            message = DeviceChangeMessage(
                payload = DeviceChangePayload(
                    devices = deviceIds.map { PlaybackSyncDevice(deviceId = it) },
                ),
            ),
        )
    }

    fun deliverLocalBroadcast(
        accountId: Long,
        message: ServerPlaybackSyncMessage,
    ) {
        val textMessage = serialize(message)
        deviceRuntimeService.listHelloCompletedConnections(accountId)
            .forEach { context ->
                try {
                    sendSerialized(context, textMessage)
                } catch (ex: Exception) {
                    logger.warn("Failed to broadcast playback sync message to sessionId={}", context.sessionId, ex)
                }
            }
    }

    private fun broadcast(
        accountId: Long,
        eventType: PlaybackSyncEventType,
        dedupeKey: String,
        message: ServerPlaybackSyncMessage,
    ) {
        val serialized = objectMapper.writeValueAsString(message)
        if (broadcastPublisher == null) {
            deliverLocalBroadcast(accountId, message)
            return
        }
        broadcastPublisher.publish(
            accountId = accountId,
            eventType = eventType,
            dedupeKey = dedupeKey,
            payload = serialized,
        )
    }

    private fun sendSerialized(
        context: PlaybackConnectionContext,
        message: TextMessage,
    ) {
        val session = context.session ?: return
        if (!session.isOpen) {
            return
        }

        session.sendMessage(message)
    }

    private fun serialize(message: ServerPlaybackSyncMessage): TextMessage {
        return TextMessage(objectMapper.writeValueAsString(message))
    }
}
