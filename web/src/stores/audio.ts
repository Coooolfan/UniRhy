import { defineStore } from 'pinia'
import { ref, shallowRef } from 'vue'

export type AudioTrack = {
    id: number
    title: string
    artist: string
    cover?: string
    src: string
    workId?: number
}

const VOLUME_STORAGE_KEY = 'unirhy.audio.volume'
const PLAYBACK_ERROR_MESSAGE = 'Unable to play audio'

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
    const sourceNode = shallowRef<AudioBufferSourceNode | null>(null)
    const gainNode = shallowRef<GainNode | null>(null)
    const playbackStartContextTime = ref(0)
    const playbackOffsetSec = ref(0)
    const rafId = ref<number | null>(null)
    const loadToken = ref(0)
    const isStoppingSource = ref(false)
    const resumeAttemptId = ref(0)
    const sourceEndedListeners = new WeakMap<AudioBufferSourceNode, EventListener>()

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

    const handleResumeFailure = (attemptId: number) => {
        if (resumeAttemptId.value !== attemptId || !isPlaying.value) {
            return
        }

        isPlaying.value = false
        isLoading.value = false
        error.value = PLAYBACK_ERROR_MESSAGE
        playbackStartContextTime.value = 0
        stopAnimationLoop()
        stopSourcePlayback()
    }

    const resumeAudioContext = () => {
        const context = ensureAudioContext()
        if (!context) {
            return
        }

        const attemptId = resumeAttemptId.value + 1
        resumeAttemptId.value = attemptId

        void context.resume().catch(() => {
            handleResumeFailure(attemptId)
        })
    }

    const detachSourceNode = (node: AudioBufferSourceNode | null) => {
        if (!node) {
            return
        }

        isStoppingSource.value = true
        const endedListener = sourceEndedListeners.get(node)
        if (endedListener) {
            node.removeEventListener('ended', endedListener)
            sourceEndedListeners.delete(node)
        }
        if (sourceNode.value === node) {
            sourceNode.value = null
        }

        try {
            node.stop()
        } catch {
            // Source nodes can only be stopped once.
        }

        try {
            node.disconnect()
        } catch {
            // Ignore already-disconnected nodes.
        }

        isStoppingSource.value = false
    }

    const stopSourcePlayback = () => {
        detachSourceNode(sourceNode.value)
    }

    const clearLoadedBuffer = () => {
        stopSourcePlayback()
        currentBuffer.value = null
        duration.value = 0
        playbackStartContextTime.value = 0
        updatePausedTime(0)
    }

    const handlePlaybackEnded = (node: AudioBufferSourceNode) => {
        if (sourceNode.value !== node || isStoppingSource.value) {
            return
        }

        sourceNode.value = null
        stopAnimationLoop()
        isPlaying.value = false
        playbackStartContextTime.value = 0
        playbackOffsetSec.value = 0
        currentTime.value = 0
    }

    const createAndStartSource = (offsetSeconds: number) => {
        const context = ensureAudioContext()
        const buffer = currentBuffer.value
        const gain = gainNode.value
        if (!context || !buffer || !gain) {
            return
        }

        const normalizedOffset =
            offsetSeconds >= buffer.duration && buffer.duration > 0
                ? 0
                : clampTime(offsetSeconds, buffer.duration)

        stopSourcePlayback()

        const node = context.createBufferSource()
        node.buffer = buffer
        node.connect(gain)
        const endedListener: EventListener = () => {
            handlePlaybackEnded(node)
        }
        sourceEndedListeners.set(node, endedListener)
        node.addEventListener('ended', endedListener)

        sourceNode.value = node
        playbackOffsetSec.value = normalizedOffset
        playbackStartContextTime.value = context.currentTime
        currentTime.value = normalizedOffset
        node.start(0, normalizedOffset)
        startAnimationLoop()
    }

    const applyLoadedTrack = (track: AudioTrack, buffer: AudioBuffer, token: number) => {
        if (loadToken.value !== token || currentTrack.value?.id !== track.id) {
            return
        }

        currentBuffer.value = buffer
        duration.value = buffer.duration
        updatePausedTime(playbackOffsetSec.value)
        isLoading.value = false

        if (isPlaying.value) {
            resumeAudioContext()
            createAndStartSource(playbackOffsetSec.value)
        }
    }

    const loadTrackBuffer = async (track: AudioTrack, token: number) => {
        const context = ensureAudioContext()
        if (!context) {
            isLoading.value = false
            return
        }

        const response = await fetch(track.src)
        if (!response.ok) {
            throw new Error(`Failed to fetch audio: ${response.status}`)
        }

        const arrayBuffer = await response.arrayBuffer()
        const decodedBuffer = await context.decodeAudioData(arrayBuffer)
        applyLoadedTrack(track, decodedBuffer, token)
    }

    const startTrackLoad = (track: AudioTrack) => {
        const token = loadToken.value + 1
        loadToken.value = token
        error.value = null
        isLoading.value = true
        clearLoadedBuffer()

        void loadTrackBuffer(track, token).catch(() => {
            if (loadToken.value !== token || currentTrack.value?.id !== track.id) {
                return
            }

            isLoading.value = false
            isPlaying.value = false
            error.value = PLAYBACK_ERROR_MESSAGE
        })
    }

    function play(track: AudioTrack) {
        if (currentTrack.value?.id === track.id) {
            if (isPlaying.value) {
                pause()
            } else {
                resume()
            }
            return
        }

        currentTrack.value = track
        isPlaying.value = true
        currentTime.value = 0
        playbackOffsetSec.value = 0
        duration.value = 0
        error.value = null

        resumeAudioContext()
        startTrackLoad(track)
    }

    function pause() {
        if (!currentTrack.value) {
            return
        }

        syncCurrentTime()
        isPlaying.value = false
        playbackStartContextTime.value = 0
        updatePausedTime(currentTime.value)
        stopAnimationLoop()
        stopSourcePlayback()
    }

    function resume() {
        if (!currentTrack.value) {
            return
        }

        error.value = null
        isPlaying.value = true
        resumeAudioContext()

        if (!currentBuffer.value) {
            if (!isLoading.value) {
                startTrackLoad(currentTrack.value)
            }
            return
        }

        createAndStartSource(playbackOffsetSec.value)
    }

    function stop() {
        resumeAttemptId.value += 1
        loadToken.value += 1
        isPlaying.value = false
        isLoading.value = false
        currentTrack.value = null
        isPlayerHidden.value = false
        error.value = null
        stopAnimationLoop()
        clearLoadedBuffer()
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
        const bufferDuration = currentBuffer.value?.duration ?? duration.value
        const nextTime = clampTime(time, bufferDuration)

        if (!currentTrack.value) {
            updatePausedTime(nextTime)
            return
        }

        updatePausedTime(nextTime)

        if (!currentBuffer.value || !isPlaying.value) {
            return
        }

        if (currentBuffer.value.duration > 0 && nextTime >= currentBuffer.value.duration) {
            pause()
            updatePausedTime(currentBuffer.value.duration)
            return
        }

        resumeAudioContext()
        createAndStartSource(nextTime)
    }

    function setVolume(vol: number) {
        const normalizedVolume = normalizeVolume(vol)
        volume.value = normalizedVolume
        persistVolume(normalizedVolume)

        if (gainNode.value) {
            gainNode.value.gain.value = normalizedVolume
        }
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
        play,
        pause,
        resume,
        stop,
        hidePlayer,
        showPlayer,
        seek,
        setVolume,
    }
})
