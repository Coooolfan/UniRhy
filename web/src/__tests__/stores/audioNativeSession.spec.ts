import { describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import type { CurrentQueueDto } from '@/services/playbackSyncProtocol'
import type { PlaybackSyncClientPhase } from '@/services/playbackSyncClient'
import type { AudioTrack } from '@/stores/audioShared'
import { useAudioNativeSession } from '@/stores/audioNativeSession'

vi.mock('@/runtime/nativePlaybackBridge', () => ({
    configureNativePlayback: vi.fn(),
    connectNativeSync: vi.fn(),
    disconnectNativeSync: vi.fn(),
    getNativePlaybackState: vi.fn(),
    listenForNativePlaybackEvents: vi.fn(),
    nativeLocalPause: vi.fn(),
    nativeLocalPlay: vi.fn(),
    nativeLocalSeek: vi.fn(),
    requestNativePause: vi.fn(),
    requestNativePlay: vi.fn(),
    requestNativeSeek: vi.fn(),
    requestNativeSyncRecovery: vi.fn(),
    setNativeLocalQueue: vi.fn(),
    setNativeVolume: vi.fn(),
}))

vi.mock('@/runtime/platform', () => ({
    getPlatformRuntime: () => ({ apiBaseUrl: 'http://backend.local', platform: 'android' }),
}))

vi.mock('@/ApiInstance', () => ({
    getAuthToken: () => 'test-token',
}))

const createQueue = (version: number): CurrentQueueDto => ({
    items: [
        {
            recordingId: 1001,
            title: 'Track',
            artistLabel: 'Artist',
            durationMs: 180_000,
            mediaFileId: 2001,
        },
    ],
    recordingIds: [1001],
    currentIndex: 0,
    playbackStrategy: 'SEQUENTIAL',
    stopStrategy: 'LIST',
    playbackStatus: 'PAUSED',
    positionMs: 0,
    serverTimeToExecuteMs: 0,
    version,
    updatedAtMs: 0,
})

const createSession = (options: { independent?: boolean } = {}) => {
    const refs = {
        currentTrack: ref<AudioTrack | null>(null),
        currentQueue: ref(createQueue(1)),
        isPlaying: ref(false),
        currentTime: ref(0),
        duration: ref(0),
        isLoading: ref(false),
        error: ref<string | null>(null),
        clientPhase: ref<PlaybackSyncClientPhase>('connecting'),
        clockOffsetMs: ref(0),
        roundTripEstimateMs: ref(0),
        volume: ref(1),
    }
    const applyQueueSnapshot = vi.fn<(queue: CurrentQueueDto) => void>()
    const updateLocalQueueCurrentIndex = vi.fn<(currentIndex: number) => void>()
    const session = useAudioNativeSession({
        ...refs,
        isIndependentPlaybackMode: () => options.independent ?? false,
        applyQueueSnapshot,
        updateLocalQueueCurrentIndex,
    })
    return { session, refs, applyQueueSnapshot, updateLocalQueueCurrentIndex }
}

describe('useAudioNativeSession', () => {
    it('applies sync-state events to phase and clock refs', () => {
        const { session, refs } = createSession()
        session.applyNativeEvent({
            type: 'sync-state',
            seq: 1,
            syncPhase: 'ready',
            clockOffsetMs: 42.5,
            roundTripEstimateMs: 18,
        })

        expect(refs.clientPhase.value).toBe('ready')
        expect(refs.clockOffsetMs.value).toBe(42.5)
        expect(refs.roundTripEstimateMs.value).toBe(18)
    })

    it('drops out-of-order events but accepts a native seq reset', () => {
        const { session, refs } = createSession()
        session.applyNativeEvent({
            type: 'sync-state',
            seq: 10,
            syncPhase: 'ready',
            clockOffsetMs: 1,
            roundTripEstimateMs: 1,
        })
        // 乱序的旧事件被丢弃
        session.applyNativeEvent({
            type: 'sync-state',
            seq: 9,
            syncPhase: 'error',
            clockOffsetMs: 2,
            roundTripEstimateMs: 2,
        })
        expect(refs.clientPhase.value).toBe('ready')

        // 原生进程重启后 seq 归零，低位 seq 应被接受
        session.applyNativeEvent({
            type: 'sync-state',
            seq: 1,
            syncPhase: 'connecting',
            clockOffsetMs: 0,
            roundTripEstimateMs: 0,
        })
        expect(refs.clientPhase.value).toBe('connecting')
    })

    it('applies queue-changed via the shared queue snapshot pipeline in sync mode', () => {
        const { session, applyQueueSnapshot } = createSession()
        const queue = createQueue(7)
        session.applyNativeEvent({ type: 'queue-changed', seq: 1, queue })
        expect(applyQueueSnapshot).toHaveBeenCalledWith(queue)
    })

    it('ignores native queue events in independent mode', () => {
        const { session, applyQueueSnapshot } = createSession({ independent: true })
        session.applyNativeEvent({ type: 'queue-changed', seq: 1, queue: createQueue(7) })
        expect(applyQueueSnapshot).not.toHaveBeenCalled()
    })

    it('applies state-changed fields and follows native index in independent mode', () => {
        const { session, refs, updateLocalQueueCurrentIndex } = createSession({
            independent: true,
        })
        session.applyNativeEvent({
            type: 'state-changed',
            seq: 1,
            isPlaying: true,
            positionSeconds: 12.5,
            durationSeconds: 180,
            isLoading: false,
            error: null,
            currentIndex: 2,
        })

        expect(refs.isPlaying.value).toBe(true)
        expect(refs.currentTime.value).toBe(12.5)
        expect(refs.duration.value).toBe(180)
        expect(updateLocalQueueCurrentIndex).toHaveBeenCalledWith(2)
        session.dispose()
    })

    it('updates position baseline from position heartbeats', () => {
        const { session, refs } = createSession()
        session.applyNativeEvent({
            type: 'position',
            seq: 1,
            positionSeconds: 33.3,
            isPlaying: true,
        })
        expect(refs.currentTime.value).toBe(33.3)
        expect(refs.isPlaying.value).toBe(true)
    })
})
