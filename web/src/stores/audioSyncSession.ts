import type { Ref, ShallowRef } from 'vue'
import {
    PlaybackSyncClient,
    type PlaybackSyncClientDiagnosticsSnapshot,
    type PlaybackSyncClientPhase,
} from '@/services/playbackSyncClient'
import type {
    CurrentQueueDto,
    DeviceChangePayload,
    LoadAudioSourcePayload,
    PlaybackSyncStatePayload,
    QueueChangePayload,
    ScheduledActionPayload,
    ServerPlaybackSyncMessage,
    SnapshotPayload,
} from '@/services/playbackSyncProtocol'
import {
    type AudioTrack,
    type TimestampedPayload,
    PLAYBACK_ERROR_MESSAGE,
    cloneTrack,
    isNil,
} from '@/stores/audioShared'
import { nowClientMs } from '@/utils/time'

export type QueuedPlayIntent = {
    track: AudioTrack
    positionSeconds: number
}

export type QueuedSystemPauseIntent = {
    recordingId: number
    currentIndex: number
    positionSeconds: number
    commandId?: string
    conflictRecoveryAttempted?: boolean
}

const queuedSystemPauseMatches = (
    intent: QueuedSystemPauseIntent,
    currentIndex: number | null,
    recordingId: number | null,
) => intent.currentIndex === currentIndex && intent.recordingId === recordingId

type UsePlaybackSyncSessionOptions = {
    playbackSyncClient: ShallowRef<PlaybackSyncClient | null>
    clientPhase: Ref<PlaybackSyncClientPhase>
    clockOffsetMs: Ref<number>
    roundTripEstimateMs: Ref<number>
    latestSnapshot: Ref<PlaybackSyncStatePayload | null>
    latestSnapshotReceivedAtMs: Ref<number | null>
    lastAppliedVersion: Ref<number>
    queuedPlayIntent: Ref<QueuedPlayIntent | null>
    queuedSystemPauseIntent: Ref<QueuedSystemPauseIntent | null>
    awaitingSyncRecovery: Ref<boolean>
    audioUnlockRequired: Ref<boolean>
    clientDiagnostics: ShallowRef<PlaybackSyncClientDiagnosticsSnapshot | null>
    lastScheduledAction: ShallowRef<TimestampedPayload<ScheduledActionPayload> | null>
    lastLoadAudioSource: ShallowRef<TimestampedPayload<LoadAudioSourcePayload> | null>
    lastDeviceChange: ShallowRef<TimestampedPayload<DeviceChangePayload> | null>
    preloadedTrackForCommand: ShallowRef<{ commandId: string; track: AudioTrack } | null>
    currentTrack: Ref<AudioTrack | null>
    isPlaying: Ref<boolean>
    duration: Ref<number>
    error: Ref<string | null>
    currentBuffer: ShallowRef<AudioBuffer | null>
    isIndependentPlaybackMode: () => boolean
    // 队列层
    getCurrentQueueControlContext: () => { currentIndex: number; version: number } | null
    getQueueRecordingId: (queueIndex: number | null | undefined) => number | null
    syncQueuePlaybackState: (state: PlaybackSyncStatePayload) => void
    applyQueueSnapshot: (queue: CurrentQueueDto) => void
    // 引擎层
    primeTrackState: (track: AudioTrack, positionSeconds: number) => void
    ensureTrackBuffer: (track: AudioTrack) => AudioBuffer | Promise<AudioBuffer | null> | null
    applyPausedState: (track: AudioTrack | null, positionSeconds: number) => void
    schedulePauseState: (
        track: AudioTrack | null,
        positionSeconds: number,
        executeAtMs: number,
    ) => void
    schedulePlayExecution: (
        payload: ScheduledActionPayload,
        track: AudioTrack,
        actionToken: number,
    ) => Promise<void>
    updatePausedTime: (time: number) => void
    isSameBufferTrack: (track: AudioTrack) => boolean
    // 曲目目录层
    createTrackShell: (recordingId: number) => AudioTrack
    hydrateTrackMetadata: (track: AudioTrack) => Promise<void>
    resolveTrackForPlayback: (track: AudioTrack) => Promise<AudioTrack | null>
    cacheTrack: (track: AudioTrack) => void
}

/**
 * 同步会话层：管理 PlaybackSyncClient 生命周期、NTP 校时状态与全部服务端消息处理
 * （快照恢复、队列变更、调度动作、音源预加载）。将播放执行委托给引擎层。
 */
export const usePlaybackSyncSession = (options: UsePlaybackSyncSessionOptions) => {
    const {
        playbackSyncClient,
        clientPhase,
        clockOffsetMs,
        roundTripEstimateMs,
        latestSnapshot,
        latestSnapshotReceivedAtMs,
        lastAppliedVersion,
        queuedPlayIntent,
        queuedSystemPauseIntent,
        awaitingSyncRecovery,
        audioUnlockRequired,
        clientDiagnostics,
        lastScheduledAction,
        lastLoadAudioSource,
        lastDeviceChange,
        preloadedTrackForCommand,
        currentTrack,
        isPlaying,
        duration,
        error,
        currentBuffer,
        isIndependentPlaybackMode,
        getCurrentQueueControlContext,
        getQueueRecordingId,
        syncQueuePlaybackState,
        applyQueueSnapshot,
        primeTrackState,
        ensureTrackBuffer,
        applyPausedState,
        schedulePauseState,
        schedulePlayExecution,
        updatePausedTime,
        isSameBufferTrack,
        createTrackShell,
        hydrateTrackMetadata,
        resolveTrackForPlayback,
        cacheTrack,
    } = options

    let commandSequence = 0
    let applyNextSnapshotPlaybackState = false

    const nextCommandId = (prefix: string) => {
        commandSequence += 1
        return `${prefix}-${Date.now()}-${commandSequence}`
    }

    const requestSyncRecovery = () => {
        if (!playbackSyncClient.value || awaitingSyncRecovery.value) {
            return
        }
        awaitingSyncRecovery.value = true
        playbackSyncClient.value.requestSync()
    }

    const flushQueuedSystemPauseIntent = () => {
        const queued = queuedSystemPauseIntent.value
        const client = playbackSyncClient.value
        if (!queued || !client || clientPhase.value !== 'ready') {
            return false
        }

        const controlContext = getCurrentQueueControlContext()
        if (!controlContext) {
            queuedSystemPauseIntent.value = null
            return false
        }

        const recordingId = getQueueRecordingId(controlContext.currentIndex)
        if (!queuedSystemPauseMatches(queued, controlContext.currentIndex, recordingId)) {
            queuedSystemPauseIntent.value = null
            return false
        }

        if (queued.commandId) {
            return true
        }

        const commandId = nextCommandId('system-pause')
        queuedSystemPauseIntent.value = { ...queued, commandId }
        awaitingSyncRecovery.value = false
        client.sendPause({
            commandId,
            currentIndex: controlContext.currentIndex,
            positionSeconds: queued.positionSeconds,
            version: controlContext.version,
        })
        return true
    }

    const flushQueuedPlayIntent = () => {
        const queued = queuedPlayIntent.value
        if (!queued || !playbackSyncClient.value) {
            return
        }

        queuedPlayIntent.value = null
        awaitingSyncRecovery.value = false
        const controlContext = getCurrentQueueControlContext()
        if (!controlContext) {
            return
        }
        playbackSyncClient.value.sendPlay({
            commandId: nextCommandId('play'),
            currentIndex: controlContext.currentIndex,
            positionSeconds: queued.positionSeconds,
            version: controlContext.version,
        })
    }

    const handleClientReady = () => {
        if (flushQueuedSystemPauseIntent()) {
            return
        }

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

    const handleLoadAudioSource = async (payload: LoadAudioSourcePayload) => {
        if (!playbackSyncClient.value) {
            return
        }

        preloadedTrackForCommand.value = null

        const shellTrack = createTrackShell(payload.recordingId)
        currentTrack.value = cloneTrack(shellTrack)
        cacheTrack(shellTrack)
        void hydrateTrackMetadata(shellTrack)

        const track = (await resolveTrackForPlayback(shellTrack)) ?? shellTrack
        currentTrack.value = cloneTrack(track)
        cacheTrack(track)

        if (!track.src || track.mediaFileId === undefined) {
            error.value = PLAYBACK_ERROR_MESSAGE
            return
        }

        const buffer = await ensureTrackBuffer(track)
        if (!buffer || track.mediaFileId === undefined) {
            return
        }

        preloadedTrackForCommand.value = { commandId: payload.commandId, track }

        playbackSyncClient.value.sendAudioSourceLoaded({
            commandId: payload.commandId,
            currentIndex: payload.currentIndex,
            recordingId: payload.recordingId,
        })
    }

    const handleSnapshot = async (payload: SnapshotPayload) => {
        awaitingSyncRecovery.value = false
        const snapshotVersion = payload.state.version
        const snapshotCurrentIndex = payload.state.currentIndex
        latestSnapshotReceivedAtMs.value = nowClientMs()
        latestSnapshot.value = payload.state
        lastAppliedVersion.value = Math.max(lastAppliedVersion.value, snapshotVersion)
        if ('queue' in payload && payload.queue) {
            applyQueueSnapshot(payload.queue)
        }
        syncQueuePlaybackState(payload.state)

        const snapshotRecordingId = getQueueRecordingId(payload.state.currentIndex)
        const queuedSystemPause = queuedSystemPauseIntent.value
        const retainsQueuedSystemPause =
            queuedSystemPause !== null &&
            payload.state.status === 'PLAYING' &&
            queuedSystemPauseMatches(queuedSystemPause, snapshotCurrentIndex, snapshotRecordingId)
        if (queuedSystemPause && !retainsQueuedSystemPause) {
            queuedSystemPauseIntent.value = null
        }
        if (retainsQueuedSystemPause && queuedSystemPause.conflictRecoveryAttempted) {
            flushQueuedSystemPauseIntent()
        }
        if (isNil(snapshotCurrentIndex) || isNil(snapshotRecordingId)) {
            if (applyNextSnapshotPlaybackState) {
                applyNextSnapshotPlaybackState = false
                applyPausedState(null, 0)
                return
            }
            if (!isPlaying.value) {
                applyPausedState(null, 0)
            }
            return
        }

        const shellTrack = createTrackShell(snapshotRecordingId)
        void hydrateTrackMetadata(shellTrack)
        const track = (await resolveTrackForPlayback(shellTrack)) ?? shellTrack

        if (
            latestSnapshot.value?.version !== snapshotVersion ||
            latestSnapshot.value.currentIndex !== snapshotCurrentIndex
        ) {
            return
        }

        const shouldApplyPlaybackState = applyNextSnapshotPlaybackState
        applyNextSnapshotPlaybackState = false

        cacheTrack(track)
        currentTrack.value = cloneTrack(track)

        let recoveredPosition = payload.state.positionSeconds
        if (retainsQueuedSystemPause) {
            recoveredPosition = queuedSystemPause.positionSeconds
        } else if (payload.state.status === 'PLAYING') {
            recoveredPosition +=
                Math.max(0, payload.serverNowMs - payload.state.serverTimeToExecuteMs) / 1_000
        }
        updatePausedTime(recoveredPosition)

        if (shouldApplyPlaybackState) {
            if (payload.state.status === 'PAUSED') {
                applyPausedState(track, recoveredPosition)
            } else {
                await schedulePlayExecution(
                    {
                        commandId: nextCommandId('snapshot-recovery'),
                        serverTimeToExecuteMs: payload.serverNowMs,
                        scheduledAction: {
                            action: 'PLAY',
                            status: 'PLAYING',
                            currentIndex: snapshotCurrentIndex,
                            positionSeconds: recoveredPosition,
                            version: snapshotVersion,
                        },
                        skipLateCompensation: true,
                    },
                    track,
                    snapshotVersion,
                )
            }
        }

        if (isSameBufferTrack(track)) {
            duration.value = currentBuffer.value!.duration
        }
    }

    const handleQueueChange = (payload: QueueChangePayload) => {
        applyQueueSnapshot(payload.queue)
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
            currentIndex: scheduledAction.currentIndex,
            positionSeconds: scheduledAction.positionSeconds,
            serverTimeToExecuteMs: payload.serverTimeToExecuteMs,
            version: scheduledAction.version,
            updatedAtMs: nowClientMs(),
        }
        syncQueuePlaybackState(latestSnapshot.value)

        const actionToken = scheduledAction.version
        const scheduledRecordingId =
            getQueueRecordingId(scheduledAction.currentIndex) ?? scheduledAction.currentIndex
        const queuedSystemPause = queuedSystemPauseIntent.value
        if (queuedSystemPause) {
            const matchesQueuedPause = queuedSystemPauseMatches(
                queuedSystemPause,
                scheduledAction.currentIndex,
                scheduledRecordingId,
            )
            if (scheduledAction.action === 'PAUSE' && matchesQueuedPause) {
                queuedSystemPauseIntent.value = null
            } else if (matchesQueuedPause) {
                flushQueuedSystemPauseIntent()
                return
            } else {
                queuedSystemPauseIntent.value = null
            }
        }

        const cached = preloadedTrackForCommand.value
        const shellTrack =
            scheduledRecordingId === null ? null : createTrackShell(scheduledRecordingId)
        let track: AudioTrack | null
        if (cached && cached.commandId === payload.commandId) {
            track = cached.track
            preloadedTrackForCommand.value = null
        } else {
            if (shellTrack) {
                void hydrateTrackMetadata(shellTrack)
            }
            track = shellTrack ? await resolveTrackForPlayback(shellTrack) : null
        }

        if (scheduledAction.action === 'PAUSE') {
            schedulePauseState(
                track ?? shellTrack,
                scheduledAction.positionSeconds,
                payload.serverTimeToExecuteMs,
            )
            return
        }

        if (scheduledAction.action === 'SEEK' && scheduledAction.status === 'PAUSED') {
            schedulePauseState(
                track ?? shellTrack,
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
        if (isIndependentPlaybackMode()) {
            return
        }

        const receivedAtMs = nowClientMs()
        switch (message.type) {
            case 'SNAPSHOT':
                await handleSnapshot(message.payload)
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
            case 'ROOM_EVENT_QUEUE_CHANGE':
                handleQueueChange(message.payload)
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
                if (message.payload.code === 'VERSION_CONFLICT') {
                    const queuedSystemPause = queuedSystemPauseIntent.value
                    applyNextSnapshotPlaybackState =
                        queuedSystemPause?.conflictRecoveryAttempted === true
                    queuedSystemPauseIntent.value =
                        queuedSystemPause && !queuedSystemPause.conflictRecoveryAttempted
                            ? {
                                  ...queuedSystemPause,
                                  commandId: undefined,
                                  conflictRecoveryAttempted: true,
                              }
                            : null
                    requestSyncRecovery()
                }
                break
            default:
                break
        }
    }

    const updateClientState = (phase: PlaybackSyncClientPhase, offsetMs: number, rttMs: number) => {
        const previousPhase = clientPhase.value
        clientPhase.value = phase
        const queuedSystemPause = queuedSystemPauseIntent.value
        if (phase !== 'ready' && queuedSystemPause?.commandId) {
            queuedSystemPauseIntent.value = { ...queuedSystemPause, commandId: undefined }
        }
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
        queuedSystemPauseIntent.value = null
        queuedPlayIntent.value = {
            track: cloneTrack(track),
            positionSeconds,
        }
        isPlaying.value = false
    }

    const sendPlayCommand = (track: AudioTrack, positionSeconds: number) => {
        const controlContext = getCurrentQueueControlContext()
        if (!controlContext) {
            return
        }
        primeTrackState(track, positionSeconds)
        queuedPlayIntent.value = null
        queuedSystemPauseIntent.value = null
        awaitingSyncRecovery.value = false
        ensureSyncClient().sendPlay({
            commandId: nextCommandId('play'),
            currentIndex: controlContext.currentIndex,
            positionSeconds,
            version: controlContext.version,
        })
    }

    const queueSystemPause = (intent: QueuedSystemPauseIntent) => {
        queuedPlayIntent.value = null
        queuedSystemPauseIntent.value = intent
        flushQueuedSystemPauseIntent()
    }

    const resetSyncRecoveryState = () => {
        applyNextSnapshotPlaybackState = false
    }

    return {
        nextCommandId,
        requestSyncRecovery,
        flushQueuedPlayIntent,
        ensureSyncClient,
        queuePlay,
        queueSystemPause,
        sendPlayCommand,
        resetSyncRecoveryState,
    }
}
