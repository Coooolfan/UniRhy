package com.coooolfan.unirhy.sync.service

import com.coooolfan.unirhy.sync.protocol.DeviceChangeMessage
import com.coooolfan.unirhy.sync.protocol.DeviceChangePayload
import com.coooolfan.unirhy.sync.protocol.PlaybackSyncDevice
import com.coooolfan.unirhy.sync.protocol.ServerPlaybackSyncMessage
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.socket.TextMessage

@Service
class PlaybackSyncMessageSender(
    private val objectMapper: ObjectMapper,
    private val deviceRuntimeService: DeviceRuntimeService,
) {
    private val logger = LoggerFactory.getLogger(PlaybackSyncMessageSender::class.java)

    fun send(
        context: PlaybackConnectionContext,
        message: ServerPlaybackSyncMessage,
    ) {
        if (!context.session.isOpen) {
            return
        }

        val payload = objectMapper.writeValueAsString(message)
        context.session.sendMessage(TextMessage(payload))
    }

    fun broadcastDeviceChange(
        accountId: Long,
        deviceIds: List<String>,
    ) {
        val message = DeviceChangeMessage(
            payload = DeviceChangePayload(
                devices = deviceIds.map { PlaybackSyncDevice(deviceId = it) },
            ),
        )

        deviceRuntimeService.listHelloCompletedConnections(accountId)
            .forEach { context ->
                try {
                    send(context, message)
                } catch (ex: Exception) {
                    logger.warn("Failed to broadcast playback sync device change to sessionId={}", context.sessionId, ex)
                }
            }
    }
}
