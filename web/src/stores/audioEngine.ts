import { shallowRef, type Ref, type ShallowRef } from 'vue'
import { getAuthToken } from '@/ApiInstance'
import type {
    CurrentQueueDto,
    PlaybackSyncStatePayload,
    ScheduledActionPayload,
} from '@/services/playbackSyncProtocol'
import {
    type AudioTrack,
    type PlaybackSyncLocalExecutionSnapshot,
    PLAYBACK_ERROR_MESSAGE,
    clampTime,
    cloneTrack,
} from '@/stores/audioShared'
import { getPlatformRuntime } from '@/runtime/platform'
import { runtimeFetch } from '@/runtime/http'
import { nowClientMs } from '@/utils/time'

type BufferLoadState = {
    mediaFileId: number
    promise: Promise<AudioBuffer | null>
}

const isSignedMediaUrl = (url: string) => {
    try {
        const params = new URL(url, window.location.href).searchParams
        return params.has('_sig') || params.has('X-Amz-Signature')
    } catch {
        return false
    }
}

const defaultIndependentPlaybackModeGetter = () => false
const defaultPlaybackActivationGuard = () => true
const noopProgressUpdates = () => undefined

export type PlaybackActivationGuard = () => boolean | Promise<boolean>

type UseAudioEngineOptions = {
    currentTrack: Ref<AudioTrack | null>
    currentQueue: Ref<CurrentQueueDto>
    isPlaying: Ref<boolean>
    currentTime: Ref<number>
    duration: Ref<number>
    error: Ref<string | null>
    isLoading: Ref<boolean>
    currentBuffer: ShallowRef<AudioBuffer | null>
    currentBufferTrack: ShallowRef<AudioTrack | null>
    currentBufferFileSizeBytes: Ref<number | null>
    currentBufferContentType: Ref<string | null>
    lastAppliedVersion: Ref<number>
    audioUnlockRequired: Ref<boolean>
    lastLocalExecution: ShallowRef<PlaybackSyncLocalExecutionSnapshot | null>
    cacheTrack: (track: AudioTrack) => void
    updateLocalQueuePlaybackState: (
        playbackStatus?: PlaybackSyncStatePayload['status'],
        positionSeconds?: number,
    ) => void
    updateLocalCurrentQueueItemDuration: (track: AudioTrack, buffer: AudioBuffer) => void
    rememberHydratedTrack: (track: AudioTrack) => void
    getEstimatedServerNowMs: () => number
    requestPlayNext: () => void
}

/**
 * Web Audio 引擎层：封装 AudioContext 生命周期、AudioBufferSourceNode 调度、
 * 缓冲加载与解码、进度动画循环，以及本地（独立模式）与同步调度两种播放执行路径。
 * 仅通过注入的少量回调与上层的队列/同步状态交互。
 */
export const useAudioEngine = (options: UseAudioEngineOptions) => {
    const {
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
        requestPlayNext,
    } = options

    const audioContext = shallowRef<AudioContext | null>(null)
    const gainNode = shallowRef<GainNode | null>(null)
    const sourceNode = shallowRef<AudioBufferSourceNode | null>(null)
    const playbackStartContextTime = shallowRef(0)
    const playbackOffsetSec = shallowRef(0)
    const rafId = shallowRef<number | null>(null)
    const backgroundProgressIntervalId = shallowRef<number | null>(null)
    const activeLoad = shallowRef<BufferLoadState | null>(null)

    const liveSourceNodes = new Set<AudioBufferSourceNode>()
    const ignoredEndedNodes = new WeakSet<AudioBufferSourceNode>()
    const sourceEndedListeners = new WeakMap<AudioBufferSourceNode, EventListener>()

    let scheduledPauseTimer: number | null = null
    let scheduledStopTimer: number | null = null
    let visibilityListenerAttached = false
    let restartProgressUpdates = noopProgressUpdates

    let volumeValue = 0

    // 独立播放模式判定由上层注入：结束回调需据此决定自动续播路径
    let isIndependentPlaybackMode: () => boolean = defaultIndependentPlaybackModeGetter
    let playbackActivationGuard: PlaybackActivationGuard = defaultPlaybackActivationGuard
    const setIndependentPlaybackModeGetter = (getter: () => boolean) => {
        isIndependentPlaybackMode = getter
    }
    const setPlaybackActivationGuard = (guard: PlaybackActivationGuard | null) => {
        playbackActivationGuard = guard ?? defaultPlaybackActivationGuard
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

    const attachVisibilityListener = () => {
        if (visibilityListenerAttached || typeof document === 'undefined') {
            return
        }
        document.addEventListener('visibilitychange', restartProgressUpdates)
        visibilityListenerAttached = true
    }

    const detachVisibilityListener = () => {
        if (!visibilityListenerAttached || typeof document === 'undefined') {
            return
        }
        document.removeEventListener('visibilitychange', restartProgressUpdates)
        visibilityListenerAttached = false
    }

    const stopAnimationLoop = () => {
        if (rafId.value !== null && typeof cancelAnimationFrame !== 'undefined') {
            cancelAnimationFrame(rafId.value)
            rafId.value = null
        }

        if (backgroundProgressIntervalId.value !== null) {
            window.clearInterval(backgroundProgressIntervalId.value)
            backgroundProgressIntervalId.value = null
        }

        detachVisibilityListener()
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

        if (!isPlaying.value || !audioContext.value || !currentBuffer.value) {
            return
        }

        attachVisibilityListener()
        syncCurrentTime()

        if (globalThis.document?.hidden) {
            backgroundProgressIntervalId.value = window.setInterval(() => {
                if (!isPlaying.value || !audioContext.value || !currentBuffer.value) {
                    stopAnimationLoop()
                    return
                }
                syncCurrentTime()
            }, 1_000)
            return
        }

        if (typeof requestAnimationFrame === 'undefined') {
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

        rafId.value = requestAnimationFrame(tick)
    }

    restartProgressUpdates = () => {
        if (isPlaying.value && audioContext.value && currentBuffer.value) {
            startAnimationLoop()
        }
    }

    const ensureAudioContext = () => {
        const AudioContextConstructor = globalThis.window?.AudioContext
        if (AudioContextConstructor === undefined) {
            return null
        }

        if (!audioContext.value) {
            const context = new AudioContextConstructor()
            const gain = context.createGain()
            gain.gain.value = volumeValue
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

    function isSameBufferTrack(track: AudioTrack) {
        return (
            track.mediaFileId !== undefined &&
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

    const handleLocalPlaybackEnded = () => {
        stopAnimationLoop()
        isPlaying.value = false
        playbackStartContextTime.value = 0

        const endedAt = currentBuffer.value?.duration ?? duration.value
        updatePausedTime(endedAt)
        updateLocalQueuePlaybackState('PAUSED', endedAt)

        if (currentQueue.value.playbackStrategy === 'SINGLE') {
            requestPlayNext()
        } else if (
            currentQueue.value.stopStrategy !== 'TRACK' &&
            currentQueue.value.items.length > 1
        ) {
            requestPlayNext()
        }
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

        if (isIndependentPlaybackMode()) {
            handleLocalPlaybackEnded()
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
        currentBufferFileSizeBytes.value = null
        currentBufferContentType.value = null
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

    const primeTrackState = (track: AudioTrack, positionSeconds: number) => {
        cacheTrack(track)
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
        cacheTrack(track)

        if (!track.src || track.mediaFileId === undefined) {
            error.value = PLAYBACK_ERROR_MESSAGE
            return null
        }

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

        const fetchInit: RequestInit = {}
        const token = getPlatformRuntime().platform === 'web' ? null : getAuthToken()
        if (token && !isSignedMediaUrl(track.src)) {
            fetchInit.headers = { 'unirhy-token': token }
        }
        const promise = runtimeFetch(track.src, fetchInit)
            .then(async (response) => {
                if (!response.ok) {
                    throw new Error(`Failed to fetch audio: ${response.status}`)
                }

                const contentType = response.headers.get('content-type')
                const arrayBuffer = await response.arrayBuffer()
                const fileSizeBytes = arrayBuffer.byteLength
                const buffer = await context.decodeAudioData(arrayBuffer)
                if (activeLoad.value !== loadState) {
                    return null
                }

                currentBuffer.value = buffer
                currentBufferTrack.value = cloneTrack(track)
                currentBufferFileSizeBytes.value = fileSizeBytes
                currentBufferContentType.value = contentType
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

    const applyPausedState = (track: AudioTrack | null, positionSeconds: number) => {
        stopAnimationLoop()
        stopAllSourceNodes()
        sourceNode.value = null
        isPlaying.value = false
        playbackStartContextTime.value = 0

        if (track) {
            cacheTrack(track)
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

        if (!(await playbackActivationGuard()) || actionToken !== lastAppliedVersion.value) {
            applyPausedState(track, payload.scheduledAction.positionSeconds)
            return
        }

        const estimatedServerNowMs = getEstimatedServerNowMs()
        const lateSeconds = payload.skipLateCompensation
            ? 0
            : Math.max(0, (estimatedServerNowMs - payload.serverTimeToExecuteMs) / 1_000)
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
            currentIndex: payload.scheduledAction.currentIndex,
            mediaFileId: track.mediaFileId ?? null,
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

    const startLocalPlayback = async (track: AudioTrack, positionSeconds: number) => {
        rememberHydratedTrack(track)
        primeTrackState(track, positionSeconds)

        const buffer = await ensureTrackBuffer(track)
        if (!buffer) {
            updateLocalQueuePlaybackState('PAUSED', positionSeconds)
            return
        }
        updateLocalCurrentQueueItemDuration(track, buffer)

        const context = await ensureAudioContextResumed()
        if (!context) {
            updateLocalQueuePlaybackState('PAUSED', positionSeconds)
            return
        }

        if (!(await playbackActivationGuard())) {
            applyPausedState(track, positionSeconds)
            updateLocalQueuePlaybackState('PAUSED', positionSeconds)
            return
        }

        const nextTime = clampTime(positionSeconds, buffer.duration)
        stopAllSourceNodes()
        const nextNode = createSourceNode(buffer)
        if (!nextNode) {
            error.value = PLAYBACK_ERROR_MESSAGE
            updateLocalQueuePlaybackState('PAUSED', nextTime)
            return
        }

        sourceNode.value = nextNode
        playbackOffsetSec.value = nextTime
        playbackStartContextTime.value = context.currentTime
        currentTime.value = nextTime
        isPlaying.value = true
        nextNode.start(context.currentTime, nextTime)
        updateLocalQueuePlaybackState('PLAYING', nextTime)
        startAnimationLoop()
    }

    const pauseLocalPlayback = () => {
        if (!currentTrack.value) {
            return
        }

        const pausedAt = getCurrentPlaybackTime()
        applyPausedState(currentTrack.value, pausedAt)
        updateLocalQueuePlaybackState('PAUSED', pausedAt)
    }

    const seekLocalPlayback = (time: number) => {
        if (!currentTrack.value) {
            return
        }

        const bufferDuration = currentBuffer.value?.duration ?? duration.value
        const nextTime = clampTime(time, bufferDuration)
        if (!isPlaying.value) {
            updatePausedTime(nextTime)
            updateLocalQueuePlaybackState('PAUSED', nextTime)
            return
        }

        void startLocalPlayback(currentTrack.value, nextTime)
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

    const applyVolumeToGain = (nextVolume: number) => {
        volumeValue = nextVolume
        if (gainNode.value) {
            gainNode.value.gain.value = nextVolume
        }
    }

    return {
        activeLoad,
        setInitialVolume: (value: number) => {
            volumeValue = value
        },
        setIndependentPlaybackModeGetter,
        setPlaybackActivationGuard,
        ensureAudioContextResumed,
        isSameBufferTrack,
        stopAllSourceNodes,
        ensureTrackBuffer,
        updatePausedTime,
        getCurrentPlaybackTime,
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
    }
}
