import { describe, expect, it, vi } from 'vitest'
import { ref, shallowRef } from 'vue'
import type {
    CurrentQueueDto,
    DeviceChangePayload,
    LoadAudioSourcePayload,
    ScheduledActionPayload,
} from '@/services/playbackSyncProtocol'
import type {
    PlaybackSyncClientDiagnosticsSnapshot,
    PlaybackSyncClientPhase,
} from '@/services/playbackSyncClient'
import type {
    AudioTrack,
    PlaybackSyncLocalExecutionSnapshot,
    TimestampedPayload,
} from '@/stores/audioShared'
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
        clientDiagnostics: shallowRef<PlaybackSyncClientDiagnosticsSnapshot | null>(null),
        lastScheduledAction: shallowRef<TimestampedPayload<ScheduledActionPayload> | null>(null),
        lastLoadAudioSource: shallowRef<TimestampedPayload<LoadAudioSourcePayload> | null>(null),
        lastDeviceChange: shallowRef<TimestampedPayload<DeviceChangePayload> | null>(null),
        lastLocalExecution: shallowRef<PlaybackSyncLocalExecutionSnapshot | null>(null),
        lastAppliedVersion: ref(0),
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

    it('assembles clientDiagnostics from sync-state diagnostics payloads', () => {
        const { session, refs } = createSession()
        session.applyNativeEvent({
            type: 'sync-state',
            seq: 1,
            syncPhase: 'ready',
            clockOffsetMs: 42.5,
            roundTripEstimateMs: 18,
            diagnostics: {
                socketState: 'open',
                reconnectAttempt: 2,
                snapshotReceived: true,
                lastNtpResponseAtMs: 1_000,
                measurements: [{ offsetMs: 40, rttMs: 20, recordedAtMs: 990 }],
            },
        })

        const diagnostics = refs.clientDiagnostics.value
        expect(diagnostics).not.toBeNull()
        expect(diagnostics?.phase).toBe('ready')
        expect(diagnostics?.socketState).toBe('open')
        expect(diagnostics?.reconnectAttempt).toBe(2)
        expect(diagnostics?.lastNtpResponseAtMs).toBe(1_000)
        expect(diagnostics?.measurements).toEqual([{ offsetMs: 40, rttMs: 20, recordedAtMs: 990 }])
        expect(diagnostics?.deviceId.startsWith('tauri-android-')).toBe(true)
    })

    it('records protocol events and maps well-known message payloads', () => {
        const { session, refs } = createSession()
        const scheduledAction: ScheduledActionPayload = {
            commandId: 'cmd-1',
            serverTimeToExecuteMs: 5_000,
            scheduledAction: {
                action: 'PLAY',
                status: 'PLAYING',
                currentIndex: 0,
                positionSeconds: 0,
                version: 9,
            },
        }
        session.applyNativeEvent({
            type: 'protocol-event',
            seq: 1,
            direction: 'in',
            messageType: 'SCHEDULED_ACTION',
            payload: scheduledAction,
            atMs: 4_900,
        })
        session.applyNativeEvent({
            type: 'protocol-event',
            seq: 2,
            direction: 'in',
            messageType: 'ROOM_EVENT_DEVICE_CHANGE',
            payload: { devices: [{ deviceId: 'device-a' }] },
            atMs: 4_950,
        })
        session.applyNativeEvent({
            type: 'protocol-event',
            seq: 3,
            direction: 'out',
            messageType: 'PLAY',
            payload: { commandId: 'cmd-2' },
            atMs: 4_960,
        })

        expect(refs.lastScheduledAction.value).toEqual({ atMs: 4_900, payload: scheduledAction })
        expect(refs.lastDeviceChange.value?.payload.devices).toEqual([{ deviceId: 'device-a' }])
        const diagnostics = refs.clientDiagnostics.value
        expect(diagnostics?.protocolEvents).toHaveLength(3)
        expect(diagnostics?.lastInboundEvent?.type).toBe('ROOM_EVENT_DEVICE_CHANGE')
        expect(diagnostics?.lastOutboundEvent?.type).toBe('PLAY')
    })

    it('applies local-execution events and advances the applied version', () => {
        const { session, refs } = createSession()
        session.applyNativeEvent({
            type: 'local-execution',
            seq: 1,
            atMs: 6_000,
            action: 'PLAY',
            commandId: 'cmd-3',
            version: 12,
            estimatedServerNowMs: 5_990,
            executeAtServerMs: 6_040,
            waitMs: 50,
            lateSeconds: 0,
            scheduledOffset: 3.2,
            currentIndex: 1,
            mediaFileId: 2001,
        })

        expect(refs.lastLocalExecution.value?.action).toBe('PLAY')
        expect(refs.lastLocalExecution.value?.waitMs).toBe(50)
        expect(refs.lastAppliedVersion.value).toBe(12)
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
