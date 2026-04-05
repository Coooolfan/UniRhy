export type PlaybackSyncMessageType =
    | 'HELLO'
    | 'NTP_REQUEST'
    | 'PLAY'
    | 'PAUSE'
    | 'SEEK'
    | 'AUDIO_SOURCE_LOADED'
    | 'SYNC'
    | 'NTP_RESPONSE'
    | 'SNAPSHOT'
    | 'ROOM_EVENT_LOAD_AUDIO_SOURCE'
    | 'ROOM_EVENT_QUEUE_CHANGE'
    | 'SCHEDULED_ACTION'
    | 'ROOM_EVENT_DEVICE_CHANGE'
    | 'ERROR'

export type PlaybackStatus = 'PLAYING' | 'PAUSED'
export type ScheduledActionType = 'PLAY' | 'PAUSE' | 'SEEK'
export type PlaybackStrategy = 'SEQUENTIAL' | 'SHUFFLE' | 'RADIO'
export type StopStrategy = 'TRACK' | 'LIST'
export type PlaybackSyncErrorCode =
    | 'INVALID_MESSAGE'
    | 'UNSUPPORTED_MESSAGE'
    | 'RECORDING_NOT_FOUND'
    | 'MEDIA_FILE_NOT_FOUND'
    | 'RECORDING_NOT_PLAYABLE'
    | 'SYNC_NOT_READY'
    | 'INTERNAL_ERROR'

export type PlaybackSyncStatePayload = {
    status: PlaybackStatus
    recordingId: number | null
    positionSeconds: number
    serverTimeToExecuteMs: number
    version: number
    updatedAtMs: number
}

export type HelloPayload = {
    deviceId: string
    clientVersion?: string
    token?: string
}

export type NtpRequestPayload = {
    t0: number
    clientRttMs?: number
}

export type PlaybackControlPayload = {
    commandId: string
    deviceId: string
    recordingId?: number | null
    mediaFileId?: number | null
    positionSeconds: number
}

export type AudioSourceLoadedPayload = {
    commandId: string
    deviceId: string
    recordingId: number
    mediaFileId: number
}

export type SyncPayload = {
    deviceId: string
}

export type NtpResponsePayload = {
    t0: number
    t1: number
    t2: number
}

export type SnapshotPayload = {
    state: PlaybackSyncStatePayload
    queue: CurrentQueueDto
    serverNowMs: number
}

export type CurrentQueueItemDto = {
    entryId: number
    recordingId: number
    title: string
    artistLabel: string
    coverUrl?: string
    durationMs: number
}

export type CurrentQueueDto = {
    items: readonly CurrentQueueItemDto[]
    currentEntryId?: number
    playbackStrategy: PlaybackStrategy
    stopStrategy: StopStrategy
    version: number
    updatedAtMs: number
}

export type QueueChangePayload = {
    queue: CurrentQueueDto
}

export type LoadAudioSourcePayload = {
    commandId: string
    recordingId: number
}

export type ScheduledPlaybackAction = {
    action: ScheduledActionType
    status: PlaybackStatus
    recordingId: number | null
    positionSeconds: number
    version: number
}

export type ScheduledActionPayload = {
    commandId: string
    serverTimeToExecuteMs: number
    scheduledAction: ScheduledPlaybackAction
}

export type DeviceChangePayload = {
    devices: Array<{
        deviceId: string
    }>
}

export type ErrorPayload = {
    code: PlaybackSyncErrorCode
    message: string
}

export type HelloMessage = {
    type: 'HELLO'
    payload: HelloPayload
}

export type NtpRequestMessage = {
    type: 'NTP_REQUEST'
    payload: NtpRequestPayload
}

export type PlayMessage = {
    type: 'PLAY'
    payload: PlaybackControlPayload
}

export type PauseMessage = {
    type: 'PAUSE'
    payload: PlaybackControlPayload
}

export type SeekMessage = {
    type: 'SEEK'
    payload: PlaybackControlPayload
}

export type AudioSourceLoadedMessage = {
    type: 'AUDIO_SOURCE_LOADED'
    payload: AudioSourceLoadedPayload
}

export type SyncMessage = {
    type: 'SYNC'
    payload: SyncPayload
}

export type ClientPlaybackSyncMessage =
    | HelloMessage
    | NtpRequestMessage
    | PlayMessage
    | PauseMessage
    | SeekMessage
    | AudioSourceLoadedMessage
    | SyncMessage

export type NtpResponseMessage = {
    type: 'NTP_RESPONSE'
    payload: NtpResponsePayload
}

export type SnapshotMessage = {
    type: 'SNAPSHOT'
    payload: SnapshotPayload
}

export type LoadAudioSourceMessage = {
    type: 'ROOM_EVENT_LOAD_AUDIO_SOURCE'
    payload: LoadAudioSourcePayload
}

export type QueueChangeMessage = {
    type: 'ROOM_EVENT_QUEUE_CHANGE'
    payload: QueueChangePayload
}

export type ScheduledActionMessage = {
    type: 'SCHEDULED_ACTION'
    payload: ScheduledActionPayload
}

export type DeviceChangeMessage = {
    type: 'ROOM_EVENT_DEVICE_CHANGE'
    payload: DeviceChangePayload
}

export type ErrorMessage = {
    type: 'ERROR'
    payload: ErrorPayload
}

export type ServerPlaybackSyncMessage =
    | NtpResponseMessage
    | SnapshotMessage
    | LoadAudioSourceMessage
    | QueueChangeMessage
    | ScheduledActionMessage
    | DeviceChangeMessage
    | ErrorMessage
