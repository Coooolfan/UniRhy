import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

type MockPlaybackSyncClientState = {
    deviceId: string
    phase: string
    clockOffsetMs: number
    roundTripEstimateMs: number
}

type MockPlaybackSyncClientCallbacks = {
    onMessage?: (message: unknown) => void
    onStateChange?: (state: MockPlaybackSyncClientState) => void
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
    getState: () => MockPlaybackSyncClientState
    setState: (nextState: Partial<MockPlaybackSyncClientState>) => void
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
    this.callbacks.onStateChange?.(this.state)
}

function emitMockPlaybackSyncClientMessage(this: MockPlaybackSyncClientInstance, message: unknown) {
    this.callbacks.onMessage?.(message)
}

const playbackSyncMockState = vi.hoisted(() => ({
    clients: [] as MockPlaybackSyncClientInstance[],
}))

vi.mock('@/services/playbackSyncClient', () => {
    function PlaybackSyncClient(
        this: MockPlaybackSyncClientInstance,
        callbacks: MockPlaybackSyncClientCallbacks = {},
    ) {
        this.connect = vi.fn()
        this.disconnect = vi.fn()
        this.sendPlay = vi.fn(() => true)
        this.sendPause = vi.fn(() => true)
        this.sendSeek = vi.fn(() => true)
        this.sendAudioSourceLoaded = vi.fn(() => true)
        this.requestSync = vi.fn(() => true)
        this.callbacks = callbacks
        this.state = {
            deviceId: 'web-test',
            phase: 'connecting',
            clockOffsetMs: 0,
            roundTripEstimateMs: 0,
        }
        playbackSyncMockState.clients.push(this)
    }

    PlaybackSyncClient.prototype.getState = getMockPlaybackSyncClientState
    PlaybackSyncClient.prototype.setState = setMockPlaybackSyncClientState
    PlaybackSyncClient.prototype.emitMessage = emitMockPlaybackSyncClientMessage

    return {
        PlaybackSyncClient,
    }
})

import { useAudioStore, type AudioTrack } from '@/stores/audio'
import type {
    LoadAudioSourceMessage,
    ScheduledActionMessage,
} from '@/services/playbackSyncProtocol'
import { nowClientMs } from '@/utils/time'

let nextAnimationFrameId = 1
let animationFrameCallbacks = new Map<number, FrameRequestCallback>()
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
        playbackSyncMockState.clients.length = 0
        nextAnimationFrameId = 1
        animationFrameCallbacks = new Map()
        resumeError = null
        MockAudioContext.instances = []
        MockAudioContext.defaultState = 'running'
        fetchMock.mockReset()

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
                recordingId: secondTrack.id,
                mediaFileId: secondTrack.mediaFileId,
                positionSeconds: 0,
            }),
        )
    })

    it('loads audio on load-audio-source and acknowledges after decoding', async () => {
        fetchMock.mockResolvedValue(createResponse(45))

        const audioStore = useAudioStore()
        audioStore.connectPlaybackSync()
        const client = latestClient()

        const message: LoadAudioSourceMessage = {
            type: 'ROOM_EVENT_LOAD_AUDIO_SOURCE',
            payload: {
                commandId: 'cmd-play-1',
                recordingId: 7,
                mediaFileId: 2_007,
                sourceUrl: '/api/media/2007',
            },
        }

        client.emitMessage(message)
        await flushPromises(12)

        expect(fetchMock).toHaveBeenCalledWith('/api/media/2007')
        expect(client.sendAudioSourceLoaded).toHaveBeenCalledWith({
            commandId: 'cmd-play-1',
            recordingId: 7,
            mediaFileId: 2_007,
        })
        expect(audioStore.currentTrack?.id).toBe(7)
        expect(audioStore.duration).toBe(45)
    })

    it('ignores stale scheduled actions with a lower version', async () => {
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
                    recordingId: 5,
                    mediaFileId: 2_005,
                    sourceUrl: '/api/media/2005',
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
                    recordingId: null,
                    mediaFileId: null,
                    sourceUrl: null,
                    positionSeconds: 0,
                    version: 1,
                },
            },
        } satisfies ScheduledActionMessage)
        await flushPromises(12)

        expect(audioStore.currentTrack?.id).toBe(5)
        expect(audioStore.isPlaying).toBe(true)
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
                    recordingId: 8,
                    mediaFileId: 2_008,
                    sourceUrl: '/api/media/2008',
                    positionSeconds: 16,
                    serverTimeToExecuteMs: performance.timeOrigin + performance.now(),
                    version: 3,
                    updatedAtMs: performance.timeOrigin + performance.now(),
                },
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
        fetchMock.mockResolvedValue(createResponse(45))

        const audioStore = useAudioStore()
        audioStore.connectPlaybackSync()
        const client = latestClient()

        const message: LoadAudioSourceMessage = {
            type: 'ROOM_EVENT_LOAD_AUDIO_SOURCE',
            payload: {
                commandId: 'cmd-preload-1',
                recordingId: 7,
                mediaFileId: 2_007,
                sourceUrl: '/api/media/2007',
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
            recordingId: 7,
            mediaFileId: 2_007,
        })
        expect(client.sendAudioSourceLoaded).toHaveBeenNthCalledWith(2, {
            commandId: 'cmd-preload-2',
            recordingId: 7,
            mediaFileId: 2_007,
        })
        expect(audioStore.duration).toBe(45)
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
                    recordingId: 2,
                    mediaFileId: 1_002,
                    sourceUrl: '/api/media/1002',
                    positionSeconds: 0,
                    serverTimeToExecuteMs: nowClientMs(),
                    version: 1,
                    updatedAtMs: nowClientMs(),
                },
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
                    recordingId: 1,
                    mediaFileId: 1_001,
                    sourceUrl: '/api/media/1001',
                    positionSeconds: 0,
                    serverTimeToExecuteMs: nowClientMs(),
                    version: 2,
                    updatedAtMs: nowClientMs(),
                },
                serverNowMs: nowClientMs(),
            },
        })
        await flushPromises()

        expect(audioStore.currentTrack?.title).toBe('Recording #1')
    })

    it('ignores late buffer loads after disconnect tears down local playback state', async () => {
        const deferredResponse = createDeferred<Response>()
        fetchMock.mockReturnValueOnce(deferredResponse.promise)

        const audioStore = useAudioStore()
        audioStore.connectPlaybackSync()
        const client = latestClient()

        client.emitMessage({
            type: 'ROOM_EVENT_LOAD_AUDIO_SOURCE',
            payload: {
                commandId: 'cmd-late-load',
                recordingId: 9,
                mediaFileId: 2_009,
                sourceUrl: '/api/media/2009',
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
                    recordingId: 6,
                    mediaFileId: 2_006,
                    sourceUrl: '/api/media/2006',
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
