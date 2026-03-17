package com.coooolfan.unirhy.sync.protocol

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName

enum class PlaybackSyncMessageType {
    HELLO,
    NTP_REQUEST,
    PLAY,
    PAUSE,
    SEEK,
    AUDIO_SOURCE_LOADED,
    SYNC,
    NTP_RESPONSE,
    SNAPSHOT,
    ROOM_EVENT_LOAD_AUDIO_SOURCE,
    SCHEDULED_ACTION,
    ROOM_EVENT_DEVICE_CHANGE,
    ERROR,
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

enum class PlaybackSyncErrorCode {
    INVALID_MESSAGE,
    UNSUPPORTED_MESSAGE,
    RECORDING_NOT_FOUND,
    MEDIA_FILE_NOT_FOUND,
    RECORDING_NOT_PLAYABLE,
    SYNC_NOT_READY,
    INTERNAL_ERROR,
}

data class HelloPayload(
    val deviceId: String,
    val clientVersion: String? = null,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val token: String? = null,
)

data class NtpRequestPayload(
    val t0: Long,
    val clientRttMs: Double? = null,
)

data class PlaybackControlPayload(
    val commandId: String,
    val deviceId: String,
    val recordingId: Long? = null,
    val mediaFileId: Long? = null,
    val positionSeconds: Double,
)

data class AudioSourceLoadedPayload(
    val commandId: String,
    val deviceId: String,
    val recordingId: Long,
    val mediaFileId: Long,
)

data class SyncPayload(
    val deviceId: String,
)

data class NtpResponsePayload(
    val t0: Long,
    val t1: Long,
    val t2: Long,
)

data class AccountPlaybackStateDto(
    val status: PlaybackStatus,
    val recordingId: Long? = null,
    val mediaFileId: Long? = null,
    val sourceUrl: String? = null,
    val positionSeconds: Double,
    val serverTimeToExecuteMs: Long,
    val version: Long,
    val updatedAtMs: Long,
)

data class SnapshotPayload(
    val state: AccountPlaybackStateDto,
    val serverNowMs: Long,
)

data class LoadAudioSourcePayload(
    val commandId: String,
    val recordingId: Long,
    val mediaFileId: Long,
    val sourceUrl: String,
)

data class ScheduledPlaybackAction(
    val action: ScheduledActionType,
    val status: PlaybackStatus,
    val recordingId: Long? = null,
    val mediaFileId: Long? = null,
    val sourceUrl: String? = null,
    val positionSeconds: Double,
    val version: Long,
)

data class ScheduledActionPayload(
    val commandId: String,
    val serverTimeToExecuteMs: Long,
    val scheduledAction: ScheduledPlaybackAction,
)

data class PlaybackSyncDevice(
    val deviceId: String,
)

data class DeviceChangePayload(
    val devices: List<PlaybackSyncDevice>,
)

data class ErrorPayload(
    val code: PlaybackSyncErrorCode,
    val message: String,
)

@JsonPropertyOrder("type", "payload")
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "type",
    visible = true,
)
@JsonSubTypes(
    JsonSubTypes.Type(value = HelloMessage::class, name = "HELLO"),
    JsonSubTypes.Type(value = NtpRequestMessage::class, name = "NTP_REQUEST"),
    JsonSubTypes.Type(value = PlayMessage::class, name = "PLAY"),
    JsonSubTypes.Type(value = PauseMessage::class, name = "PAUSE"),
    JsonSubTypes.Type(value = SeekMessage::class, name = "SEEK"),
    JsonSubTypes.Type(value = AudioSourceLoadedMessage::class, name = "AUDIO_SOURCE_LOADED"),
    JsonSubTypes.Type(value = SyncMessage::class, name = "SYNC"),
)
sealed interface ClientPlaybackSyncMessage {
    val type: PlaybackSyncMessageType
    val payload: Any
}

@JsonTypeName("HELLO")
data class HelloMessage(
    override val payload: HelloPayload,
    override val type: PlaybackSyncMessageType = PlaybackSyncMessageType.HELLO,
) : ClientPlaybackSyncMessage

@JsonTypeName("NTP_REQUEST")
data class NtpRequestMessage(
    override val payload: NtpRequestPayload,
    override val type: PlaybackSyncMessageType = PlaybackSyncMessageType.NTP_REQUEST,
) : ClientPlaybackSyncMessage

@JsonTypeName("PLAY")
data class PlayMessage(
    override val payload: PlaybackControlPayload,
    override val type: PlaybackSyncMessageType = PlaybackSyncMessageType.PLAY,
) : ClientPlaybackSyncMessage

@JsonTypeName("PAUSE")
data class PauseMessage(
    override val payload: PlaybackControlPayload,
    override val type: PlaybackSyncMessageType = PlaybackSyncMessageType.PAUSE,
) : ClientPlaybackSyncMessage

@JsonTypeName("SEEK")
data class SeekMessage(
    override val payload: PlaybackControlPayload,
    override val type: PlaybackSyncMessageType = PlaybackSyncMessageType.SEEK,
) : ClientPlaybackSyncMessage

@JsonTypeName("AUDIO_SOURCE_LOADED")
data class AudioSourceLoadedMessage(
    override val payload: AudioSourceLoadedPayload,
    override val type: PlaybackSyncMessageType = PlaybackSyncMessageType.AUDIO_SOURCE_LOADED,
) : ClientPlaybackSyncMessage

@JsonTypeName("SYNC")
data class SyncMessage(
    override val payload: SyncPayload,
    override val type: PlaybackSyncMessageType = PlaybackSyncMessageType.SYNC,
) : ClientPlaybackSyncMessage

@JsonPropertyOrder("type", "payload")
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "type",
    visible = true,
)
@JsonSubTypes(
    JsonSubTypes.Type(value = NtpResponseMessage::class, name = "NTP_RESPONSE"),
    JsonSubTypes.Type(value = SnapshotMessage::class, name = "SNAPSHOT"),
    JsonSubTypes.Type(value = LoadAudioSourceMessage::class, name = "ROOM_EVENT_LOAD_AUDIO_SOURCE"),
    JsonSubTypes.Type(value = ScheduledActionMessage::class, name = "SCHEDULED_ACTION"),
    JsonSubTypes.Type(value = DeviceChangeMessage::class, name = "ROOM_EVENT_DEVICE_CHANGE"),
    JsonSubTypes.Type(value = ErrorMessage::class, name = "ERROR"),
)
sealed interface ServerPlaybackSyncMessage {
    val type: PlaybackSyncMessageType
    val payload: Any
}

@JsonTypeName("NTP_RESPONSE")
data class NtpResponseMessage(
    override val payload: NtpResponsePayload,
    override val type: PlaybackSyncMessageType = PlaybackSyncMessageType.NTP_RESPONSE,
) : ServerPlaybackSyncMessage

@JsonTypeName("SNAPSHOT")
data class SnapshotMessage(
    override val payload: SnapshotPayload,
    override val type: PlaybackSyncMessageType = PlaybackSyncMessageType.SNAPSHOT,
) : ServerPlaybackSyncMessage

@JsonTypeName("ROOM_EVENT_LOAD_AUDIO_SOURCE")
data class LoadAudioSourceMessage(
    override val payload: LoadAudioSourcePayload,
    override val type: PlaybackSyncMessageType = PlaybackSyncMessageType.ROOM_EVENT_LOAD_AUDIO_SOURCE,
) : ServerPlaybackSyncMessage

@JsonTypeName("SCHEDULED_ACTION")
data class ScheduledActionMessage(
    override val payload: ScheduledActionPayload,
    override val type: PlaybackSyncMessageType = PlaybackSyncMessageType.SCHEDULED_ACTION,
) : ServerPlaybackSyncMessage

@JsonTypeName("ROOM_EVENT_DEVICE_CHANGE")
data class DeviceChangeMessage(
    override val payload: DeviceChangePayload,
    override val type: PlaybackSyncMessageType = PlaybackSyncMessageType.ROOM_EVENT_DEVICE_CHANGE,
) : ServerPlaybackSyncMessage

@JsonTypeName("ERROR")
data class ErrorMessage(
    override val payload: ErrorPayload,
    override val type: PlaybackSyncMessageType = PlaybackSyncMessageType.ERROR,
) : ServerPlaybackSyncMessage
