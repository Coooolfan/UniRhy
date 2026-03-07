import { computed, ref, shallowRef } from 'vue'
import { defineStore } from 'pinia'
import {
    PlaybackSyncClient,
    type PlaybackSyncClientDiagnosticsSnapshot,
    type PlaybackSyncClientPhase,
} from '@/services/playbackSyncClient'
import type {
    DeviceChangePayload,
    LoadAudioSourcePayload,
    PlaybackSyncStatePayload,
    ScheduledActionPayload,
    ServerPlaybackSyncMessage,
    SnapshotPayload,
} from '@/services/playbackSyncProtocol'
import { nowClientMs } from '@/utils/time'

export type AudioTrack = {
    id: number
    title: string
    artist: string
    cover?: string
    src: string
    mediaFileId: number
    workId?: number
}

export type AudioSyncState =
    | 'connecting'
    | 'calibrating'
    | 'ready'
    | 'reconnecting'
    | 'audio_locked'
    | 'error'

type QueuedPlayIntent = {
    track: AudioTrack
    positionSeconds: number
}

type BufferLoadState = {
    mediaFileId: number
    promise: Promise<AudioBuffer | null>
}

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
    recordingId: number | null
    mediaFileId: number | null
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
    } | null
    activeLoad: {
        mediaFileId: number
        inFlight: boolean
    } | null
    lastLocalExecution: PlaybackSyncLocalExecutionSnapshot | null
    clientDiagnostics: PlaybackSyncClientDiagnosticsSnapshot | null
    error: string | null
}

const VOLUME_STORAGE_KEY = 'unirhy.audio.volume'
const PLAYBACK_ERROR_MESSAGE = 'Unable to play audio'
const MAX_KNOWN_TRACKS = 200

const normalizeVolume = (volume: number) => {
    return Math.max(0, Math.min(1, volume))
}

const readSavedVolume = () => {
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

const persistVolume = (volume: number) => {
    if (typeof window === 'undefined') {
        return
    }
    window.localStorage.setItem(VOLUME_STORAGE_KEY, volume.toString())
}

const isFiniteTime = (time: number) => Number.isFinite(time) && time >= 0

const clampTime = (time: number, maxDuration?: number) => {
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

const isNil = (value: unknown): value is null | undefined => {
    return value === null || value === undefined
}

const cloneTrack = (track: AudioTrack): AudioTrack => ({
    ...track,
})

export const useAudioStore = defineStore('audio', () => {
    const currentTrack = ref<AudioTrack | null>(null)
    const isPlaying = ref(false)
    const isPlayerHidden = ref(false)
    const currentTime = ref(0)
    const duration = ref(0)
    const volume = ref(readSavedVolume())
    const isLoading = ref(false)
    const error = ref<string | null>(null)

    const audioContext = shallowRef<AudioContext | null>(null)
    const currentBuffer = shallowRef<AudioBuffer | null>(null)
    const currentBufferTrack = shallowRef<AudioTrack | null>(null)
    const sourceNode = shallowRef<AudioBufferSourceNode | null>(null)
    const gainNode = shallowRef<GainNode | null>(null)
    const playbackStartContextTime = ref(0)
    const playbackOffsetSec = ref(0)
    const rafId = ref<number | null>(null)

    const playbackSyncClient = shallowRef<PlaybackSyncClient | null>(null)
    const clientPhase = ref<PlaybackSyncClientPhase>('connecting')
    const clockOffsetMs = ref(0)
    const roundTripEstimateMs = ref(0)
    const latestSnapshot = ref<PlaybackSyncStatePayload | null>(null)
    const latestSnapshotReceivedAtMs = ref<number | null>(null)
    const lastAppliedVersion = ref(0)
    const queuedPlayIntent = ref<QueuedPlayIntent | null>(null)
    const awaitingSyncRecovery = ref(false)
    const audioUnlockRequired = ref(false)
    const activeLoad = shallowRef<BufferLoadState | null>(null)
    const clientDiagnostics = shallowRef<PlaybackSyncClientDiagnosticsSnapshot | null>(null)
    const lastScheduledAction = shallowRef<TimestampedPayload<ScheduledActionPayload> | null>(null)
    const lastLoadAudioSource = shallowRef<TimestampedPayload<LoadAudioSourcePayload> | null>(null)
    const lastDeviceChange = shallowRef<TimestampedPayload<DeviceChangePayload> | null>(null)
    const lastLocalExecution = shallowRef<PlaybackSyncLocalExecutionSnapshot | null>(null)

    const knownTracks = new Map<number, AudioTrack>()
    const liveSourceNodes = new Set<AudioBufferSourceNode>()
    const ignoredEndedNodes = new WeakSet<AudioBufferSourceNode>()
    const sourceEndedListeners = new WeakMap<AudioBufferSourceNode, EventListener>()

    let commandSequence = 0
    let scheduledPauseTimer: number | null = null
    let scheduledStopTimer: number | null = null

    const effectiveSyncState = computed<AudioSyncState>(() => {
        if (clientPhase.value === 'error') {
            return 'error'
        }
        if (clientPhase.value === 'reconnecting') {
            return 'reconnecting'
        }
        if (clientPhase.value === 'calibrating') {
            return 'calibrating'
        }
        if (clientPhase.value === 'ready') {
            return audioUnlockRequired.value ? 'audio_locked' : 'ready'
        }
        return 'connecting'
    })

    const syncStatusText = computed(() => {
        switch (effectiveSyncState.value) {
            case 'connecting':
                return '同步连接中'
            case 'calibrating':
                return '同步校时中'
            case 'ready':
                return '同步已就绪'
            case 'reconnecting':
                return '同步重连中'
            case 'audio_locked':
                return '等待启用音频'
            case 'error':
                return '同步连接失败'
            default:
                return '同步连接中'
        }
    })

    const canSendRealtimeControl = computed(() => effectiveSyncState.value === 'ready')

    const playbackSyncDebugSnapshot = computed<PlaybackSyncDebugSnapshot>(() => {
        const queuedIntent = queuedPlayIntent.value
        const bufferTrack = currentBufferTrack.value

        return {
            syncState: effectiveSyncState.value,
            syncStatusText: syncStatusText.value,
            canSendRealtimeControl: canSendRealtimeControl.value,
            clockOffsetMs: clockOffsetMs.value,
            roundTripEstimateMs: roundTripEstimateMs.value,
            latestSnapshot: latestSnapshot.value ? { ...latestSnapshot.value } : null,
            latestSnapshotReceivedAtMs: latestSnapshotReceivedAtMs.value,
            lastScheduledAction: lastScheduledAction.value
                ? {
                      atMs: lastScheduledAction.value.atMs,
                      payload: { ...lastScheduledAction.value.payload },
                  }
                : null,
            lastLoadAudioSource: lastLoadAudioSource.value
                ? {
                      atMs: lastLoadAudioSource.value.atMs,
                      payload: { ...lastLoadAudioSource.value.payload },
                  }
                : null,
            lastDeviceChange: lastDeviceChange.value
                ? {
                      atMs: lastDeviceChange.value.atMs,
                      payload: {
                          devices: lastDeviceChange.value.payload.devices.map((device) => ({
                              ...device,
                          })),
                      },
                  }
                : null,
            queuedPlayIntent: queuedIntent
                ? {
                      track: cloneTrack(queuedIntent.track),
                      positionSeconds: queuedIntent.positionSeconds,
                  }
                : null,
            awaitingSyncRecovery: awaitingSyncRecovery.value,
            audioUnlockRequired: audioUnlockRequired.value,
            lastAppliedVersion: lastAppliedVersion.value,
            currentTrack: currentTrack.value ? cloneTrack(currentTrack.value) : null,
            isPlaying: isPlaying.value,
            currentTime: currentTime.value,
            duration: duration.value,
            currentBuffer:
                currentBuffer.value && bufferTrack
                    ? {
                          recordingId: bufferTrack.id,
                          mediaFileId: bufferTrack.mediaFileId,
                          duration: currentBuffer.value.duration,
                      }
                    : null,
            activeLoad: activeLoad.value
                ? {
                      mediaFileId: activeLoad.value.mediaFileId,
                      inFlight: true,
                  }
                : null,
            lastLocalExecution: lastLocalExecution.value ? { ...lastLocalExecution.value } : null,
            clientDiagnostics: clientDiagnostics.value,
            error: error.value,
        }
    })

    const rememberTrack = (track: AudioTrack) => {
        if (knownTracks.has(track.id)) {
            knownTracks.delete(track.id)
        }
        knownTracks.set(track.id, cloneTrack(track))
        if (knownTracks.size > MAX_KNOWN_TRACKS) {
            const oldestKey = knownTracks.keys().next().value
            if (oldestKey !== undefined) {
                knownTracks.delete(oldestKey)
            }
        }
    }

    const getEstimatedServerNowMs = () => {
        return nowClientMs() + clockOffsetMs.value
    }

    const clearScheduledTimers = () => {
        if (scheduledPauseTimer !== null) {
            window.clearTimeout(scheduledPauseTimer)
            scheduledPauseTimer = null
        }
        if (scheduledStopTimer !== null) {
            window.clearTimeout(scheduledStopTimer)
            scheduledStopTimer = null
        }
    }

    const stopAnimationLoop = () => {
        if (rafId.value === null || typeof cancelAnimationFrame === 'undefined') {
            return
        }

        cancelAnimationFrame(rafId.value)
        rafId.value = null
    }

    const updatePausedTime = (time: number) => {
        const nextTime = clampTime(time, currentBuffer.value?.duration ?? duration.value)
        playbackOffsetSec.value = nextTime
        currentTime.value = nextTime
    }

    const getCurrentPlaybackTime = () => {
        if (!audioContext.value || !isPlaying.value) {
            return playbackOffsetSec.value
        }

        const elapsed = Math.max(0, audioContext.value.currentTime - playbackStartContextTime.value)
        return clampTime(
            playbackOffsetSec.value + elapsed,
            currentBuffer.value?.duration ?? duration.value,
        )
    }

    const syncCurrentTime = () => {
        currentTime.value = getCurrentPlaybackTime()
    }

    const startAnimationLoop = () => {
        stopAnimationLoop()

        if (
            typeof requestAnimationFrame === 'undefined' ||
            !isPlaying.value ||
            !audioContext.value ||
            !currentBuffer.value
        ) {
            return
        }

        const tick = () => {
            if (!isPlaying.value || !audioContext.value || !currentBuffer.value) {
                rafId.value = null
                return
            }

            syncCurrentTime()
            rafId.value = requestAnimationFrame(tick)
        }

        syncCurrentTime()
        rafId.value = requestAnimationFrame(tick)
    }

    const ensureAudioContext = () => {
        const AudioContextConstructor = globalThis.window?.AudioContext
        if (AudioContextConstructor === undefined) {
            return null
        }

        if (!audioContext.value) {
            const context = new AudioContextConstructor()
            const gain = context.createGain()
            gain.gain.value = volume.value
            gain.connect(context.destination)

            audioContext.value = context
            gainNode.value = gain
        }

        return audioContext.value
    }

    const ensureAudioContextResumed = async () => {
        const context = ensureAudioContext()
        if (!context) {
            error.value = PLAYBACK_ERROR_MESSAGE
            return null
        }

        if (context.state === 'running') {
            audioUnlockRequired.value = false
            return context
        }

        audioUnlockRequired.value = true

        try {
            await context.resume()
        } catch {
            audioUnlockRequired.value = true
            isPlaying.value = false
            stopAnimationLoop()
            playbackStartContextTime.value = 0
            return null
        }

        audioUnlockRequired.value = false
        return context
    }

    const isSameBufferTrack = (track: AudioTrack) => {
        return (
            currentBufferTrack.value?.mediaFileId === track.mediaFileId &&
            currentBuffer.value !== null
        )
    }

    const cleanupSourceNode = (node: AudioBufferSourceNode) => {
        const endedListener = sourceEndedListeners.get(node)
        if (endedListener) {
            node.removeEventListener('ended', endedListener)
            sourceEndedListeners.delete(node)
        }
        liveSourceNodes.delete(node)

        try {
            node.disconnect()
        } catch {
            // Ignore already-disconnected nodes.
        }
    }

    const stopSourceNode = (node: AudioBufferSourceNode | null) => {
        if (!node) {
            return
        }

        ignoredEndedNodes.add(node)
        try {
            node.stop()
        } catch {
            // Ignore stop failures and continue explicit cleanup below.
        }
        cleanupSourceNode(node)
    }

    const stopAllSourceNodes = () => {
        const nodes = [...liveSourceNodes]
        nodes.forEach((node) => {
            stopSourceNode(node)
        })
        liveSourceNodes.clear()
        sourceNode.value = null
    }

    const handlePlaybackEnded = (node: AudioBufferSourceNode) => {
        const isIgnored = ignoredEndedNodes.has(node)
        if (isIgnored) {
            ignoredEndedNodes.delete(node)
        }

        cleanupSourceNode(node)

        if (sourceNode.value === node) {
            sourceNode.value = null
        }

        if (isIgnored || sourceNode.value !== null) {
            return
        }

        stopAnimationLoop()
        isPlaying.value = false
        playbackStartContextTime.value = 0
        playbackOffsetSec.value = 0
        currentTime.value = 0
    }

    const createSourceNode = (buffer: AudioBuffer) => {
        const context = ensureAudioContext()
        const gain = gainNode.value
        if (!context || !gain) {
            return null
        }

        const node = context.createBufferSource()
        node.buffer = buffer
        node.connect(gain)

        const endedListener: EventListener = () => {
            try {
                handlePlaybackEnded(node)
            } catch (caughtError) {
                cleanupSourceNode(node)
                if (sourceNode.value === node) {
                    sourceNode.value = null
                }
                stopAnimationLoop()
                isPlaying.value = false
                playbackStartContextTime.value = 0
                playbackOffsetSec.value = 0
                currentTime.value = 0
                console.error('Failed to finalize audio playback', caughtError)
            }
        }
        sourceEndedListeners.set(node, endedListener)
        node.addEventListener('ended', endedListener)
        liveSourceNodes.add(node)
        return node
    }

    const invalidateActiveLoad = () => {
        activeLoad.value = null
        isLoading.value = false
    }

    const releaseLoadedBuffer = () => {
        currentBuffer.value = null
        currentBufferTrack.value = null
        duration.value = 0
    }

    const clearTrackState = () => {
        invalidateActiveLoad()
        clearScheduledTimers()
        stopAnimationLoop()
        stopAllSourceNodes()
        currentTrack.value = null
        isPlaying.value = false
        playbackStartContextTime.value = 0
        playbackOffsetSec.value = 0
        currentTime.value = 0
        error.value = null
        releaseLoadedBuffer()
    }

    const createPlaceholderTrack = (
        recordingId: number,
        mediaFileId: number,
        sourceUrl: string,
    ): AudioTrack => {
        const current = currentTrack.value?.id === recordingId ? currentTrack.value : null
        const cached = knownTracks.get(recordingId)
        const base = current ?? cached
        const track: AudioTrack = {
            id: recordingId,
            title: base?.title ?? `Recording #${recordingId}`,
            artist: base?.artist ?? 'Unknown Artist',
            cover: base?.cover ?? '',
            src: sourceUrl,
            mediaFileId,
            ...(base?.workId !== undefined ? { workId: base.workId } : {}),
        }
        rememberTrack(track)
        return track
    }

    const resolveTrackFromState = (
        state:
            | PlaybackSyncStatePayload
            | ScheduledActionPayload['scheduledAction']
            | LoadAudioSourcePayload,
    ) => {
        if (isNil(state.recordingId) || isNil(state.mediaFileId) || isNil(state.sourceUrl)) {
            return null
        }
        return createPlaceholderTrack(state.recordingId, state.mediaFileId, state.sourceUrl)
    }

    const primeTrackState = (track: AudioTrack, positionSeconds: number) => {
        rememberTrack(track)
        currentTrack.value = cloneTrack(track)
        playbackOffsetSec.value = positionSeconds
        currentTime.value = positionSeconds
        error.value = null

        if (isSameBufferTrack(track)) {
            duration.value = currentBuffer.value!.duration
            updatePausedTime(positionSeconds)
        } else {
            duration.value = 0
        }
    }

    const ensureTrackBuffer = (track: AudioTrack) => {
        rememberTrack(track)

        if (isSameBufferTrack(track)) {
            if (currentTrack.value?.mediaFileId === track.mediaFileId) {
                duration.value = currentBuffer.value!.duration
            }
            return currentBuffer.value
        }

        const existingLoad = activeLoad.value
        if (existingLoad && existingLoad.mediaFileId === track.mediaFileId) {
            return existingLoad.promise
        }

        const context = ensureAudioContext()
        if (!context) {
            error.value = PLAYBACK_ERROR_MESSAGE
            return null
        }

        invalidateActiveLoad()
        isLoading.value = true
        error.value = null

        const loadState: BufferLoadState = {
            mediaFileId: track.mediaFileId,
            promise: Promise.resolve(null),
        }

        const promise = fetch(track.src)
            .then(async (response) => {
                if (!response.ok) {
                    throw new Error(`Failed to fetch audio: ${response.status}`)
                }

                const arrayBuffer = await response.arrayBuffer()
                const buffer = await context.decodeAudioData(arrayBuffer)
                if (activeLoad.value !== loadState) {
                    return null
                }

                currentBuffer.value = buffer
                currentBufferTrack.value = cloneTrack(track)
                if (currentTrack.value?.mediaFileId === track.mediaFileId) {
                    duration.value = buffer.duration
                    updatePausedTime(playbackOffsetSec.value)
                }
                isLoading.value = false
                return buffer
            })
            .catch(() => {
                if (activeLoad.value === loadState) {
                    isLoading.value = false
                    error.value = PLAYBACK_ERROR_MESSAGE
                }
                return null
            })
            .finally(() => {
                if (activeLoad.value === loadState) {
                    activeLoad.value = null
                }
            })

        loadState.promise = promise
        activeLoad.value = loadState

        return promise
    }

    const nextCommandId = (prefix: string) => {
        commandSequence += 1
        return `${prefix}-${Date.now()}-${commandSequence}`
    }

    const requestSyncRecovery = () => {
        if (!playbackSyncClient.value) {
            return
        }
        awaitingSyncRecovery.value = true
        playbackSyncClient.value.requestSync()
    }

    const flushQueuedPlayIntent = () => {
        const queued = queuedPlayIntent.value
        if (!queued || !playbackSyncClient.value) {
            return
        }

        queuedPlayIntent.value = null
        awaitingSyncRecovery.value = false
        playbackSyncClient.value.sendPlay({
            commandId: nextCommandId('play'),
            recordingId: queued.track.id,
            mediaFileId: queued.track.mediaFileId,
            positionSeconds: queued.positionSeconds,
        })
    }

    const handleClientReady = () => {
        if (audioUnlockRequired.value) {
            return
        }

        if (queuedPlayIntent.value) {
            flushQueuedPlayIntent()
            return
        }

        if (latestSnapshot.value) {
            requestSyncRecovery()
        }
    }

    const applyPausedState = (track: AudioTrack | null, positionSeconds: number) => {
        stopAnimationLoop()
        stopAllSourceNodes()
        sourceNode.value = null
        isPlaying.value = false
        playbackStartContextTime.value = 0

        if (track) {
            rememberTrack(track)
            currentTrack.value = cloneTrack(track)
            if (isSameBufferTrack(track)) {
                duration.value = currentBuffer.value!.duration
            }
            updatePausedTime(positionSeconds)
            return
        }

        currentTrack.value = null
        playbackOffsetSec.value = 0
        currentTime.value = 0
        releaseLoadedBuffer()
    }

    const schedulePauseState = (
        track: AudioTrack | null,
        positionSeconds: number,
        executeAtMs: number,
    ) => {
        clearScheduledTimers()
        const waitMs = Math.max(0, Math.round(executeAtMs - getEstimatedServerNowMs()))
        if (waitMs === 0) {
            applyPausedState(track, positionSeconds)
            return
        }

        scheduledPauseTimer = window.setTimeout(() => {
            scheduledPauseTimer = null
            applyPausedState(track, positionSeconds)
        }, waitMs)
    }

    const cancelPendingFutureSource = () => {
        if (
            audioContext.value &&
            sourceNode.value &&
            playbackStartContextTime.value > audioContext.value.currentTime
        ) {
            stopSourceNode(sourceNode.value)
            sourceNode.value = null
        }
    }

    const schedulePlayExecution = async (
        payload: ScheduledActionPayload,
        track: AudioTrack,
        actionToken: number,
    ) => {
        primeTrackState(track, payload.scheduledAction.positionSeconds)
        clearScheduledTimers()
        cancelPendingFutureSource()

        const buffer = await ensureTrackBuffer(track)
        if (!buffer || actionToken !== lastAppliedVersion.value) {
            return
        }

        const context = await ensureAudioContextResumed()
        if (!context || actionToken !== lastAppliedVersion.value) {
            return
        }

        const estimatedServerNowMs = getEstimatedServerNowMs()
        const lateSeconds = Math.max(
            0,
            (estimatedServerNowMs - payload.serverTimeToExecuteMs) / 1_000,
        )
        const scheduledOffset = clampTime(
            payload.scheduledAction.positionSeconds + lateSeconds,
            buffer.duration,
        )
        if (buffer.duration > 0 && scheduledOffset >= buffer.duration) {
            applyPausedState(track, buffer.duration)
            return
        }

        const waitMs = Math.max(0, Math.round(payload.serverTimeToExecuteMs - estimatedServerNowMs))
        const when = context.currentTime + waitMs / 1_000
        lastLocalExecution.value = {
            atMs: nowClientMs(),
            action: payload.scheduledAction.action,
            commandId: payload.commandId,
            version: payload.scheduledAction.version,
            estimatedServerNowMs,
            executeAtServerMs: payload.serverTimeToExecuteMs,
            waitMs,
            lateSeconds,
            scheduledOffset,
            whenContextTime: when,
            bufferDuration: buffer.duration,
            recordingId: payload.scheduledAction.recordingId,
            mediaFileId: payload.scheduledAction.mediaFileId,
        }
        const nextNode = createSourceNode(buffer)
        if (!nextNode) {
            error.value = PLAYBACK_ERROR_MESSAGE
            return
        }

        const previousNodes = [...liveSourceNodes].filter((node) => node !== nextNode)
        if (waitMs === 0) {
            previousNodes.forEach((node) => {
                stopSourceNode(node)
            })
        } else {
            scheduledStopTimer = window.setTimeout(() => {
                scheduledStopTimer = null
                previousNodes.forEach((node) => {
                    stopSourceNode(node)
                })
            }, waitMs)
        }

        sourceNode.value = nextNode
        playbackOffsetSec.value = scheduledOffset
        playbackStartContextTime.value = when
        currentTime.value = scheduledOffset
        isPlaying.value = true
        nextNode.start(when, scheduledOffset)
        startAnimationLoop()
    }

    const handleLoadAudioSource = async (payload: LoadAudioSourcePayload) => {
        const track = resolveTrackFromState(payload)
        if (!track || !playbackSyncClient.value) {
            return
        }

        currentTrack.value = cloneTrack(track)
        rememberTrack(track)

        const buffer = await ensureTrackBuffer(track)
        if (!buffer) {
            return
        }

        playbackSyncClient.value.sendAudioSourceLoaded({
            commandId: payload.commandId,
            recordingId: payload.recordingId,
            mediaFileId: payload.mediaFileId,
        })
    }

    const handleSnapshot = (payload: SnapshotPayload) => {
        latestSnapshotReceivedAtMs.value = nowClientMs()
        latestSnapshot.value = payload.state
        const track = resolveTrackFromState(payload.state)

        if (!track) {
            if (!isPlaying.value) {
                applyPausedState(null, 0)
            }
            return
        }

        rememberTrack(track)
        currentTrack.value = cloneTrack(track)

        const recoveredPosition =
            payload.state.status === 'PLAYING'
                ? payload.state.positionSeconds +
                  Math.max(0, payload.serverNowMs - payload.state.serverTimeToExecuteMs) / 1_000
                : payload.state.positionSeconds
        updatePausedTime(recoveredPosition)

        if (isSameBufferTrack(track)) {
            duration.value = currentBuffer.value!.duration
        } else {
            duration.value = 0
        }
    }

    const handleScheduledAction = async (payload: ScheduledActionPayload) => {
        const { scheduledAction } = payload

        if (scheduledAction.version < lastAppliedVersion.value) {
            return
        }
        if (scheduledAction.version === lastAppliedVersion.value && !awaitingSyncRecovery.value) {
            return
        }

        awaitingSyncRecovery.value = false
        lastAppliedVersion.value = scheduledAction.version
        latestSnapshotReceivedAtMs.value = nowClientMs()
        latestSnapshot.value = {
            status: scheduledAction.status,
            recordingId: scheduledAction.recordingId,
            mediaFileId: scheduledAction.mediaFileId,
            sourceUrl: scheduledAction.sourceUrl,
            positionSeconds: scheduledAction.positionSeconds,
            serverTimeToExecuteMs: payload.serverTimeToExecuteMs,
            version: scheduledAction.version,
            updatedAtMs: nowClientMs(),
        }

        const track = resolveTrackFromState(scheduledAction)
        const actionToken = scheduledAction.version

        if (scheduledAction.action === 'PAUSE') {
            schedulePauseState(
                track,
                scheduledAction.positionSeconds,
                payload.serverTimeToExecuteMs,
            )
            return
        }

        if (scheduledAction.action === 'SEEK' && scheduledAction.status === 'PAUSED') {
            schedulePauseState(
                track,
                scheduledAction.positionSeconds,
                payload.serverTimeToExecuteMs,
            )
            return
        }

        if (!track) {
            applyPausedState(null, 0)
            return
        }

        await schedulePlayExecution(payload, track, actionToken)
    }

    const handleServerMessage = async (message: ServerPlaybackSyncMessage) => {
        const receivedAtMs = nowClientMs()
        switch (message.type) {
            case 'SNAPSHOT':
                handleSnapshot(message.payload)
                break
            case 'NTP_RESPONSE':
                break
            case 'ROOM_EVENT_LOAD_AUDIO_SOURCE':
                lastLoadAudioSource.value = {
                    atMs: receivedAtMs,
                    payload: { ...message.payload },
                }
                await handleLoadAudioSource(message.payload)
                break
            case 'SCHEDULED_ACTION':
                lastScheduledAction.value = {
                    atMs: receivedAtMs,
                    payload: { ...message.payload },
                }
                await handleScheduledAction(message.payload)
                break
            case 'ROOM_EVENT_DEVICE_CHANGE':
                lastDeviceChange.value = {
                    atMs: receivedAtMs,
                    payload: {
                        devices: message.payload.devices.map((device) => ({ ...device })),
                    },
                }
                break
            case 'ERROR':
                error.value = message.payload.message
                break
            default:
                break
        }
    }

    const updateClientState = (phase: PlaybackSyncClientPhase, offsetMs: number, rttMs: number) => {
        const previousPhase = clientPhase.value
        clientPhase.value = phase
        clockOffsetMs.value = offsetMs
        roundTripEstimateMs.value = rttMs

        if (previousPhase !== 'ready' && phase === 'ready') {
            handleClientReady()
        }
    }

    const updateClientDiagnostics = (snapshot: PlaybackSyncClientDiagnosticsSnapshot) => {
        clientDiagnostics.value = snapshot
    }

    const ensureSyncClient = () => {
        if (playbackSyncClient.value) {
            return playbackSyncClient.value
        }

        const client = new PlaybackSyncClient({
            onMessage: (message) => {
                void handleServerMessage(message)
            },
            onStateChange: (state) => {
                updateClientState(state.phase, state.clockOffsetMs, state.roundTripEstimateMs)
            },
            onDiagnosticsChange: (snapshot) => {
                updateClientDiagnostics(snapshot)
            },
        })
        playbackSyncClient.value = client
        const state = client.getState()
        updateClientDiagnostics(client.getDiagnosticsSnapshot())
        updateClientState(state.phase, state.clockOffsetMs, state.roundTripEstimateMs)
        return client
    }

    const queuePlay = (track: AudioTrack, positionSeconds: number) => {
        primeTrackState(track, positionSeconds)
        queuedPlayIntent.value = {
            track: cloneTrack(track),
            positionSeconds,
        }
        isPlaying.value = false
    }

    const sendPlayCommand = (track: AudioTrack, positionSeconds: number) => {
        primeTrackState(track, positionSeconds)
        queuedPlayIntent.value = null
        awaitingSyncRecovery.value = false
        ensureSyncClient().sendPlay({
            commandId: nextCommandId('play'),
            recordingId: track.id,
            mediaFileId: track.mediaFileId,
            positionSeconds,
        })
    }

    async function play(track: AudioTrack) {
        rememberTrack(track)

        const isSameTrack = currentTrack.value?.id === track.id
        const targetPosition = isSameTrack ? currentTime.value : 0

        if (isSameTrack && isPlaying.value) {
            pause()
            return
        }

        const client = ensureSyncClient()
        client.connect()

        if (effectiveSyncState.value === 'audio_locked') {
            queuePlay(track, targetPosition)
            const context = await ensureAudioContextResumed()
            if (!context) {
                return
            }
            if (canSendRealtimeControl.value) {
                flushQueuedPlayIntent()
            }
            return
        }

        if (!canSendRealtimeControl.value) {
            queuePlay(track, targetPosition)
            return
        }

        sendPlayCommand(track, targetPosition)
    }

    function pause() {
        if (!currentTrack.value || !canSendRealtimeControl.value) {
            return
        }

        ensureSyncClient().sendPause({
            commandId: nextCommandId('pause'),
            recordingId: currentTrack.value.id,
            mediaFileId: currentTrack.value.mediaFileId,
            positionSeconds: currentTime.value,
        })
    }

    async function resume() {
        if (!currentTrack.value) {
            return
        }

        if (effectiveSyncState.value === 'audio_locked') {
            const context = await ensureAudioContextResumed()
            if (!context) {
                return
            }
            if (queuedPlayIntent.value) {
                flushQueuedPlayIntent()
                return
            }
            requestSyncRecovery()
            return
        }

        if (!canSendRealtimeControl.value) {
            queuePlay(currentTrack.value, currentTime.value)
            return
        }

        sendPlayCommand(currentTrack.value, currentTime.value)
    }

    function stop() {
        queuedPlayIntent.value = null
        awaitingSyncRecovery.value = false

        if (!canSendRealtimeControl.value) {
            latestSnapshot.value = null
            latestSnapshotReceivedAtMs.value = null
            clearTrackState()
            return
        }

        ensureSyncClient().sendPause({
            commandId: nextCommandId('stop'),
            recordingId: null,
            mediaFileId: null,
            positionSeconds: 0,
        })
    }

    function hidePlayer() {
        if (currentTrack.value) {
            isPlayerHidden.value = true
        }
    }

    function showPlayer() {
        isPlayerHidden.value = false
    }

    function seek(time: number) {
        if (!currentTrack.value || !canSendRealtimeControl.value) {
            return
        }

        const bufferDuration = currentBuffer.value?.duration ?? duration.value
        const nextTime = clampTime(time, bufferDuration)
        ensureSyncClient().sendSeek({
            commandId: nextCommandId('seek'),
            recordingId: currentTrack.value.id,
            mediaFileId: currentTrack.value.mediaFileId,
            positionSeconds: nextTime,
        })
    }

    function setVolume(vol: number) {
        const normalizedVolume = normalizeVolume(vol)
        volume.value = normalizedVolume
        persistVolume(normalizedVolume)

        if (gainNode.value) {
            gainNode.value.gain.value = normalizedVolume
        }
    }

    function connectPlaybackSync() {
        const client = ensureSyncClient()
        client.connect()
    }

    const destroyAudioContext = () => {
        const context = audioContext.value
        audioContext.value = null
        gainNode.value = null

        if (!context || typeof context.close !== 'function') {
            return
        }

        void context.close().catch(() => {
            // Ignore close failures during teardown.
        })
    }

    function disconnectPlaybackSync() {
        playbackSyncClient.value?.disconnect()
        playbackSyncClient.value = null
        clientDiagnostics.value = null
        queuedPlayIntent.value = null
        latestSnapshot.value = null
        latestSnapshotReceivedAtMs.value = null
        lastScheduledAction.value = null
        lastLoadAudioSource.value = null
        lastDeviceChange.value = null
        lastLocalExecution.value = null
        lastAppliedVersion.value = 0
        clientPhase.value = 'connecting'
        clockOffsetMs.value = 0
        roundTripEstimateMs.value = 0
        awaitingSyncRecovery.value = false
        audioUnlockRequired.value = false
        isPlayerHidden.value = false
        knownTracks.clear()
        clearTrackState()
        destroyAudioContext()
    }

    return {
        currentTrack,
        isPlaying,
        isPlayerHidden,
        currentTime,
        duration,
        volume,
        isLoading,
        error,
        syncState: effectiveSyncState,
        syncStatusText,
        canSendRealtimeControl,
        play,
        pause,
        resume,
        stop,
        hidePlayer,
        showPlayer,
        seek,
        setVolume,
        connectPlaybackSync,
        disconnectPlaybackSync,
        playbackSyncDebugSnapshot,
    }
})
