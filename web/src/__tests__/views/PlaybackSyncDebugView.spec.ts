import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { reactive } from 'vue'

const playbackSyncDebugSnapshot = reactive({
    syncState: 'ready',
    syncStatusText: '同步已就绪',
    canSendRealtimeControl: true,
    clockOffsetMs: 5,
    roundTripEstimateMs: 18,
    latestSnapshot: {
        status: 'PLAYING',
        recordingId: 9,
        mediaFileId: 2_009,
        sourceUrl: '/api/media/2009',
        positionSeconds: 12,
        serverTimeToExecuteMs: 1_000,
        version: 4,
        updatedAtMs: 1_000,
    },
    latestSnapshotReceivedAtMs: 1_000,
    lastScheduledAction: {
        atMs: 1_200,
        payload: {
            commandId: 'cmd-play-9',
            serverTimeToExecuteMs: 1_500,
            scheduledAction: {
                action: 'PLAY',
                status: 'PLAYING',
                recordingId: 9,
                mediaFileId: 2_009,
                sourceUrl: '/api/media/2009',
                positionSeconds: 12,
                version: 4,
            },
        },
    },
    lastLoadAudioSource: {
        atMs: 1_100,
        payload: {
            commandId: 'cmd-play-9',
            recordingId: 9,
            mediaFileId: 2_009,
            sourceUrl: '/api/media/2009',
        },
    },
    lastDeviceChange: {
        atMs: 1_300,
        payload: {
            devices: [{ deviceId: 'web-test' }, { deviceId: 'web-pad' }],
        },
    },
    queuedPlayIntent: null,
    awaitingSyncRecovery: false,
    audioUnlockRequired: false,
    lastAppliedVersion: 4,
    currentTrack: {
        id: 9,
        title: 'Track 9',
        artist: 'Artist 9',
        cover: '/cover/9.jpg',
        src: '/audio/9.mp3',
        mediaFileId: 2_009,
    },
    isPlaying: true,
    currentTime: 13,
    duration: 25,
    currentBuffer: {
        recordingId: 9,
        mediaFileId: 2_009,
        duration: 25,
    },
    activeLoad: null,
    lastLocalExecution: {
        atMs: 1_400,
        action: 'PLAY',
        commandId: 'cmd-play-9',
        version: 4,
        estimatedServerNowMs: 1_250,
        executeAtServerMs: 1_500,
        waitMs: 250,
        lateSeconds: 0,
        scheduledOffset: 12,
        whenContextTime: 42.125,
        bufferDuration: 25,
        recordingId: 9,
        mediaFileId: 2_009,
    },
    clientDiagnostics: {
        deviceId: 'web-test',
        phase: 'ready',
        clockOffsetMs: 5,
        roundTripEstimateMs: 18,
        socketState: 'open',
        reconnectAttempt: 1,
        snapshotReceived: true,
        initialCalibration: {
            sampledCount: 20,
            requiredSampleCount: 20,
            settling: false,
        },
        measurements: [
            {
                offsetMs: 5,
                rttMs: 18,
                recordedAtMs: 1_000,
            },
        ],
        lastNtpRequestAtMs: 1_500,
        lastNtpResponseAtMs: 2_000,
        lastInboundEvent: {
            direction: 'in',
            type: 'SCHEDULED_ACTION',
            rawType: 'SCHEDULED_ACTION',
            atMs: 2_100,
            payload: {
                commandId: 'cmd-play-9',
            },
        },
        lastOutboundEvent: {
            direction: 'out',
            type: 'NTP_REQUEST',
            rawType: 'NTP_REQUEST',
            atMs: 2_050,
            payload: {
                t0: 2_050,
            },
        },
        protocolEvents: [
            {
                direction: 'out',
                type: 'HELLO',
                rawType: 'HELLO',
                atMs: 900,
                payload: {
                    deviceId: 'web-test',
                },
            },
            {
                direction: 'in',
                type: 'SCHEDULED_ACTION',
                rawType: 'SCHEDULED_ACTION',
                atMs: 2_100,
                payload: {
                    commandId: 'cmd-play-9',
                },
            },
        ],
        lastError: null,
    },
    error: null,
})

vi.mock('@/stores/audio', () => ({
    useAudioStore: () => ({
        playbackSyncDebugSnapshot,
    }),
}))

vi.mock('@/components/dashboard/DashboardTopBar.vue', () => ({
    default: {
        template: '<div data-test="dashboard-top-bar" />',
    },
}))

import PlaybackSyncDebugView from '@/views/PlaybackSyncDebugView.vue'

describe('PlaybackSyncDebugView', () => {
    beforeEach(() => {
        vi.useFakeTimers()
        vi.setSystemTime(new Date('2026-03-07T00:00:03Z'))
        Object.defineProperty(performance, 'timeOrigin', {
            configurable: true,
            value: 0,
        })
        vi.spyOn(performance, 'now').mockImplementation(() => Date.now())
        playbackSyncDebugSnapshot.syncStatusText = '同步已就绪'
        playbackSyncDebugSnapshot.clientDiagnostics.lastNtpResponseAtMs = Date.now() - 1_000
    })

    afterEach(() => {
        vi.useRealTimers()
        vi.restoreAllMocks()
    })

    it('renders sync diagnostics panels from the store snapshot', () => {
        const wrapper = mount(PlaybackSyncDebugView)

        expect(wrapper.get('[data-test="debug-panel-overview"]').text()).toContain('同步已就绪')
        expect(wrapper.get('[data-test="debug-device-id"]').text()).toContain('web-test')
        expect(wrapper.get('[data-test="debug-panel-events"]').text()).toContain('SCHEDULED_ACTION')
        expect(wrapper.get('[data-test="debug-local-execution"]').text()).toContain('250ms')
    })

    it('updates relative age labels on the one-second tick', async () => {
        const wrapper = mount(PlaybackSyncDebugView)

        expect(wrapper.get('[data-test="debug-last-response-age"]').text()).toContain('1s ago')

        vi.advanceTimersByTime(1_000)
        await wrapper.vm.$nextTick()

        expect(wrapper.get('[data-test="debug-last-response-age"]').text()).toContain('2s ago')
    })
})
