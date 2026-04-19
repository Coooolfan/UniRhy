import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

const apiMockState = vi.hoisted(() => ({
    getRecording: vi.fn(),
    replaceCurrentQueue: vi.fn(),
    appendToCurrentQueue: vi.fn(),
    reorderCurrentQueue: vi.fn(),
    setCurrentIndex: vi.fn(),
    updateCurrentQueueStrategy: vi.fn(),
    playNextInCurrentQueue: vi.fn(),
    playPreviousInCurrentQueue: vi.fn(),
    removeCurrentQueueEntry: vi.fn(),
    clearCurrentQueue: vi.fn(),
}))

type MockPlaybackSyncClientState = {
    deviceId: string
    phase: string
    clockOffsetMs: number
    roundTripEstimateMs: number
}

type MockPlaybackSyncClientCallbacks = {
    onMessage?: (message: unknown) => void
    onStateChange?: (state: MockPlaybackSyncClientState) => void
    onDiagnosticsChange?: (snapshot: MockPlaybackSyncClientDiagnosticsSnapshot) => void
}

type MockPlaybackSyncProtocolEvent = {
    direction: 'in' | 'out'
    type: string
    rawType: string | null
    atMs: number
    payload: unknown
}

type MockPlaybackSyncClientDiagnosticsSnapshot = {
    deviceId: string
    phase: string
    clockOffsetMs: number
    roundTripEstimateMs: number
    socketState: string
    reconnectAttempt: number
    snapshotReceived: boolean
    initialCalibration: {
        sampledCount: number
        requiredSampleCount: number
        settling: boolean
    }
    measurements: Array<{
        offsetMs: number
        rttMs: number
        recordedAtMs: number
    }>
    lastNtpRequestAtMs: number | null
    lastNtpResponseAtMs: number | null
    lastInboundEvent: MockPlaybackSyncProtocolEvent | null
    lastOutboundEvent: MockPlaybackSyncProtocolEvent | null
    protocolEvents: MockPlaybackSyncProtocolEvent[]
    lastError: {
        atMs: number
        code: string | null
        message: string
        rawType: string | null
        rawMessage: string | null
    } | null
}

type MockPlaybackSyncClientInstance = {
    connect: ReturnType<typeof vi.fn>
    disconnect: ReturnType<typeof vi.fn>
    sendPlay: ReturnType<typeof vi.fn>
    sendPause: ReturnType<typeof vi.fn>
    sendSeek: ReturnType<typeof vi.fn>
    sendAudioSourceLoaded: ReturnType<typeof vi.fn>
    requestSync: ReturnType<typeof vi.fn>
    callbacks: MockPlaybackSyncClientCallbacks
    state: MockPlaybackSyncClientState
    diagnostics: MockPlaybackSyncClientDiagnosticsSnapshot
    getState: () => MockPlaybackSyncClientState
    getDiagnosticsSnapshot: () => MockPlaybackSyncClientDiagnosticsSnapshot
    setState: (nextState: Partial<MockPlaybackSyncClientState>) => void
    setDiagnostics: (nextDiagnostics: Partial<MockPlaybackSyncClientDiagnosticsSnapshot>) => void
    emitMessage: (message: unknown) => void
}

function getMockPlaybackSyncClientState(this: MockPlaybackSyncClientInstance) {
    return this.state
}

function setMockPlaybackSyncClientState(
    this: MockPlaybackSyncClientInstance,
    nextState: Partial<MockPlaybackSyncClientState>,
) {
    this.state = {
        ...this.state,
        ...nextState,
    }
    this.diagnostics = {
        ...this.diagnostics,
        deviceId: this.state.deviceId,
        phase: this.state.phase,
        clockOffsetMs: this.state.clockOffsetMs,
        roundTripEstimateMs: this.state.roundTripEstimateMs,
    }
    this.callbacks.onStateChange?.(this.state)
    this.callbacks.onDiagnosticsChange?.(this.diagnostics)
}

function getMockPlaybackSyncClientDiagnostics(
    this: MockPlaybackSyncClientInstance,
): MockPlaybackSyncClientDiagnosticsSnapshot {
    return this.diagnostics
}

function setMockPlaybackSyncClientDiagnostics(
    this: MockPlaybackSyncClientInstance,
    nextDiagnostics: Partial<MockPlaybackSyncClientDiagnosticsSnapshot>,
) {
    this.diagnostics = {
        ...this.diagnostics,
        ...nextDiagnostics,
        initialCalibration: {
            ...this.diagnostics.initialCalibration,
            ...nextDiagnostics.initialCalibration,
        },
        measurements: nextDiagnostics.measurements
            ? [...nextDiagnostics.measurements]
            : this.diagnostics.measurements,
        protocolEvents: nextDiagnostics.protocolEvents
            ? [...nextDiagnostics.protocolEvents]
            : this.diagnostics.protocolEvents,
    }
    this.callbacks.onDiagnosticsChange?.(this.diagnostics)
}

function appendMockProtocolEvent(
    this: MockPlaybackSyncClientInstance,
    eventInput: Pick<MockPlaybackSyncProtocolEvent, 'direction' | 'type' | 'payload'>,
) {
    const event = {
        direction: eventInput.direction,
        type: eventInput.type,
        rawType: eventInput.type,
        atMs: Date.now(),
        payload: eventInput.payload,
    } satisfies MockPlaybackSyncProtocolEvent
    const protocolEvents = [...this.diagnostics.protocolEvents, event].slice(-30)
    this.diagnostics = {
        ...this.diagnostics,
        protocolEvents,
        lastInboundEvent: event.direction === 'in' ? event : this.diagnostics.lastInboundEvent,
        lastOutboundEvent: event.direction === 'out' ? event : this.diagnostics.lastOutboundEvent,
        lastNtpRequestAtMs:
            event.direction === 'out' && event.type === 'NTP_REQUEST'
                ? event.atMs
                : this.diagnostics.lastNtpRequestAtMs,
        lastNtpResponseAtMs:
            event.direction === 'in' && event.type === 'NTP_RESPONSE'
                ? event.atMs
                : this.diagnostics.lastNtpResponseAtMs,
        snapshotReceived:
            event.direction === 'in' && event.type === 'SNAPSHOT'
                ? true
                : this.diagnostics.snapshotReceived,
        lastError:
            event.direction === 'in' &&
            event.type === 'ERROR' &&
            typeof event.payload === 'object' &&
            event.payload !== null &&
            'message' in event.payload
                ? {
                      atMs: event.atMs,
                      code:
                          'code' in event.payload && typeof event.payload.code === 'string'
                              ? event.payload.code
                              : null,
                      message:
                          typeof event.payload.message === 'string'
                              ? event.payload.message
                              : 'Unknown error',
                      rawType: event.type,
                      rawMessage: JSON.stringify(event.payload),
                  }
                : this.diagnostics.lastError,
    }
    this.callbacks.onDiagnosticsChange?.(this.diagnostics)
}

function emitMockPlaybackSyncClientMessage(this: MockPlaybackSyncClientInstance, message: unknown) {
    if (typeof message === 'object' && message !== null && 'type' in message) {
        appendMockProtocolEvent.call(this, {
            direction: 'in',
            type: typeof message.type === 'string' ? message.type : 'UNKNOWN',
            payload: 'payload' in message ? message.payload : null,
        })
    }
    this.callbacks.onMessage?.(message)
}

const playbackSyncMockState = vi.hoisted(() => ({
    clients: [] as MockPlaybackSyncClientInstance[],
}))

vi.mock('@/ApiInstance', async (importOriginal) => {
    const actual = await importOriginal<typeof import('@/ApiInstance')>()
    const recordingController = Object.assign(
        Object.create(Object.getPrototypeOf(actual.api.recordingController)),
        actual.api.recordingController,
        {
            getRecording: apiMockState.getRecording,
        },
    )
    const playbackQueueController = Object.assign(
        Object.create(Object.getPrototypeOf(actual.api.playbackQueueController)),
        actual.api.playbackQueueController,
        {
            replaceCurrentQueue: apiMockState.replaceCurrentQueue,
            appendToCurrentQueue: apiMockState.appendToCurrentQueue,
            reorderCurrentQueue: apiMockState.reorderCurrentQueue,
            setCurrentIndex: apiMockState.setCurrentIndex,
            updateCurrentQueueStrategy: apiMockState.updateCurrentQueueStrategy,
            playNextInCurrentQueue: apiMockState.playNextInCurrentQueue,
            playPreviousInCurrentQueue: apiMockState.playPreviousInCurrentQueue,
            removeCurrentQueueEntry: apiMockState.removeCurrentQueueEntry,
            clearCurrentQueue: apiMockState.clearCurrentQueue,
        },
    )
    const api = Object.assign(Object.create(Object.getPrototypeOf(actual.api)), actual.api, {
        recordingController,
        playbackQueueController,
    })

    return {
        ...actual,
        api,
    }
})

vi.mock('@/services/playbackSyncClient', () => {
    function PlaybackSyncClient(
        this: MockPlaybackSyncClientInstance,
        callbacks: MockPlaybackSyncClientCallbacks = {},
    ) {
        this.connect = vi.fn()
        this.callbacks = callbacks
        this.state = {
            deviceId: 'web-test',
            phase: 'connecting',
            clockOffsetMs: 0,
            roundTripEstimateMs: 0,
        }
        this.diagnostics = {
            deviceId: 'web-test',
            phase: 'connecting',
            clockOffsetMs: 0,
            roundTripEstimateMs: 0,
            socketState: 'idle',
            reconnectAttempt: 0,
            snapshotReceived: false,
            initialCalibration: {
                sampledCount: 0,
                requiredSampleCount: 20,
                settling: false,
            },
            measurements: [],
            lastNtpRequestAtMs: null,
            lastNtpResponseAtMs: null,
            lastInboundEvent: null,
            lastOutboundEvent: null,
            protocolEvents: [],
            lastError: null,
        }
        this.connect = vi.fn(() => {
            setMockPlaybackSyncClientDiagnostics.call(this, { socketState: 'open' })
        })
        this.disconnect = vi.fn(() => {
            setMockPlaybackSyncClientDiagnostics.call(this, { socketState: 'idle' })
        })
        this.sendPlay = vi.fn((payload: unknown) => {
            appendMockProtocolEvent.call(this, { direction: 'out', type: 'PLAY', payload })
            return true
        })
        this.sendPause = vi.fn((payload: unknown) => {
            appendMockProtocolEvent.call(this, { direction: 'out', type: 'PAUSE', payload })
            return true
        })
        this.sendSeek = vi.fn((payload: unknown) => {
            appendMockProtocolEvent.call(this, { direction: 'out', type: 'SEEK', payload })
            return true
        })
        this.sendAudioSourceLoaded = vi.fn((payload: unknown) => {
            appendMockProtocolEvent.call(this, {
                direction: 'out',
                type: 'AUDIO_SOURCE_LOADED',
                payload,
            })
            return true
        })
        this.requestSync = vi.fn(() => {
            appendMockProtocolEvent.call(this, {
                direction: 'out',
                type: 'SYNC',
                payload: { deviceId: this.state.deviceId },
            })
            return true
        })
        playbackSyncMockState.clients.push(this)
    }

    PlaybackSyncClient.prototype.getState = getMockPlaybackSyncClientState
    PlaybackSyncClient.prototype.getDiagnosticsSnapshot = getMockPlaybackSyncClientDiagnostics
    PlaybackSyncClient.prototype.setState = setMockPlaybackSyncClientState
    PlaybackSyncClient.prototype.setDiagnostics = setMockPlaybackSyncClientDiagnostics
    PlaybackSyncClient.prototype.emitMessage = emitMockPlaybackSyncClientMessage

    return {
        PlaybackSyncClient,
    }
})

import { useAudioStore, type AudioTrack } from '@/stores/audio'
import { api } from '@/ApiInstance'
import type {
    LoadAudioSourceMessage,
    ScheduledActionMessage,
} from '@/services/playbackSyncProtocol'
import { useUserStore } from '@/stores/user'
import { nowClientMs } from '@/utils/time'

const getRecordingMock = vi.mocked(api.recordingController.getRecording)
const replaceCurrentQueueMock = apiMockState.replaceCurrentQueue
const appendToCurrentQueueMock = apiMockState.appendToCurrentQueue
const reorderCurrentQueueMock = apiMockState.reorderCurrentQueue
const setCurrentIndexMock = apiMockState.setCurrentIndex
const updateCurrentQueueStrategyMock = apiMockState.updateCurrentQueueStrategy
const playNextInCurrentQueueMock = apiMockState.playNextInCurrentQueue
const playPreviousInCurrentQueueMock = apiMockState.playPreviousInCurrentQueue
const removeCurrentQueueEntryMock = apiMockState.removeCurrentQueueEntry
const clearCurrentQueueMock = apiMockState.clearCurrentQueue
type TestRecordingAsset = {
    id: number
    comment: string
    mediaFile: {
        id: number
        sha256?: string
        objectKey: string
        mimeType: string
        size: number
        url?: string
    }
}
type RecordingMetadata = Awaited<ReturnType<typeof api.recordingController.getRecording>> & {
    lyrics?: string
    assets?: readonly TestRecordingAsset[]
}
type RecordingMetadataOverrides = Partial<Omit<RecordingMetadata, 'cover'>> & {
    cover?: RecordingMetadata['cover'] | null
}

let nextAnimationFrameId: number
let animationFrameCallbacks: Map<number, FrameRequestCallback>
let resumeError: Error | null = null

type MockAudioBuffer = {
    duration: number
}

type MockGainNode = {
    gain: {
        value: number
    }
    connect: ReturnType<typeof vi.fn>
}

type MockAudioBufferSourceNode = EventTarget & {
    buffer: MockAudioBuffer | null
    connect: ReturnType<typeof vi.fn>
    disconnect: ReturnType<typeof vi.fn>
    start: ReturnType<typeof vi.fn<(when: number, offset?: number) => void>>
    stop: ReturnType<typeof vi.fn<() => void>>
    emitEnded: () => void
}

const createMockAudioBuffer = (duration: number): MockAudioBuffer => ({
    duration,
})

const createMockGainNode = (): MockGainNode => ({
    gain: { value: 1 },
    connect: vi.fn(),
})

const createMockSourceNode = (): MockAudioBufferSourceNode => {
    const target = new EventTarget()

    return Object.assign(target, {
        buffer: null,
        connect: vi.fn(),
        disconnect: vi.fn(),
        start: vi.fn<(when: number, offset?: number) => void>(),
        stop: vi.fn(() => {
            target.dispatchEvent(new Event('ended'))
        }),
        emitEnded: () => {
            target.dispatchEvent(new Event('ended'))
        },
    })
}

class MockAudioContext {
    static instances: MockAudioContext[] = []
    static defaultState: 'running' | 'suspended' = 'running'

    readonly destination = {}
    readonly gain = createMockGainNode()
    readonly sourceNodes: MockAudioBufferSourceNode[] = []
    currentTime = 0
    state: 'running' | 'suspended' | 'closed' = MockAudioContext.defaultState

    constructor() {
        MockAudioContext.instances.push(this)
    }

    readonly resume = vi.fn(() => {
        if (resumeError) {
            return Promise.reject(new Error(resumeError.message))
        }
        this.state = 'running'
        return Promise.resolve()
    })

    readonly close = vi.fn(() => {
        this.state = 'closed'
        return Promise.resolve()
    })

    readonly createGain = vi.fn(() => {
        return this.gain
    })

    readonly createBufferSource = vi.fn(() => {
        const source = createMockSourceNode()
        this.sourceNodes.push(source)
        return source
    })

    readonly decodeAudioData = vi.fn((arrayBuffer: ArrayBuffer) => {
        const view = new Uint8Array(arrayBuffer)
        return Promise.resolve(createMockAudioBuffer(view[0] ?? 0))
    })
}

const fetchMock = vi.fn<typeof fetch>()

const buildTrack = (id: number, src = `/audio/${id}.mp3`): AudioTrack => ({
    id,
    title: `Track ${id}`,
    artist: `Artist ${id}`,
    cover: `/cover/${id}.jpg`,
    src,
    mediaFileId: id + 1_000,
})

const buildQueue = (tracks: AudioTrack[], currentIndex = 0) => ({
    items: tracks.map((track) => ({
        recordingId: track.id,
        title: track.title,
        artistLabel: track.artist,
        coverUrl: track.cover,
        durationMs: 180_000,
    })),
    recordingIds: tracks.map((track) => track.id),
    currentIndex: tracks[currentIndex] ? currentIndex : 0,
    playbackStrategy: 'SEQUENTIAL' as const,
    stopStrategy: 'LIST' as const,
    playbackStatus: 'PAUSED' as const,
    positionMs: 0,
    serverTimeToExecuteMs: 0,
    version: 1,
    updatedAtMs: nowClientMs(),
})

const buildRecordingMetadata = (
    id: number,
    overrides: RecordingMetadataOverrides = {},
): RecordingMetadata => {
    const { cover, ...rest } = overrides

    return {
        id,
        kind: rest.kind ?? 'CD',
        label: rest.label ?? `Label ${id}`,
        work: rest.work ?? { id: id + 10_000, title: `Work ${id}` },
        title: rest.title ?? `Hydrated Track ${id}`,
        comment: rest.comment ?? `Comment ${id}`,
        durationMs: rest.durationMs ?? id * 1_000,
        defaultInWork: rest.defaultInWork ?? false,
        lyrics: rest.lyrics ?? '',
        artists: rest.artists ?? [
            {
                id: id + 20_000,
                displayName: `Hydrated Artist ${id}`,
                alias: [],
                comment: '',
            },
        ],
        assets: rest.assets ?? [
            {
                id: id + 40_000,
                comment: `Asset ${id}`,
                mediaFile: {
                    id: id + 2_000,
                    sha256: `audio-sha-${id}`,
                    objectKey: `audio/${id}.opus`,
                    mimeType: 'audio/opus',
                    size: 4_096,
                    url: `/api/media/${id + 2_000}`,
                },
            },
        ],
        ...(cover === null
            ? {}
            : {
                  cover: cover ?? {
                      id: id + 30_000,
                      sha256: `sha-${id}`,
                      objectKey: `cover/${id}`,
                      mimeType: 'image/jpeg',
                      size: 1_024,
                      url: `/api/media/${id + 30_000}`,
                  },
              }),
    }
}

const setPreferredAssetFormat = (preferredAssetFormat: string) => {
    const userStore = useUserStore()
    userStore.user = {
        id: 1,
        name: 'Tester',
        email: 'tester@example.com',
        admin: false,
        preferences: {
            preferredAssetFormat,
        },
    }
}

const createResponse = (duration: number, status = 200) => {
    return new Response(new Uint8Array([duration]).buffer, { status })
}

const createDeferred = <T>() => {
    let resolve!: (value: T | PromiseLike<T>) => void
    let reject!: (reason?: unknown) => void
    const promise = new Promise<T>((innerResolve, innerReject) => {
        resolve = innerResolve
        reject = innerReject
    })
    return { promise, resolve, reject }
}

const flushPromises = async (times = 8) => {
    for (let index = 0; index < times; index += 1) {
        await Promise.resolve()
    }
    await new Promise<void>((resolve) => {
        window.setTimeout(resolve, 0)
    })
}

const latestClient = () => {
    // oxlint-disable-next-line unicorn/prefer-at
    const client = playbackSyncMockState.clients[playbackSyncMockState.clients.length - 1]
    if (!client) {
        throw new Error('PlaybackSyncClient was not created')
    }
    return client
}

describe('audio store', () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        window.localStorage.clear()
        delete window.__UNIRHY_RUNTIME__
        setPreferredAssetFormat('audio/opus')
        playbackSyncMockState.clients.length = 0
        nextAnimationFrameId = 1
        animationFrameCallbacks = new Map()
        resumeError = null
        MockAudioContext.instances = []
        MockAudioContext.defaultState = 'running'
        fetchMock.mockReset()
        getRecordingMock.mockReset()
        replaceCurrentQueueMock.mockReset()
        appendToCurrentQueueMock.mockReset()
        reorderCurrentQueueMock.mockReset()
        setCurrentIndexMock.mockReset()
        removeCurrentQueueEntryMock.mockReset()
        clearCurrentQueueMock.mockReset()
        getRecordingMock.mockRejectedValue(new Error('metadata unavailable'))
        replaceCurrentQueueMock.mockImplementation(
            ({
                body,
            }: {
                body: { recordingIds: number[]; currentIndex: number; version: number }
            }) =>
                Promise.resolve(
                    buildQueue(
                        body.recordingIds.map((recordingId: number) => buildTrack(recordingId)),
                        body.currentIndex,
                    ),
                ),
        )
        appendToCurrentQueueMock.mockResolvedValue(buildQueue([]))
        reorderCurrentQueueMock.mockResolvedValue(buildQueue([]))
        setCurrentIndexMock.mockResolvedValue(buildQueue([]))
        removeCurrentQueueEntryMock.mockResolvedValue(buildQueue([]))
        clearCurrentQueueMock.mockResolvedValue(buildQueue([]))

        vi.stubGlobal('fetch', fetchMock)
        vi.stubGlobal('AudioContext', MockAudioContext)
        Object.defineProperty(window, 'AudioContext', {
            configurable: true,
            writable: true,
            value: MockAudioContext,
        })
        vi.stubGlobal('requestAnimationFrame', (callback: FrameRequestCallback) => {
            const id = nextAnimationFrameId
            nextAnimationFrameId += 1
            animationFrameCallbacks.set(id, callback)
            return id
        })
        vi.stubGlobal('cancelAnimationFrame', (id: number) => {
            animationFrameCallbacks.delete(id)
        })
    })

    it('prefers the configured asset format when hydrating synced audio sources', async () => {
        setPreferredAssetFormat('audio/flac')
        getRecordingMock.mockResolvedValueOnce(
            buildRecordingMetadata(7, {
                assets: [
                    {
                        id: 47_001,
                        comment: 'Audio mp3',
                        mediaFile: {
                            id: 2_071,
                            sha256: 'audio-sha-7-mp3',
                            objectKey: 'audio/7.mp3',
                            mimeType: 'audio/mpeg',
                            size: 2_048,
                            url: '/api/media/2071',
                        },
                    },
                    {
                        id: 47_002,
                        comment: 'Audio flac',
                        mediaFile: {
                            id: 2_072,
                            sha256: 'audio-sha-7-flac',
                            objectKey: 'audio/7.flac',
                            mimeType: 'audio/flac',
                            size: 4_096,
                            url: '/api/media/2072',
                        },
                    },
                ],
            }),
        )
        fetchMock.mockResolvedValue(createResponse(45))

        const audioStore = useAudioStore()
        audioStore.connectPlaybackSync()
        const client = latestClient()

        client.emitMessage({
            type: 'ROOM_EVENT_LOAD_AUDIO_SOURCE',
            payload: {
                commandId: 'cmd-pref-flac',
                currentIndex: 0,
                recordingId: 7,
            },
        } satisfies LoadAudioSourceMessage)
        await flushPromises(12)

        expect(fetchMock).toHaveBeenCalledWith('/api/media/2072', { credentials: 'include' })
        expect(audioStore.currentTrack?.mediaFileId).toBe(2_072)
        expect(audioStore.playbackSyncDebugSnapshot.currentBuffer?.mediaFileId).toBe(2_072)
    })

    it('does not hot-switch the current source when the preferred format changes later', async () => {
        setPreferredAssetFormat('audio/mpeg')
        getRecordingMock.mockResolvedValueOnce(
            buildRecordingMetadata(7, {
                assets: [
                    {
                        id: 47_001,
                        comment: 'Audio mp3',
                        mediaFile: {
                            id: 2_071,
                            sha256: 'audio-sha-7-mp3',
                            objectKey: 'audio/7.mp3',
                            mimeType: 'audio/mpeg',
                            size: 2_048,
                            url: '/api/media/2071',
                        },
                    },
                    {
                        id: 47_002,
                        comment: 'Audio flac',
                        mediaFile: {
                            id: 2_072,
                            sha256: 'audio-sha-7-flac',
                            objectKey: 'audio/7.flac',
                            mimeType: 'audio/flac',
                            size: 4_096,
                            url: '/api/media/2072',
                        },
                    },
                ],
            }),
        )

        const audioStore = useAudioStore()
        audioStore.connectPlaybackSync()
        const client = latestClient()

        client.emitMessage({
            type: 'SNAPSHOT',
            payload: {
                state: {
                    status: 'PAUSED',
                    currentIndex: 0,
                    positionSeconds: 12,
                    serverTimeToExecuteMs: nowClientMs(),
                    version: 5,
                    updatedAtMs: nowClientMs(),
                },
                queue: buildQueue([buildTrack(7)], 0),
                serverNowMs: nowClientMs(),
            },
        })
        await flushPromises(12)

        expect(audioStore.currentTrack?.mediaFileId).toBe(2_071)

        setPreferredAssetFormat('audio/flac')
        client.emitMessage({
            type: 'SNAPSHOT',
            payload: {
                state: {
                    status: 'PAUSED',
                    currentIndex: 0,
                    positionSeconds: 18,
                    serverTimeToExecuteMs: nowClientMs(),
                    version: 6,
                    updatedAtMs: nowClientMs(),
                },
                queue: buildQueue([buildTrack(7)], 0),
                serverNowMs: nowClientMs(),
            },
        })
        await flushPromises(12)

        expect(audioStore.currentTrack?.mediaFileId).toBe(2_071)
    })

    it('queues only the latest play intent until sync becomes ready', async () => {
        const audioStore = useAudioStore()
        const firstTrack = buildTrack(1)
        const secondTrack = buildTrack(2)

        await audioStore.play(firstTrack)
        await audioStore.play(secondTrack)

        const client = latestClient()
        expect(client.connect).toHaveBeenCalledTimes(2)
        expect(client.sendPlay).not.toHaveBeenCalled()
        expect(audioStore.currentTrack?.id).toBe(2)
        expect(audioStore.isPlaying).toBe(false)

        client.setState({
            phase: 'ready',
            clockOffsetMs: 5,
            roundTripEstimateMs: 15,
        })
        await flushPromises(12)

        expect(client.sendPlay).toHaveBeenCalledTimes(1)
        expect(client.sendPlay).toHaveBeenCalledWith(
            expect.objectContaining({
                currentIndex: 0,
                positionSeconds: 0,
                version: 1,
            }),
        )
        expect(replaceCurrentQueueMock).toHaveBeenNthCalledWith(1, {
            body: {
                recordingIds: [1],
                currentIndex: 0,
                version: 0,
            },
        })
        expect(replaceCurrentQueueMock).toHaveBeenNthCalledWith(2, {
            body: {
                recordingIds: [2],
                currentIndex: 0,
                version: 0,
            },
        })
    })

    it('updates local queue state from queue change broadcasts', async () => {
        const audioStore = useAudioStore()
        audioStore.connectPlaybackSync()
        const client = latestClient()

        client.emitMessage({
            type: 'ROOM_EVENT_QUEUE_CHANGE',
            payload: {
                queue: buildQueue([buildTrack(3), buildTrack(4)], 1),
            },
        })
        await flushPromises()

        expect(audioStore.queueEntries).toHaveLength(2)
        expect(audioStore.currentQueueIndex).toBe(1)
        expect(audioStore.currentQueueEntry?.recordingId).toBe(4)
        expect(audioStore.currentTrack?.id).toBe(4)
        expect(audioStore.currentTrack?.title).toBe('Track 4')
    })

    it('advances to the next queue entry through the server current queue API', async () => {
        playNextInCurrentQueueMock.mockResolvedValueOnce(
            buildQueue([buildTrack(5), buildTrack(6)], 1),
        )

        const audioStore = useAudioStore()
        audioStore.connectPlaybackSync()
        const client = latestClient()
        client.setState({
            phase: 'ready',
            clockOffsetMs: 0,
            roundTripEstimateMs: 10,
        })

        client.emitMessage({
            type: 'ROOM_EVENT_QUEUE_CHANGE',
            payload: {
                queue: buildQueue([buildTrack(5), buildTrack(6)], 0),
            },
        })
        await flushPromises()

        await audioStore.playNext()

        expect(playNextInCurrentQueueMock).toHaveBeenCalledWith({
            body: {
                version: 1,
            },
        })
        expect(playPreviousInCurrentQueueMock).not.toHaveBeenCalled()
        expect(audioStore.currentQueueIndex).toBe(1)
        expect(audioStore.currentQueueEntry?.recordingId).toBe(6)
        expect(audioStore.currentTrack?.id).toBe(6)
        expect(client.sendPlay).not.toHaveBeenCalled()
    })

    it('updates queue strategies through the server current queue API', async () => {
        updateCurrentQueueStrategyMock.mockResolvedValueOnce({
            ...buildQueue([buildTrack(7)], 0),
            playbackStrategy: 'RADIO',
            stopStrategy: 'TRACK',
        })

        const audioStore = useAudioStore()
        audioStore.connectPlaybackSync()
        const client = latestClient()
        client.emitMessage({
            type: 'ROOM_EVENT_QUEUE_CHANGE',
            payload: {
                queue: buildQueue([buildTrack(7)], 0),
            },
        })
        await flushPromises()

        await audioStore.updateQueueStrategies('RADIO', 'TRACK')

        expect(updateCurrentQueueStrategyMock).toHaveBeenCalledWith({
            body: {
                playbackStrategy: 'RADIO',
                stopStrategy: 'TRACK',
                version: 1,
            },
        })
        expect(audioStore.playbackStrategy).toBe('RADIO')
        expect(audioStore.stopStrategy).toBe('TRACK')
    })

    it('loads audio on load-audio-source and acknowledges after decoding', async () => {
        getRecordingMock.mockResolvedValueOnce(buildRecordingMetadata(7))
        fetchMock.mockResolvedValue(createResponse(45))

        const audioStore = useAudioStore()
        audioStore.connectPlaybackSync()
        const client = latestClient()

        const message: LoadAudioSourceMessage = {
            type: 'ROOM_EVENT_LOAD_AUDIO_SOURCE',
            payload: {
                commandId: 'cmd-play-1',
                currentIndex: 0,
                recordingId: 7,
            },
        }

        client.emitMessage(message)
        await flushPromises(12)

        expect(fetchMock).toHaveBeenCalledWith('/api/media/2007', { credentials: 'include' })
        expect(client.sendAudioSourceLoaded).toHaveBeenCalledWith({
            commandId: 'cmd-play-1',
            currentIndex: 0,
            recordingId: 7,
        })
        expect(audioStore.currentTrack?.id).toBe(7)
        expect(audioStore.duration).toBe(45)
    })

    it('adds proxy auth token to synced audio sources in tauri runtime', async () => {
        window.__UNIRHY_RUNTIME__ = {
            apiBaseUrl: 'http://127.0.0.1:34855',
            platform: 'web',
        }
        window.localStorage.setItem('unirhy.auth-token', 'mobile-token')
        getRecordingMock.mockResolvedValueOnce(
            buildRecordingMetadata(8, {
                assets: [
                    {
                        id: 48_008,
                        comment: 'Signed Audio 2008',
                        mediaFile: {
                            id: 2_008,
                            sha256: 'audio-sha-8',
                            objectKey: 'audio/8.opus',
                            mimeType: 'audio/opus',
                            size: 4_096,
                            url: '/api/media/2008?_sig=abc&_exp=9999999999',
                        },
                    },
                ],
            }),
        )
        fetchMock.mockResolvedValue(createResponse(45))

        const audioStore = useAudioStore()
        audioStore.connectPlaybackSync()
        const client = latestClient()

        client.emitMessage({
            type: 'ROOM_EVENT_LOAD_AUDIO_SOURCE',
            payload: {
                commandId: 'cmd-play-2',
                currentIndex: 0,
                recordingId: 8,
            },
        } satisfies LoadAudioSourceMessage)
        await flushPromises(12)

        expect(fetchMock).toHaveBeenCalledWith(
            'http://127.0.0.1:34855/api/media/2008?_sig=abc&_exp=9999999999',
            {},
        )
        expect(client.sendAudioSourceLoaded).toHaveBeenCalledWith({
            commandId: 'cmd-play-2',
            currentIndex: 0,
            recordingId: 8,
        })
    })

    it('ignores stale scheduled actions with a lower version', async () => {
        getRecordingMock.mockResolvedValueOnce(buildRecordingMetadata(5))
        fetchMock.mockResolvedValue(createResponse(90))

        const audioStore = useAudioStore()
        audioStore.connectPlaybackSync()
        const client = latestClient()
        client.setState({
            phase: 'ready',
            clockOffsetMs: 0,
            roundTripEstimateMs: 10,
        })

        const nowMs = performance.timeOrigin + performance.now()
        const playMessage: ScheduledActionMessage = {
            type: 'SCHEDULED_ACTION',
            payload: {
                commandId: 'cmd-play-1',
                serverTimeToExecuteMs: nowMs,
                scheduledAction: {
                    action: 'PLAY',
                    status: 'PLAYING',
                    currentIndex: 0,
                    positionSeconds: 12,
                    version: 2,
                },
            },
        }

        client.emitMessage(playMessage)
        await flushPromises(12)

        expect(audioStore.currentTrack?.id).toBe(5)
        expect(audioStore.isPlaying).toBe(true)

        client.emitMessage({
            type: 'SCHEDULED_ACTION',
            payload: {
                commandId: 'cmd-pause-stale',
                serverTimeToExecuteMs: nowMs,
                scheduledAction: {
                    action: 'PAUSE',
                    status: 'PAUSED',
                    currentIndex: null,
                    positionSeconds: 0,
                    version: 1,
                },
            },
        } satisfies ScheduledActionMessage)
        await flushPromises(12)

        expect(audioStore.currentTrack?.id).toBe(5)
        expect(audioStore.isPlaying).toBe(true)
    })

    it('ignores stale scheduled actions that arrive after a newer paused snapshot', async () => {
        const audioStore = useAudioStore()
        audioStore.connectPlaybackSync()
        const client = latestClient()

        client.emitMessage({
            type: 'SNAPSHOT',
            payload: {
                state: {
                    status: 'PAUSED',
                    currentIndex: 0,
                    positionSeconds: 12,
                    serverTimeToExecuteMs: nowClientMs(),
                    version: 5,
                    updatedAtMs: nowClientMs(),
                },
                queue: buildQueue([buildTrack(7)], 0),
                serverNowMs: nowClientMs(),
            },
        })
        await flushPromises(12)

        expect(audioStore.currentTrack?.id).toBe(7)
        expect(audioStore.currentTime).toBe(12)
        expect(audioStore.isPlaying).toBe(false)

        client.emitMessage({
            type: 'SCHEDULED_ACTION',
            payload: {
                commandId: 'cmd-stale-clear',
                serverTimeToExecuteMs: nowClientMs(),
                scheduledAction: {
                    action: 'PAUSE',
                    status: 'PAUSED',
                    currentIndex: null,
                    positionSeconds: 0,
                    version: 4,
                },
            },
        } satisfies ScheduledActionMessage)
        await flushPromises(12)

        expect(audioStore.currentTrack?.id).toBe(7)
        expect(audioStore.currentTime).toBe(12)
        expect(audioStore.isPlaying).toBe(false)
    })

    it('requests sync recovery when a snapshot exists and sync becomes ready', async () => {
        const audioStore = useAudioStore()
        audioStore.connectPlaybackSync()
        const client = latestClient()

        client.emitMessage({
            type: 'SNAPSHOT',
            payload: {
                state: {
                    status: 'PLAYING',
                    currentIndex: 0,
                    positionSeconds: 16,
                    serverTimeToExecuteMs: performance.timeOrigin + performance.now(),
                    version: 3,
                    updatedAtMs: performance.timeOrigin + performance.now(),
                },
                queue: buildQueue([buildTrack(8)], 0),
                serverNowMs: performance.timeOrigin + performance.now(),
            },
        })
        await flushPromises()

        client.setState({
            phase: 'ready',
            clockOffsetMs: 0,
            roundTripEstimateMs: 10,
        })
        await flushPromises()

        expect(client.requestSync).toHaveBeenCalledTimes(1)
        expect(audioStore.currentTrack?.id).toBe(8)
        expect(audioStore.playbackSyncDebugSnapshot.awaitingSyncRecovery).toBe(true)
    })

    it('keeps paused progress after sync recovery replays the same pause action', async () => {
        const audioStore = useAudioStore()
        audioStore.connectPlaybackSync()
        const client = latestClient()

        client.emitMessage({
            type: 'SNAPSHOT',
            payload: {
                state: {
                    status: 'PAUSED',
                    currentIndex: 0,
                    positionSeconds: 16,
                    serverTimeToExecuteMs: nowClientMs(),
                    version: 3,
                    updatedAtMs: nowClientMs(),
                },
                queue: buildQueue([buildTrack(8)], 0),
                serverNowMs: nowClientMs(),
            },
        })
        await flushPromises(12)

        expect(audioStore.currentTime).toBe(16)
        expect(audioStore.duration).toBe(180)

        client.setState({
            phase: 'ready',
            clockOffsetMs: 0,
            roundTripEstimateMs: 10,
        })
        await flushPromises()

        client.emitMessage({
            type: 'SCHEDULED_ACTION',
            payload: {
                commandId: 'sync-web-test',
                serverTimeToExecuteMs: nowClientMs(),
                scheduledAction: {
                    action: 'PAUSE',
                    status: 'PAUSED',
                    currentIndex: 0,
                    positionSeconds: 16,
                    version: 3,
                },
            },
        } satisfies ScheduledActionMessage)
        await flushPromises(12)

        expect(audioStore.currentTrack?.id).toBe(8)
        expect(audioStore.isPlaying).toBe(false)
        expect(audioStore.currentTime).toBe(16)
        expect(audioStore.duration).toBe(180)
    })

    it('aggregates playback sync debug snapshot from diagnostics and inbound messages', async () => {
        fetchMock.mockResolvedValue(createResponse(32))

        const audioStore = useAudioStore()
        audioStore.connectPlaybackSync()
        const client = latestClient()

        client.setDiagnostics({
            socketState: 'open',
            reconnectAttempt: 2,
            measurements: [
                {
                    offsetMs: 5,
                    rttMs: 18,
                    recordedAtMs: nowClientMs(),
                },
            ],
            lastNtpRequestAtMs: nowClientMs() - 500,
            lastNtpResponseAtMs: nowClientMs() - 200,
        })

        client.emitMessage({
            type: 'SNAPSHOT',
            payload: {
                state: {
                    status: 'PAUSED',
                    currentIndex: 0,
                    positionSeconds: 12,
                    serverTimeToExecuteMs: nowClientMs(),
                    version: 5,
                    updatedAtMs: nowClientMs(),
                },
                queue: buildQueue([buildTrack(7)], 0),
                serverNowMs: nowClientMs(),
            },
        })
        client.emitMessage({
            type: 'ROOM_EVENT_LOAD_AUDIO_SOURCE',
            payload: {
                commandId: 'cmd-load-debug',
                currentIndex: 0,
                recordingId: 7,
            },
        } satisfies LoadAudioSourceMessage)
        await flushPromises(12)

        client.emitMessage({
            type: 'ROOM_EVENT_DEVICE_CHANGE',
            payload: {
                devices: [{ deviceId: 'web-test' }, { deviceId: 'web-phone' }],
            },
        })
        client.emitMessage({
            type: 'ERROR',
            payload: {
                code: 'INTERNAL_ERROR',
                message: 'sync broken',
            },
        })
        await flushPromises(12)

        const debug = audioStore.playbackSyncDebugSnapshot
        expect(debug.clientDiagnostics?.socketState).toBe('open')
        expect(debug.clientDiagnostics?.reconnectAttempt).toBe(2)
        expect(debug.latestSnapshot?.version).toBe(5)
        expect(debug.latestSnapshotReceivedAtMs).not.toBeNull()
        expect(debug.lastLoadAudioSource?.payload.commandId).toBe('cmd-load-debug')
        expect(debug.lastDeviceChange?.payload.devices).toEqual([
            { deviceId: 'web-test' },
            { deviceId: 'web-phone' },
        ])
        expect(debug.clientDiagnostics?.lastInboundEvent?.type).toBe('ERROR')
        expect(debug.error).toBe('sync broken')
    })

    it('records local execution diagnostics for scheduled playback', async () => {
        getRecordingMock.mockResolvedValueOnce(buildRecordingMetadata(6))
        fetchMock.mockResolvedValue(createResponse(25))

        const audioStore = useAudioStore()
        audioStore.connectPlaybackSync()
        const client = latestClient()
        client.setState({
            phase: 'ready',
            clockOffsetMs: 0,
            roundTripEstimateMs: 10,
        })

        client.emitMessage({
            type: 'SCHEDULED_ACTION',
            payload: {
                commandId: 'cmd-play-debug',
                serverTimeToExecuteMs: nowClientMs() + 250,
                scheduledAction: {
                    action: 'PLAY',
                    status: 'PLAYING',
                    currentIndex: 0,
                    positionSeconds: 4,
                    version: 6,
                },
            },
        } satisfies ScheduledActionMessage)
        await flushPromises(12)

        const debug = audioStore.playbackSyncDebugSnapshot
        expect(debug.lastScheduledAction?.payload.commandId).toBe('cmd-play-debug')
        expect(debug.lastLocalExecution?.action).toBe('PLAY')
        expect(debug.lastLocalExecution?.mediaFileId).toBe(2_006)
        expect(debug.lastLocalExecution?.waitMs).toBeGreaterThanOrEqual(0)
        expect(debug.lastLocalExecution?.scheduledOffset).toBe(4)
        expect(debug.currentBuffer?.mediaFileId).toBe(2_006)
    })

    it('uses the latest scheduled-action version for subsequent control commands', async () => {
        const audioStore = useAudioStore()
        audioStore.connectPlaybackSync()
        const client = latestClient()
        client.setState({
            phase: 'ready',
            clockOffsetMs: 0,
            roundTripEstimateMs: 10,
        })

        client.emitMessage({
            type: 'SNAPSHOT',
            payload: {
                state: {
                    status: 'PAUSED',
                    currentIndex: 0,
                    positionSeconds: 12,
                    serverTimeToExecuteMs: nowClientMs(),
                    version: 8,
                    updatedAtMs: nowClientMs(),
                },
                queue: buildQueue([buildTrack(6)], 0),
                serverNowMs: nowClientMs(),
            },
        })
        await flushPromises(12)

        client.emitMessage({
            type: 'SCHEDULED_ACTION',
            payload: {
                commandId: 'cmd-seek-version-bump',
                serverTimeToExecuteMs: nowClientMs(),
                scheduledAction: {
                    action: 'SEEK',
                    status: 'PAUSED',
                    currentIndex: 0,
                    positionSeconds: 18,
                    version: 9,
                },
            },
        } satisfies ScheduledActionMessage)
        await flushPromises(12)

        audioStore.pause()

        expect(client.sendPause).toHaveBeenLastCalledWith(
            expect.objectContaining({
                currentIndex: 0,
                version: 9,
            }),
        )
    })

    it('reflects queued play and disconnect in the debug snapshot', async () => {
        const audioStore = useAudioStore()
        const queuedTrack = buildTrack(11)

        await audioStore.play(queuedTrack)

        expect(audioStore.playbackSyncDebugSnapshot.queuedPlayIntent?.track.id).toBe(11)
        expect(audioStore.playbackSyncDebugSnapshot.canSendRealtimeControl).toBe(false)

        const client = latestClient()
        client.emitMessage({
            type: 'SNAPSHOT',
            payload: {
                state: {
                    status: 'PLAYING',
                    currentIndex: 0,
                    positionSeconds: 3,
                    serverTimeToExecuteMs: nowClientMs(),
                    version: 8,
                    updatedAtMs: nowClientMs(),
                },
                serverNowMs: nowClientMs(),
            },
        })
        await flushPromises()

        client.setState({
            phase: 'ready',
            clockOffsetMs: 0,
            roundTripEstimateMs: 12,
        })
        await flushPromises()

        expect(audioStore.playbackSyncDebugSnapshot.awaitingSyncRecovery).toBe(false)
        expect(client.sendPlay).toHaveBeenCalledTimes(1)

        audioStore.disconnectPlaybackSync()

        const debug = audioStore.playbackSyncDebugSnapshot
        expect(debug.clientDiagnostics).toBeNull()
        expect(debug.queuedPlayIntent).toBeNull()
        expect(debug.latestSnapshot).toBeNull()
        expect(debug.currentTrack).toBeNull()
    })

    it('clears local playback immediately when stop is invoked before sync is ready', async () => {
        const audioStore = useAudioStore()
        await audioStore.play(buildTrack(4))

        expect(audioStore.currentTrack?.id).toBe(4)
        expect(audioStore.canSendRealtimeControl).toBe(false)

        audioStore.stop()

        expect(audioStore.currentTrack).toBeNull()
        expect(audioStore.isPlaying).toBe(false)
        expect(audioStore.currentTime).toBe(0)
    })

    it('reuses the loaded buffer for repeated load-audio-source messages of the same track', async () => {
        getRecordingMock.mockResolvedValue(buildRecordingMetadata(7))
        fetchMock.mockResolvedValue(createResponse(45))

        const audioStore = useAudioStore()
        audioStore.connectPlaybackSync()
        const client = latestClient()

        const message: LoadAudioSourceMessage = {
            type: 'ROOM_EVENT_LOAD_AUDIO_SOURCE',
            payload: {
                commandId: 'cmd-preload-1',
                currentIndex: 0,
                recordingId: 7,
            },
        }

        client.emitMessage(message)
        await flushPromises(12)

        client.emitMessage({
            ...message,
            payload: {
                ...message.payload,
                commandId: 'cmd-preload-2',
            },
        })
        await flushPromises(12)

        expect(fetchMock).toHaveBeenCalledTimes(1)
        expect(client.sendAudioSourceLoaded).toHaveBeenNthCalledWith(1, {
            commandId: 'cmd-preload-1',
            currentIndex: 0,
            recordingId: 7,
        })
        expect(client.sendAudioSourceLoaded).toHaveBeenNthCalledWith(2, {
            commandId: 'cmd-preload-2',
            currentIndex: 0,
            recordingId: 7,
        })
        expect(audioStore.duration).toBe(45)
    })

    it('hydrates placeholder metadata after passive load-audio-source and acknowledges after metadata resolves', async () => {
        fetchMock.mockResolvedValue(createResponse(45))
        const recordingDeferred = createDeferred<ReturnType<typeof buildRecordingMetadata>>()
        getRecordingMock.mockReturnValueOnce(recordingDeferred.promise)

        const audioStore = useAudioStore()
        audioStore.connectPlaybackSync()
        const client = latestClient()

        client.emitMessage({
            type: 'ROOM_EVENT_LOAD_AUDIO_SOURCE',
            payload: {
                commandId: 'cmd-preload-metadata',
                currentIndex: 0,
                recordingId: 7,
            },
        } satisfies LoadAudioSourceMessage)
        await flushPromises(12)

        expect(getRecordingMock).toHaveBeenCalledWith({ id: 7 })
        expect(audioStore.currentTrack).toEqual(
            expect.objectContaining({
                id: 7,
                title: 'Recording #7',
                artist: 'Unknown Artist',
                cover: '',
            }),
        )
        expect(client.sendAudioSourceLoaded).not.toHaveBeenCalled()

        recordingDeferred.resolve(buildRecordingMetadata(7))
        await flushPromises(12)

        expect(audioStore.currentTrack).toEqual(
            expect.objectContaining({
                id: 7,
                title: 'Hydrated Track 7',
                artist: 'Hydrated Artist 7',
                cover: '/api/media/30007',
                workId: 10007,
            }),
        )
        expect(client.sendAudioSourceLoaded).toHaveBeenCalledWith({
            commandId: 'cmd-preload-metadata',
            currentIndex: 0,
            recordingId: 7,
        })
    })

    it('dedupes metadata requests across snapshot and scheduled action for the same passive track', async () => {
        const recordingDeferred = createDeferred<ReturnType<typeof buildRecordingMetadata>>()
        getRecordingMock.mockReturnValueOnce(recordingDeferred.promise)

        const audioStore = useAudioStore()
        audioStore.connectPlaybackSync()
        const client = latestClient()

        client.emitMessage({
            type: 'SNAPSHOT',
            payload: {
                state: {
                    status: 'PAUSED',
                    currentIndex: 0,
                    positionSeconds: 1,
                    serverTimeToExecuteMs: nowClientMs(),
                    version: 1,
                    updatedAtMs: nowClientMs(),
                },
                queue: buildQueue([buildTrack(12)], 0),
                serverNowMs: nowClientMs(),
            },
        })
        await flushPromises(12)

        client.emitMessage({
            type: 'SCHEDULED_ACTION',
            payload: {
                commandId: 'cmd-pause-12',
                serverTimeToExecuteMs: nowClientMs(),
                scheduledAction: {
                    action: 'PAUSE',
                    status: 'PAUSED',
                    currentIndex: 0,
                    positionSeconds: 1,
                    version: 2,
                },
            },
        } satisfies ScheduledActionMessage)
        await flushPromises(12)

        expect(getRecordingMock).toHaveBeenCalledTimes(1)

        recordingDeferred.resolve(buildRecordingMetadata(12))
        await flushPromises(12)

        expect(audioStore.currentTrack).toEqual(
            expect.objectContaining({
                id: 12,
                title: 'Hydrated Track 12',
                artist: 'Hydrated Artist 12',
                cover: '/api/media/30012',
            }),
        )
    })

    it('retries metadata hydration on later sync messages after a failed request', async () => {
        fetchMock.mockResolvedValue(createResponse(33))
        getRecordingMock
            .mockRejectedValueOnce(new Error('metadata unavailable'))
            .mockResolvedValueOnce(buildRecordingMetadata(14, { cover: null }))

        const audioStore = useAudioStore()
        audioStore.connectPlaybackSync()
        const client = latestClient()

        client.emitMessage({
            type: 'SNAPSHOT',
            payload: {
                state: {
                    status: 'PAUSED',
                    currentIndex: 0,
                    positionSeconds: 0,
                    serverTimeToExecuteMs: nowClientMs(),
                    version: 1,
                    updatedAtMs: nowClientMs(),
                },
                queue: buildQueue([buildTrack(14)], 0),
                serverNowMs: nowClientMs(),
            },
        })
        await flushPromises(12)

        expect(audioStore.currentTrack?.title).toBe('Track 14')
        expect(audioStore.currentTrack?.artist).toBe('Artist 14')
        expect(audioStore.currentTrack?.cover).toBe('/cover/14.jpg')

        client.emitMessage({
            type: 'ROOM_EVENT_LOAD_AUDIO_SOURCE',
            payload: {
                commandId: 'cmd-retry-metadata',
                currentIndex: 0,
                recordingId: 14,
            },
        } satisfies LoadAudioSourceMessage)
        await flushPromises(12)

        expect(getRecordingMock).toHaveBeenCalledTimes(2)
        expect(audioStore.currentTrack).toEqual(
            expect.objectContaining({
                id: 14,
                title: 'Hydrated Track 14',
                artist: 'Hydrated Artist 14',
                cover: '/cover/14.jpg',
                src: '/api/media/2014',
                mediaFileId: 2_014,
                workId: 10014,
            }),
        )
    })

    it('ignores late metadata responses after the current passive track changes', async () => {
        const firstDeferred = createDeferred<ReturnType<typeof buildRecordingMetadata>>()
        const secondDeferred = createDeferred<ReturnType<typeof buildRecordingMetadata>>()
        getRecordingMock
            .mockReturnValueOnce(firstDeferred.promise)
            .mockReturnValueOnce(secondDeferred.promise)

        const audioStore = useAudioStore()
        audioStore.connectPlaybackSync()
        const client = latestClient()

        client.emitMessage({
            type: 'SNAPSHOT',
            payload: {
                state: {
                    status: 'PAUSED',
                    currentIndex: 0,
                    positionSeconds: 0,
                    serverTimeToExecuteMs: nowClientMs(),
                    version: 1,
                    updatedAtMs: nowClientMs(),
                },
                queue: buildQueue([buildTrack(21)], 0),
                serverNowMs: nowClientMs(),
            },
        })
        await flushPromises(12)

        client.emitMessage({
            type: 'SNAPSHOT',
            payload: {
                state: {
                    status: 'PAUSED',
                    currentIndex: 0,
                    positionSeconds: 0,
                    serverTimeToExecuteMs: nowClientMs(),
                    version: 2,
                    updatedAtMs: nowClientMs(),
                },
                queue: buildQueue([buildTrack(22)], 0),
                serverNowMs: nowClientMs(),
            },
        })
        await flushPromises(12)

        firstDeferred.resolve(buildRecordingMetadata(21))
        await flushPromises(12)

        expect(audioStore.currentTrack).toEqual(
            expect.objectContaining({
                id: 22,
                title: 'Track 22',
                artist: 'Artist 22',
            }),
        )

        secondDeferred.resolve(buildRecordingMetadata(22))
        await flushPromises(12)

        expect(audioStore.currentTrack).toEqual(
            expect.objectContaining({
                id: 22,
                title: 'Hydrated Track 22',
                artist: 'Hydrated Artist 22',
                cover: '/api/media/30022',
            }),
        )
    })

    it('evicts the oldest known track once the track cache exceeds capacity', async () => {
        const audioStore = useAudioStore()
        for (let index = 1; index <= 201; index += 1) {
            await audioStore.play(buildTrack(index))
        }

        const client = latestClient()
        client.emitMessage({
            type: 'SNAPSHOT',
            payload: {
                state: {
                    status: 'PAUSED',
                    currentIndex: 0,
                    positionSeconds: 0,
                    serverTimeToExecuteMs: nowClientMs(),
                    version: 1,
                    updatedAtMs: nowClientMs(),
                },
                queue: buildQueue([buildTrack(2)], 0),
                serverNowMs: nowClientMs(),
            },
        })
        await flushPromises()

        expect(audioStore.currentTrack?.title).toBe('Track 2')

        client.emitMessage({
            type: 'SNAPSHOT',
            payload: {
                state: {
                    status: 'PAUSED',
                    currentIndex: 0,
                    positionSeconds: 0,
                    serverTimeToExecuteMs: nowClientMs(),
                    version: 2,
                    updatedAtMs: nowClientMs(),
                },
                queue: buildQueue([buildTrack(1)], 0),
                serverNowMs: nowClientMs(),
            },
        })
        await flushPromises()

        expect(audioStore.currentTrack?.title).toBe('Track 1')
    })

    it('ignores late buffer loads after disconnect tears down local playback state', async () => {
        getRecordingMock.mockResolvedValueOnce(buildRecordingMetadata(9))
        const deferredResponse = createDeferred<Response>()
        fetchMock.mockReturnValueOnce(deferredResponse.promise)

        const audioStore = useAudioStore()
        audioStore.connectPlaybackSync()
        const client = latestClient()

        client.emitMessage({
            type: 'ROOM_EVENT_LOAD_AUDIO_SOURCE',
            payload: {
                commandId: 'cmd-late-load',
                currentIndex: 0,
                recordingId: 9,
            },
        } satisfies LoadAudioSourceMessage)
        await flushPromises()

        expect(audioStore.isLoading).toBe(true)

        audioStore.disconnectPlaybackSync()

        expect(audioStore.currentTrack).toBeNull()
        expect(audioStore.isLoading).toBe(false)
        expect(audioStore.duration).toBe(0)
        expect(MockAudioContext.instances[0]?.close).toHaveBeenCalledTimes(1)

        deferredResponse.resolve(createResponse(60))
        await flushPromises(12)

        expect(audioStore.currentTrack).toBeNull()
        expect(audioStore.duration).toBe(0)
        expect(client.sendAudioSourceLoaded).not.toHaveBeenCalled()
    })

    it('fully tears down scheduled playback when disconnecting sync', async () => {
        getRecordingMock.mockResolvedValueOnce(buildRecordingMetadata(6))
        fetchMock.mockResolvedValue(createResponse(25))

        const audioStore = useAudioStore()
        audioStore.connectPlaybackSync()
        const client = latestClient()
        client.setState({
            phase: 'ready',
            clockOffsetMs: 0,
            roundTripEstimateMs: 10,
        })

        client.emitMessage({
            type: 'SCHEDULED_ACTION',
            payload: {
                commandId: 'cmd-play-disconnect',
                serverTimeToExecuteMs: nowClientMs(),
                scheduledAction: {
                    action: 'PLAY',
                    status: 'PLAYING',
                    currentIndex: 0,
                    positionSeconds: 4,
                    version: 6,
                },
            },
        } satisfies ScheduledActionMessage)
        await flushPromises(12)

        expect(audioStore.isPlaying).toBe(true)
        expect(audioStore.currentTrack?.id).toBe(6)

        audioStore.disconnectPlaybackSync()

        expect(audioStore.currentTrack).toBeNull()
        expect(audioStore.isPlaying).toBe(false)
        expect(audioStore.currentTime).toBe(0)
        expect(audioStore.duration).toBe(0)
        expect(audioStore.canSendRealtimeControl).toBe(false)
        expect(MockAudioContext.instances[0]?.close).toHaveBeenCalledTimes(1)
    })
})
