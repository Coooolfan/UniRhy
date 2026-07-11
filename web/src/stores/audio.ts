import { computed, ref, shallowRef, watch } from 'vue'
import { defineStore } from 'pinia'
import { api } from '@/ApiInstance'
import { useClientPreferencesStore } from '@/stores/clientPreferences'
import { useUserStore } from '@/stores/user'
import type {
    PlaybackSyncClient,
    PlaybackSyncClientDiagnosticsSnapshot,
    PlaybackSyncClientPhase,
} from '@/services/playbackSyncClient'
import type {
    DeviceChangePayload,
    LoadAudioSourcePayload,
    PlaybackStrategy,
    PlaybackSyncStatePayload,
    ScheduledActionPayload,
    StopStrategy,
} from '@/services/playbackSyncProtocol'
import {
    type AudioSyncState,
    type AudioTrack,
    type PlaybackSyncDebugSnapshot,
    type PlaybackSyncLocalExecutionSnapshot,
    type TimestampedPayload,
    cloneTrack,
    clampTime,
    createEmptyQueue,
    isSameTrackRef,
    normalizeVolume,
    persistVolume,
    readSavedVolume,
} from '@/stores/audioShared'
import { useAudioTrackCatalog } from '@/stores/audioTrackCatalog'
import { useAudioLocalQueue } from '@/stores/audioLocalQueue'
import { useAudioEngine, type PlaybackActivationGuard } from '@/stores/audioEngine'
import {
    usePlaybackSyncSession,
    type QueuedPlayIntent,
    type QueuedSystemPauseIntent,
} from '@/stores/audioSyncSession'
import { nowClientMs } from '@/utils/time'

export type {
    AudioQueueEntry,
    AudioSyncState,
    AudioTrack,
    PlaybackSyncDebugSnapshot,
    PlaybackSyncLocalExecutionSnapshot,
    TimestampedPayload,
} from '@/stores/audioShared'

const noop = () => undefined
const QUEUE_VERSION_CONFLICT_DETAIL = 'Queue version conflict'

const isRecord = (value: unknown): value is Record<string, unknown> =>
    typeof value === 'object' && value !== null

const isQueueVersionConflict = (value: unknown) => {
    if (!isRecord(value)) {
        return false
    }

    const status = value.status ?? value.statusCode
    const detail = value.detail ?? value.message
    return status === 409 && detail === QUEUE_VERSION_CONFLICT_DETAIL
}

export const useAudioStore = defineStore('audio', () => {
    // ── 共享响应式状态（各分层通过依赖注入读写）─────────────────────────────
    const currentTrack = ref<AudioTrack | null>(null)
    const currentQueue = ref(createEmptyQueue())
    const isPlaying = ref(false)
    const isPlayerHidden = ref(false)
    const currentTime = ref(0)
    const duration = ref(0)
    const volume = ref(readSavedVolume())
    const isLoading = ref(false)
    const error = ref<string | null>(null)

    const currentBuffer = shallowRef<AudioBuffer | null>(null)
    const currentBufferTrack = shallowRef<AudioTrack | null>(null)
    const currentBufferFileSizeBytes = ref<number | null>(null)
    const currentBufferContentType = ref<string | null>(null)

    const playbackSyncClient = shallowRef<PlaybackSyncClient | null>(null)
    const clientPhase = ref<PlaybackSyncClientPhase>('connecting')
    const clockOffsetMs = ref(0)
    const roundTripEstimateMs = ref(0)
    const latestSnapshot = ref<PlaybackSyncStatePayload | null>(null)
    const latestSnapshotReceivedAtMs = ref<number | null>(null)
    const lastAppliedVersion = ref(0)
    const queuedPlayIntent = ref<QueuedPlayIntent | null>(null)
    const queuedSystemPauseIntent = ref<QueuedSystemPauseIntent | null>(null)
    const awaitingSyncRecovery = ref(false)
    const audioUnlockRequired = ref(false)
    const clientDiagnostics = shallowRef<PlaybackSyncClientDiagnosticsSnapshot | null>(null)
    const lastScheduledAction = shallowRef<TimestampedPayload<ScheduledActionPayload> | null>(null)
    const lastLoadAudioSource = shallowRef<TimestampedPayload<LoadAudioSourcePayload> | null>(null)
    const lastDeviceChange = shallowRef<TimestampedPayload<DeviceChangePayload> | null>(null)
    const lastLocalExecution = shallowRef<PlaybackSyncLocalExecutionSnapshot | null>(null)
    const preloadedTrackForCommand = shallowRef<{
        commandId: string
        track: AudioTrack
    } | null>(null)

    // ── 曲目目录层 ─────────────────────────────────────────────────────────
    const {
        cacheTrack,
        createTrackFromQueueItem,
        applyQueueSnapshot,
        queueRecordingIdsEqual,
        rememberHydratedTrack,
        createTrackShell,
        resolveTrackForPlayback,
        hydrateTrackMetadata,
        resetTrackCatalog,
        invalidateCachedTrackSources,
    } = useAudioTrackCatalog({
        currentTrack,
        currentQueue,
        currentBuffer,
        currentBufferTrack,
        duration,
    })

    const userStore = useUserStore()
    const clientPreferencesStore = useClientPreferencesStore()
    const isIndependentPlaybackMode = computed(
        () => clientPreferencesStore.isIndependentPlaybackMode,
    )

    const getEstimatedServerNowMs = () => {
        return nowClientMs() + clockOffsetMs.value
    }

    // 引擎在曲目自然结束时回调此处触发自动续播；playNext 定义在后，用可变持有者转发
    let onRequestPlayNext: () => void = noop

    // ── 本地队列层 ─────────────────────────────────────────────────────────
    const queue = useAudioLocalQueue({
        currentQueue,
        currentTime,
        currentBuffer,
        currentBufferTrack,
        latestSnapshot,
        createTrackFromQueueItem,
        applyQueueSnapshot,
        rememberHydratedTrack,
    })
    const {
        currentQueueEntry,
        currentQueueIndex,
        queueEntries,
        syncQueuePlaybackState,
        applyApiQueueSnapshot,
        getCurrentQueueVersion,
        nextLocalQueueVersion,
        updateLocalQueuePlaybackState,
        getQueueRecordingId,
        getCurrentQueueControlContext,
        createLocalQueueItem,
        updateLocalQueueCurrentIndex,
        applyLocalQueue,
        updateLocalCurrentQueueItemDuration,
    } = queue

    // ── Web Audio 引擎层 ───────────────────────────────────────────────────
    const engine = useAudioEngine({
        currentTrack,
        currentQueue,
        isPlaying,
        currentTime,
        duration,
        error,
        isLoading,
        currentBuffer,
        currentBufferTrack,
        currentBufferFileSizeBytes,
        currentBufferContentType,
        lastAppliedVersion,
        audioUnlockRequired,
        lastLocalExecution,
        cacheTrack,
        updateLocalQueuePlaybackState,
        updateLocalCurrentQueueItemDuration,
        rememberHydratedTrack,
        getEstimatedServerNowMs,
        requestPlayNext: () => {
            onRequestPlayNext()
        },
    })
    const {
        activeLoad,
        setPlaybackActivationGuard,
        ensureAudioContextResumed,
        getCurrentPlaybackTime,
        isSameBufferTrack,
        ensureTrackBuffer,
        updatePausedTime,
        clearTrackState,
        primeTrackState,
        applyPausedState,
        schedulePauseState,
        schedulePlayExecution,
        startLocalPlayback,
        pauseLocalPlayback,
        seekLocalPlayback,
        destroyAudioContext,
        applyVolumeToGain,
    } = engine
    engine.setInitialVolume(volume.value)
    engine.setIndependentPlaybackModeGetter(() => isIndependentPlaybackMode.value)

    // ── 同步会话层 ─────────────────────────────────────────────────────────
    const sync = usePlaybackSyncSession({
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
        isIndependentPlaybackMode: () => isIndependentPlaybackMode.value,
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
    })
    const {
        nextCommandId,
        requestSyncRecovery,
        flushQueuedPlayIntent,
        ensureSyncClient,
        queuePlay,
        queueSystemPause,
        sendPlayCommand,
        resetSyncRecoveryState,
    } = sync

    const executeRemoteQueueMutation = async <T>(mutation: () => Promise<T>) => {
        try {
            return await mutation()
        } catch (mutationError: unknown) {
            if (!isQueueVersionConflict(mutationError)) {
                throw mutationError
            }

            requestSyncRecovery()
            return null
        }
    }

    watch(
        () => userStore.preferredAssetFormat,
        (nextFormat, prevFormat) => {
            if (prevFormat === undefined || nextFormat === prevFormat) {
                return
            }
            invalidateCachedTrackSources()
        },
    )

    // ── 派生状态 ───────────────────────────────────────────────────────────
    const effectiveSyncState = computed<AudioSyncState>(() => {
        if (isIndependentPlaybackMode.value) {
            return 'independent'
        }
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
            case 'independent':
                return '独立播放'
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

    const canSendRealtimeControl = computed(
        () => isIndependentPlaybackMode.value || effectiveSyncState.value === 'ready',
    )

    const canNavigateQueue = computed(() => {
        return currentQueue.value.items.length > 0 && canSendRealtimeControl.value
    })

    const canPlayPrevious = computed(() => {
        return canNavigateQueue.value && currentQueueIndex.value > 0
    })

    const canPlayNext = computed(() => {
        return (
            canNavigateQueue.value && currentQueueIndex.value < currentQueue.value.items.length - 1
        )
    })

    const playbackStrategy = computed(() => currentQueue.value.playbackStrategy)
    const stopStrategy = computed(() => currentQueue.value.stopStrategy)

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
                          mediaFileId: bufferTrack.mediaFileId ?? null,
                          duration: currentBuffer.value.duration,
                          sampleRate: currentBuffer.value.sampleRate,
                          numberOfChannels: currentBuffer.value.numberOfChannels,
                          fileSizeBytes: currentBufferFileSizeBytes.value,
                          contentType: currentBufferContentType.value,
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

    // ── 独立模式的本地队列导航 ─────────────────────────────────────────────
    const getLocalQueueTrack = (currentIndex: number) => {
        const item = currentQueue.value.items[currentIndex]
        if (!item) {
            return null
        }
        return createTrackFromQueueItem(item)
    }

    const playLocalQueueIndex = async (currentIndex: number, positionSeconds = 0) => {
        const track = getLocalQueueTrack(currentIndex)
        if (!track) {
            return
        }

        updateLocalQueueCurrentIndex(currentIndex)
        await startLocalPlayback(track, positionSeconds)
    }

    const getNextLocalQueueIndex = (direction: -1 | 1) => {
        const {
            items,
            currentIndex,
            playbackStrategy: currentPlaybackStrategy,
        } = currentQueue.value
        if (items.length === 0) {
            return -1
        }
        if (items.length === 1) {
            return 0
        }

        if (currentPlaybackStrategy === 'SINGLE') {
            return currentIndex
        }

        if (direction > 0 && currentPlaybackStrategy === 'SHUFFLE') {
            const availableIndexes = items
                .map((_, index) => index)
                .filter((index) => index !== currentIndex)
            return availableIndexes[Math.floor(Math.random() * availableIndexes.length)] ?? 0
        }

        return (currentIndex + direction + items.length) % items.length
    }

    const requestPlay = async (
        track: AudioTrack,
        positionSeconds: number,
        options: {
            toggleIfSameTrack?: boolean
        } = {},
    ) => {
        const toggleIfSameTrack = options.toggleIfSameTrack ?? false
        rememberHydratedTrack(track)

        const isSameTrack = isSameTrackRef(currentTrack.value, track)
        if (isIndependentPlaybackMode.value) {
            if (toggleIfSameTrack && isSameTrack && isPlaying.value) {
                pauseLocalPlayback()
                return
            }
            await startLocalPlayback(track, positionSeconds)
            return
        }

        if (toggleIfSameTrack && isSameTrack && isPlaying.value) {
            const controlContext = getCurrentQueueControlContext()
            if (!controlContext) {
                return
            }
            ensureSyncClient().sendPause({
                commandId: nextCommandId('pause'),
                currentIndex: controlContext.currentIndex,
                positionSeconds: currentTime.value,
                version: controlContext.version,
            })
            return
        }

        const client = ensureSyncClient()
        client.connect()

        if (effectiveSyncState.value === 'audio_locked') {
            queuePlay(track, positionSeconds)
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
            queuePlay(track, positionSeconds)
            return
        }

        sendPlayCommand(track, positionSeconds)
    }

    // ── 公共播放控制 ───────────────────────────────────────────────────────
    async function replaceQueueAndPlay(tracks: AudioTrack[], currentIndex: number) {
        if (tracks.length === 0 || currentIndex < 0 || currentIndex >= tracks.length) {
            return
        }

        tracks.forEach((track) => {
            rememberHydratedTrack(track)
        })
        const targetTrack = tracks[currentIndex]
        if (!targetTrack) {
            return
        }

        if (isIndependentPlaybackMode.value) {
            applyLocalQueue(tracks, currentIndex)
            await requestPlay(targetTrack, 0, { toggleIfSameTrack: true })
            return
        }

        const recordingIds = tracks.map((track) => track.id)

        if (queueRecordingIdsEqual(recordingIds)) {
            const targetQueueEntry = currentQueue.value.items[currentIndex]
            if (!targetQueueEntry) {
                return
            }
            if (currentQueue.value.currentIndex !== currentIndex) {
                const queueSnapshot = await executeRemoteQueueMutation(() =>
                    api.playbackQueueController.setCurrentIndex({
                        body: { currentIndex, version: getCurrentQueueVersion() },
                    }),
                )
                if (!queueSnapshot) {
                    return
                }
                applyApiQueueSnapshot(queueSnapshot)
                await requestPlay(targetTrack, 0)
                return
            }

            if (isSameTrackRef(currentTrack.value, targetTrack)) {
                if (isPlaying.value) {
                    const controlContext = getCurrentQueueControlContext()
                    if (!controlContext) {
                        return
                    }
                    ensureSyncClient().sendPause({
                        commandId: nextCommandId('pause'),
                        currentIndex: controlContext.currentIndex,
                        positionSeconds: currentTime.value,
                        version: controlContext.version,
                    })
                } else {
                    await requestPlay(targetTrack, currentTime.value)
                }
                return
            }
        }

        const queueSnapshot = await executeRemoteQueueMutation(() =>
            api.playbackQueueController.replaceCurrentQueue({
                body: {
                    recordingIds,
                    currentIndex,
                    version: getCurrentQueueVersion(),
                },
            }),
        )
        if (!queueSnapshot) {
            return
        }
        applyApiQueueSnapshot(queueSnapshot)
        await requestPlay(targetTrack, 0)
    }

    async function appendToQueue(tracks: AudioTrack[]) {
        if (tracks.length === 0) {
            return
        }

        tracks.forEach((track) => {
            rememberHydratedTrack(track)
        })

        if (isIndependentPlaybackMode.value) {
            currentQueue.value = {
                ...currentQueue.value,
                items: [
                    ...currentQueue.value.items,
                    ...tracks.map((track) => createLocalQueueItem(track)),
                ],
                recordingIds: [
                    ...currentQueue.value.recordingIds,
                    ...tracks.map((track) => track.id),
                ],
                version: nextLocalQueueVersion(),
                updatedAtMs: nowClientMs(),
            }
            return
        }

        const queueSnapshot = await executeRemoteQueueMutation(() =>
            api.playbackQueueController.appendToCurrentQueue({
                body: {
                    recordingIds: tracks.map((track) => track.id),
                    version: getCurrentQueueVersion(),
                },
            }),
        )
        if (!queueSnapshot) {
            return
        }
        applyApiQueueSnapshot(queueSnapshot)
    }

    async function reorderQueue(recordingIds: number[], currentIndex: number) {
        if (recordingIds.length === 0 || currentIndex < 0 || currentIndex >= recordingIds.length) {
            return
        }

        if (isIndependentPlaybackMode.value) {
            const itemByRecordingId = new Map(
                currentQueue.value.items.map((item) => [item.recordingId, item]),
            )
            currentQueue.value = {
                ...currentQueue.value,
                items: recordingIds
                    .map((recordingId) => itemByRecordingId.get(recordingId))
                    .filter((item): item is NonNullable<typeof item> => item !== undefined),
                recordingIds,
                currentIndex,
                version: nextLocalQueueVersion(),
                updatedAtMs: nowClientMs(),
            }
            return
        }

        const queueSnapshot = await executeRemoteQueueMutation(() =>
            api.playbackQueueController.reorderCurrentQueue({
                body: { recordingIds, currentIndex, version: getCurrentQueueVersion() },
            }),
        )
        if (!queueSnapshot) {
            return
        }
        applyApiQueueSnapshot(queueSnapshot)
    }

    async function playQueueEntry(currentIndex: number) {
        if (isIndependentPlaybackMode.value) {
            await playLocalQueueIndex(currentIndex)
            return
        }

        const queueSnapshot = await executeRemoteQueueMutation(() =>
            api.playbackQueueController.setCurrentIndex({
                body: { currentIndex, version: getCurrentQueueVersion() },
            }),
        )
        if (!queueSnapshot) {
            return
        }
        const normalizedQueue = applyApiQueueSnapshot(queueSnapshot)
        const currentItem = normalizedQueue.items[normalizedQueue.currentIndex]
        if (!currentItem) {
            return
        }

        await requestPlay(createTrackFromQueueItem(currentItem), 0)
    }

    async function playNext() {
        if (currentQueue.value.items.length === 0) {
            return
        }

        if (isIndependentPlaybackMode.value) {
            const nextIndex = getNextLocalQueueIndex(1)
            if (nextIndex >= 0) {
                await playLocalQueueIndex(nextIndex)
            }
            return
        }

        const queueSnapshot = await executeRemoteQueueMutation(() =>
            api.playbackQueueController.playNextInCurrentQueue({
                body: { version: getCurrentQueueVersion() },
            }),
        )
        if (!queueSnapshot) {
            return
        }
        applyApiQueueSnapshot(queueSnapshot)
    }

    onRequestPlayNext = () => {
        void playNext()
    }

    async function playPrevious() {
        if (currentQueue.value.items.length === 0) {
            return
        }

        if (isIndependentPlaybackMode.value) {
            const nextIndex = getNextLocalQueueIndex(-1)
            if (nextIndex >= 0) {
                await playLocalQueueIndex(nextIndex)
            }
            return
        }

        const queueSnapshot = await executeRemoteQueueMutation(() =>
            api.playbackQueueController.playPreviousInCurrentQueue({
                body: { version: getCurrentQueueVersion() },
            }),
        )
        if (!queueSnapshot) {
            return
        }
        applyApiQueueSnapshot(queueSnapshot)
    }

    async function updateQueueStrategies(
        nextPlaybackStrategy: PlaybackStrategy,
        nextStopStrategy: StopStrategy,
    ) {
        if (isIndependentPlaybackMode.value) {
            currentQueue.value = {
                ...currentQueue.value,
                playbackStrategy: nextPlaybackStrategy,
                stopStrategy: nextStopStrategy,
                version: nextLocalQueueVersion(),
                updatedAtMs: nowClientMs(),
            }
            return
        }

        const queueSnapshot = await executeRemoteQueueMutation(() =>
            api.playbackQueueController.updateCurrentQueueStrategy({
                body: {
                    playbackStrategy: nextPlaybackStrategy,
                    stopStrategy: nextStopStrategy,
                    version: getCurrentQueueVersion(),
                },
            }),
        )
        if (!queueSnapshot) {
            return
        }
        applyApiQueueSnapshot(queueSnapshot)
    }

    async function removeQueueEntry(index: number) {
        if (isIndependentPlaybackMode.value) {
            const nextItems = [...currentQueue.value.items]
            if (index < 0 || index >= nextItems.length) {
                return
            }

            const removedCurrentEntry = index === currentQueue.value.currentIndex
            nextItems.splice(index, 1)
            let nextCurrentIndex = currentQueue.value.currentIndex
            if (nextItems.length === 0) {
                currentQueue.value = createEmptyQueue()
                clearTrackState()
                return
            }
            if (index < nextCurrentIndex) {
                nextCurrentIndex -= 1
            } else if (index === nextCurrentIndex) {
                nextCurrentIndex = Math.min(nextCurrentIndex, nextItems.length - 1)
            }

            currentQueue.value = {
                ...currentQueue.value,
                items: nextItems,
                recordingIds: nextItems.map((item) => item.recordingId),
                currentIndex: nextCurrentIndex,
                version: nextLocalQueueVersion(),
                updatedAtMs: nowClientMs(),
            }

            if (removedCurrentEntry) {
                await playLocalQueueIndex(nextCurrentIndex)
            }
            return
        }

        const queueSnapshot = await executeRemoteQueueMutation(() =>
            api.playbackQueueController.removeCurrentQueueEntry({
                body: { index, version: getCurrentQueueVersion() },
            }),
        )
        if (!queueSnapshot) {
            return
        }
        applyApiQueueSnapshot(queueSnapshot)
    }

    async function clearQueue() {
        if (isIndependentPlaybackMode.value) {
            currentQueue.value = createEmptyQueue()
            clearTrackState()
            return
        }

        const queueSnapshot = await executeRemoteQueueMutation(() =>
            api.playbackQueueController.clearCurrentQueue({
                body: { version: getCurrentQueueVersion() },
            }),
        )
        if (!queueSnapshot) {
            return
        }
        applyApiQueueSnapshot(queueSnapshot)
    }

    function pause() {
        if (isIndependentPlaybackMode.value) {
            pauseLocalPlayback()
            return
        }

        const controlContext = getCurrentQueueControlContext()
        if (!currentTrack.value || !canSendRealtimeControl.value || !controlContext) {
            return
        }

        ensureSyncClient().sendPause({
            commandId: nextCommandId('pause'),
            currentIndex: controlContext.currentIndex,
            positionSeconds: currentTime.value,
            version: controlContext.version,
        })
    }

    function pauseFromSystem() {
        const track = currentTrack.value
        if (!track) {
            return
        }

        if (isIndependentPlaybackMode.value) {
            pauseLocalPlayback()
            return
        }

        const pausedAt = getCurrentPlaybackTime()
        applyPausedState(track, pausedAt)

        const controlContext = getCurrentQueueControlContext()
        if (!controlContext) {
            return
        }

        queueSystemPause({
            recordingId: track.id,
            currentIndex: controlContext.currentIndex,
            positionSeconds: pausedAt,
        })
    }

    function pauseForPlaybackProtection() {
        if (!currentTrack.value || !isPlaying.value) {
            return
        }

        const pausedAt = getCurrentPlaybackTime()
        applyPausedState(currentTrack.value, pausedAt)
        if (isIndependentPlaybackMode.value) {
            updateLocalQueuePlaybackState('PAUSED', pausedAt)
        }
    }

    function setPlaybackProtectionGuard(guard: PlaybackActivationGuard | null) {
        setPlaybackActivationGuard(guard)
    }

    function recoverPlaybackAfterForeground() {
        if (isIndependentPlaybackMode.value || !playbackSyncClient.value) {
            return
        }
        requestSyncRecovery()
    }

    async function play(track: AudioTrack) {
        await replaceQueueAndPlay([track], 0)
    }

    async function resume() {
        if (!currentTrack.value) {
            return
        }

        if (isIndependentPlaybackMode.value) {
            await startLocalPlayback(currentTrack.value, currentTime.value)
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
        queuedSystemPauseIntent.value = null
        awaitingSyncRecovery.value = false

        if (isIndependentPlaybackMode.value) {
            latestSnapshot.value = null
            latestSnapshotReceivedAtMs.value = null
            currentQueue.value = createEmptyQueue()
            clearTrackState()
            return
        }

        if (!canSendRealtimeControl.value) {
            latestSnapshot.value = null
            latestSnapshotReceivedAtMs.value = null
            currentQueue.value = createEmptyQueue()
            clearTrackState()
            return
        }

        const controlContext = getCurrentQueueControlContext()
        if (!controlContext) {
            return
        }
        ensureSyncClient().sendPause({
            commandId: nextCommandId('stop'),
            currentIndex: controlContext.currentIndex,
            positionSeconds: 0,
            version: controlContext.version,
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
        if (isIndependentPlaybackMode.value) {
            seekLocalPlayback(time)
            return
        }

        const controlContext = getCurrentQueueControlContext()
        if (!currentTrack.value || !canSendRealtimeControl.value || !controlContext) {
            return
        }

        const bufferDuration = currentBuffer.value?.duration ?? duration.value
        const nextTime = clampTime(time, bufferDuration)
        ensureSyncClient().sendSeek({
            commandId: nextCommandId('seek'),
            currentIndex: controlContext.currentIndex,
            positionSeconds: nextTime,
            version: controlContext.version,
        })
    }

    function setVolume(vol: number) {
        const normalizedVolume = normalizeVolume(vol)
        volume.value = normalizedVolume
        persistVolume(normalizedVolume)
        applyVolumeToGain(normalizedVolume)
    }

    function connectPlaybackSync() {
        if (isIndependentPlaybackMode.value) {
            return
        }

        const client = ensureSyncClient()
        client.connect()
    }

    function disconnectPlaybackSync(options: { preservePlayback?: boolean } = {}) {
        const preservePlayback = options.preservePlayback ?? false
        resetSyncRecoveryState()
        playbackSyncClient.value?.disconnect()
        playbackSyncClient.value = null
        clientDiagnostics.value = null
        queuedPlayIntent.value = null
        queuedSystemPauseIntent.value = null
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

        if (preservePlayback) {
            return
        }

        isPlayerHidden.value = false
        currentQueue.value = createEmptyQueue()
        resetTrackCatalog()
        clearTrackState()
        destroyAudioContext()
    }

    watch(isIndependentPlaybackMode, (isIndependent) => {
        if (isIndependent) {
            disconnectPlaybackSync({ preservePlayback: true })
        }
    })

    return {
        currentTrack,
        currentQueue,
        currentQueueEntry,
        currentQueueIndex,
        queueEntries,
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
        canNavigateQueue,
        canPlayPrevious,
        canPlayNext,
        playbackStrategy,
        stopStrategy,
        play,
        replaceQueueAndPlay,
        appendToQueue,
        reorderQueue,
        playQueueEntry,
        playNext,
        playPrevious,
        updateQueueStrategies,
        removeQueueEntry,
        clearQueue,
        pause,
        pauseFromSystem,
        pauseForPlaybackProtection,
        setPlaybackProtectionGuard,
        recoverPlaybackAfterForeground,
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
