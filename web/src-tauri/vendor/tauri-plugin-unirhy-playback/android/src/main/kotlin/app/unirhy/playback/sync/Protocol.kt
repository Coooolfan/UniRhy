package app.unirhy.playback.sync

import android.util.Log
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.treeToValue

/**
 * 播放同步线级协议的 Kotlin 实现，规格见 server/README/PLAYBACK_SYNC.md。
 * 与 TS 实现（web/src/services/playbackSyncProtocol.ts）保持字段级一致；
 * 解析时忽略未知字段。
 */
object PlaybackSyncJson {
    val mapper: ObjectMapper = jacksonObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
}

enum class PlaybackStatus {
    PLAYING,
    PAUSED,
}

enum class ScheduledActionType {
    PLAY,
    PAUSE,
    SEEK,
}

// ---------- C2S ----------

data class HelloPayload(
    val deviceId: String,
    val clientVersion: String? = null,
    val token: String? = null,
)

data class NtpRequestPayload(
    val t0: Long,
    val clientRttMs: Double? = null,
)

data class PlaybackControlPayload(
    val commandId: String,
    val deviceId: String,
    val currentIndex: Int,
    val positionSeconds: Double,
    val version: Long,
)

data class AudioSourceLoadedPayload(
    val commandId: String,
    val deviceId: String,
    val currentIndex: Int,
    val recordingId: Long,
)

data class SyncPayload(
    val deviceId: String,
)

fun encodeClientMessage(type: String, payload: Any): String {
    return PlaybackSyncJson.mapper.writeValueAsString(
        linkedMapOf("type" to type, "payload" to payload),
    )
}

// ---------- S2C ----------

data class NtpResponsePayload(
    val t0: Long,
    val t1: Long,
    val t2: Long,
)

data class PlaybackStateDto(
    val status: PlaybackStatus,
    val currentIndex: Int? = null,
    val positionSeconds: Double,
    val serverTimeToExecuteMs: Long,
    val version: Long,
    val updatedAtMs: Long,
)

data class CurrentQueueItemDto(
    val recordingId: Long,
    val title: String,
    val artistLabel: String,
    val coverUrl: String? = null,
    val durationMs: Long,
    val mediaFileId: Long? = null,
)

data class CurrentQueueDto(
    val items: List<CurrentQueueItemDto>,
    val recordingIds: List<Long>,
    val currentIndex: Int,
    val playbackStrategy: String,
    val stopStrategy: String,
    val playbackStatus: PlaybackStatus,
    val positionMs: Long,
    val serverTimeToExecuteMs: Long,
    val version: Long,
    val updatedAtMs: Long,
)

data class SnapshotPayload(
    val state: PlaybackStateDto,
    val queue: CurrentQueueDto,
    val serverNowMs: Long,
)

data class LoadAudioSourcePayload(
    val commandId: String,
    val currentIndex: Int,
    val recordingId: Long,
)

data class QueueChangePayload(
    val queue: CurrentQueueDto,
)

data class ScheduledPlaybackAction(
    val action: ScheduledActionType,
    val status: PlaybackStatus,
    val currentIndex: Int? = null,
    val positionSeconds: Double,
    val version: Long,
)

data class ScheduledActionPayload(
    val commandId: String,
    val serverTimeToExecuteMs: Long,
    val scheduledAction: ScheduledPlaybackAction,
    val skipLateCompensation: Boolean = false,
)

data class ErrorPayload(
    val code: String,
    val message: String,
)

sealed interface ServerMessage {
    data class NtpResponse(val payload: NtpResponsePayload) : ServerMessage
    data class Snapshot(val payload: SnapshotPayload) : ServerMessage
    data class LoadAudioSource(val payload: LoadAudioSourcePayload) : ServerMessage
    data class QueueChange(val payload: QueueChangePayload) : ServerMessage
    data class ScheduledAction(val payload: ScheduledActionPayload) : ServerMessage
    data class DeviceChange(val payload: JsonNode) : ServerMessage
    data class ProtocolError(val payload: ErrorPayload) : ServerMessage

    companion object {
        /**
         * 解析服务端消息；JSON 非法或消息类型未知时返回 null（与 TS 端一致，
         * 未知类型不视为致命错误）。
         */
        fun parse(raw: String): ServerMessage? {
            val root = runCatching { PlaybackSyncJson.mapper.readTree(raw) }.getOrElse {
                Log.w("UnirhyPlaybackProtocol", "parse readTree failed: ${it.message}")
                return null
            }
            val type = root.get("type")?.asText() ?: run {
                Log.w("UnirhyPlaybackProtocol", "parse missing type field")
                return null
            }
            val payload = root.get("payload") ?: run {
                Log.w("UnirhyPlaybackProtocol", "parse missing payload for type=$type")
                return null
            }
            val mapper = PlaybackSyncJson.mapper
            return runCatching {
                when (type) {
                    "NTP_RESPONSE" -> NtpResponse(mapper.treeToValue<NtpResponsePayload>(payload))
                    "SNAPSHOT" -> Snapshot(mapper.treeToValue<SnapshotPayload>(payload))
                    "ROOM_EVENT_LOAD_AUDIO_SOURCE" ->
                        LoadAudioSource(mapper.treeToValue<LoadAudioSourcePayload>(payload))
                    "ROOM_EVENT_QUEUE_CHANGE" ->
                        QueueChange(mapper.treeToValue<QueueChangePayload>(payload))
                    "SCHEDULED_ACTION" ->
                        ScheduledAction(mapper.treeToValue<ScheduledActionPayload>(payload))
                    "ROOM_EVENT_DEVICE_CHANGE" -> DeviceChange(payload)
                    "ERROR" -> ProtocolError(mapper.treeToValue<ErrorPayload>(payload))
                    else -> null
                }
            }.getOrElse {
                Log.w("UnirhyPlaybackProtocol", "parse treeToValue failed type=$type: ${it.javaClass.simpleName}: ${it.message}")
                null
            }
        }
    }
}
