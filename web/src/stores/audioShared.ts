import type {
    CurrentQueueDto,
    DeviceChangePayload,
    LoadAudioSourcePayload,
    PlaybackSyncStatePayload,
    ScheduledActionPayload,
} from '@/services/playbackSyncProtocol'
import type { PlaybackSyncClientDiagnosticsSnapshot } from '@/services/playbackSyncClient'

export type AudioTrack = {
    id: number
    title: string
    artist: string
    cover?: string
    src?: string
    mediaFileId?: number
    workId?: number
}

export type AudioQueueEntry = {
    queueIndex: number
    recordingId: number
    mediaFileId?: number
    title: string
    artist: string
    cover?: string
    durationMs: number
}

export type AudioSyncState =
    | 'connecting'
    | 'calibrating'
    | 'ready'
    | 'reconnecting'
    | 'audio_locked'
    | 'error'

export type TimestampedPayload<T> = {
    atMs: number
    payload: T
}

export type PlaybackSyncLocalExecutionSnapshot = {
    atMs: number
    action: ScheduledActionPayload['scheduledAction']['action']
    commandId: string
    version: number
    estimatedServerNowMs: number
    executeAtServerMs: number
    waitMs: number
    lateSeconds: number
    scheduledOffset: number
    whenContextTime: number
    bufferDuration: number
    currentIndex: number | null
    mediaFileId: number | null
}

type QueuedPlayIntent = {
    track: AudioTrack
    positionSeconds: number
}

export type PlaybackSyncDebugSnapshot = {
    syncState: AudioSyncState
    syncStatusText: string
    canSendRealtimeControl: boolean
    clockOffsetMs: number
    roundTripEstimateMs: number
    latestSnapshot: PlaybackSyncStatePayload | null
    latestSnapshotReceivedAtMs: number | null
    lastScheduledAction: TimestampedPayload<ScheduledActionPayload> | null
    lastLoadAudioSource: TimestampedPayload<LoadAudioSourcePayload> | null
    lastDeviceChange: TimestampedPayload<DeviceChangePayload> | null
    queuedPlayIntent: QueuedPlayIntent | null
    awaitingSyncRecovery: boolean
    audioUnlockRequired: boolean
    lastAppliedVersion: number
    currentTrack: AudioTrack | null
    isPlaying: boolean
    currentTime: number
    duration: number
    currentBuffer: {
        recordingId: number | null
        mediaFileId: number | null
        duration: number
        sampleRate: number
        numberOfChannels: number
        fileSizeBytes: number | null
        contentType: string | null
    } | null
    activeLoad: {
        mediaFileId: number
        inFlight: boolean
    } | null
    lastLocalExecution: PlaybackSyncLocalExecutionSnapshot | null
    clientDiagnostics: PlaybackSyncClientDiagnosticsSnapshot | null
    error: string | null
}

export const VOLUME_STORAGE_KEY = 'unirhy.audio.volume'
export const PLAYBACK_ERROR_MESSAGE = 'Unable to play audio'
export const MAX_KNOWN_TRACKS = 200

export const normalizeVolume = (volume: number) => {
    return Math.max(0, Math.min(1, volume))
}

export const readSavedVolume = () => {
    if (typeof window === 'undefined') {
        return 1
    }

    const raw = window.localStorage.getItem(VOLUME_STORAGE_KEY)
    if (raw === null) {
        return 1
    }

    const parsed = Number.parseFloat(raw)
    if (Number.isNaN(parsed)) {
        return 1
    }

    return normalizeVolume(parsed)
}

export const persistVolume = (volume: number) => {
    if (typeof window === 'undefined') {
        return
    }
    window.localStorage.setItem(VOLUME_STORAGE_KEY, volume.toString())
}

const isFiniteTime = (time: number) => Number.isFinite(time) && time >= 0

export const clampTime = (time: number, maxDuration?: number) => {
    if (!isFiniteTime(time)) {
        return 0
    }

    if (maxDuration === undefined || maxDuration === null) {
        return time
    }

    if (!isFiniteTime(maxDuration) || maxDuration <= 0) {
        return 0
    }

    return Math.min(time, maxDuration)
}

export const isNil = (value: unknown): value is null | undefined => {
    return value === null || value === undefined
}

export const pickFirstNonBlank = (...values: Array<string | null | undefined>) => {
    for (const value of values) {
        if (typeof value === 'string' && value.trim().length > 0) {
            return value
        }
    }
    return null
}

export const resolveMetadataArtistLabel = (
    artists: ReadonlyArray<{ displayName?: string | null }> | null | undefined,
) => {
    const names =
        artists
            ?.map((artist) => artist.displayName?.trim())
            .filter((name): name is string => typeof name === 'string' && name.length > 0) ?? []
    if (names.length === 0) {
        return null
    }
    return names.join(', ')
}

export const cloneTrack = (track: AudioTrack): AudioTrack => ({
    ...track,
})

export const isSameTrackRef = (left: AudioTrack | null, right: AudioTrack) => {
    return left?.id === right.id
}

export const createEmptyQueue = (): CurrentQueueDto => ({
    items: [],
    recordingIds: [],
    currentIndex: 0,
    playbackStrategy: 'SEQUENTIAL',
    stopStrategy: 'LIST',
    playbackStatus: 'PAUSED',
    positionMs: 0,
    serverTimeToExecuteMs: 0,
    version: 0,
    updatedAtMs: 0,
})

export const cloneQueue = (queue: CurrentQueueDto): CurrentQueueDto => ({
    items: queue.items.map((item) => ({ ...item })),
    recordingIds: [...queue.recordingIds],
    currentIndex: queue.currentIndex,
    playbackStrategy: queue.playbackStrategy,
    stopStrategy: queue.stopStrategy,
    playbackStatus: queue.playbackStatus,
    positionMs: queue.positionMs,
    serverTimeToExecuteMs: queue.serverTimeToExecuteMs,
    version: queue.version,
    updatedAtMs: queue.updatedAtMs,
})
