import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAudioStore, type AudioTrack } from '@/stores/audio'

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

    readonly destination = {}
    readonly gain = createMockGainNode()
    readonly sourceNodes: MockAudioBufferSourceNode[] = []
    currentTime = 0
    state = 'running'

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
})

const createResponse = (duration: number, status = 200) => {
    return new Response(new Uint8Array([duration]).buffer, { status })
}

const createDeferred = <T>() => {
    let resolve!: (value: T) => void
    let reject!: (reason?: unknown) => void
    const promise = new Promise<T>((innerResolve, innerReject) => {
        resolve = innerResolve
        reject = innerReject
    })

    return { promise, resolve, reject }
}

const flushPromises = async (times = 4) => {
    for (let index = 0; index < times; index += 1) {
        await Promise.resolve()
    }
}

const setContextTime = (nextTime: number) => {
    MockAudioContext.instances.forEach((instance) => {
        instance.currentTime = nextTime
    })
}

const runAnimationFrame = (timestamp = 0) => {
    const callbacks = [...animationFrameCallbacks.values()]
    animationFrameCallbacks.clear()
    callbacks.forEach((callback) => {
        callback(timestamp)
    })
}

describe('audio store', () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        window.localStorage.clear()
        nextAnimationFrameId = 1
        animationFrameCallbacks = new Map()
        resumeError = null
        MockAudioContext.instances = []
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

    it('loads and starts playback for a new track', async () => {
        fetchMock.mockResolvedValue(createResponse(120))

        const audioStore = useAudioStore()
        const track = buildTrack(1)

        audioStore.play(track)

        expect(audioStore.currentTrack).toEqual(track)
        expect(audioStore.isPlaying).toBe(true)
        expect(audioStore.isLoading).toBe(true)

        await flushPromises()

        const context = MockAudioContext.instances[0]
        const source = context?.sourceNodes[0]

        expect(context).toBeTruthy()
        expect(audioStore.duration).toBe(120)
        expect(audioStore.isLoading).toBe(false)
        expect(source?.start).toHaveBeenCalledWith(0, 0)

        setContextTime(8)
        runAnimationFrame()

        expect(audioStore.currentTime).toBeCloseTo(8, 5)
    })

    it('toggles same-track play state and recreates source on seek', async () => {
        fetchMock.mockResolvedValue(createResponse(90))

        const audioStore = useAudioStore()
        const track = buildTrack(2)

        audioStore.play(track)
        await flushPromises()

        const context = MockAudioContext.instances[0]
        const firstSource = context?.sourceNodes[0]
        expect(firstSource).toBeTruthy()

        setContextTime(10)
        runAnimationFrame()

        audioStore.play(track)
        expect(audioStore.isPlaying).toBe(false)
        expect(audioStore.currentTime).toBeCloseTo(10, 5)
        expect(firstSource?.stop).toHaveBeenCalledTimes(1)

        audioStore.play(track)
        const resumedSource = context?.sourceNodes[1]
        expect(audioStore.isPlaying).toBe(true)
        expect(resumedSource?.start).toHaveBeenCalledWith(0, 10)

        audioStore.seek(30)
        const seekedSource = context?.sourceNodes[2]
        expect(seekedSource?.start).toHaveBeenCalledWith(0, 30)
        expect(audioStore.currentTime).toBe(30)
    })

    it('ignores stale async loads when tracks are switched quickly', async () => {
        const firstFetch = createDeferred<Response>()
        const secondFetch = createDeferred<Response>()
        fetchMock.mockReturnValueOnce(firstFetch.promise).mockReturnValueOnce(secondFetch.promise)

        const audioStore = useAudioStore()
        const firstTrack = buildTrack(3, '/audio/first.mp3')
        const secondTrack = buildTrack(4, '/audio/second.mp3')

        audioStore.play(firstTrack)
        audioStore.play(secondTrack)

        secondFetch.resolve(createResponse(240))
        await flushPromises()

        const context = MockAudioContext.instances[0]
        expect(audioStore.currentTrack?.id).toBe(secondTrack.id)
        expect(audioStore.duration).toBe(240)
        expect(context?.sourceNodes).toHaveLength(1)

        firstFetch.resolve(createResponse(180))
        await flushPromises()

        expect(audioStore.currentTrack?.id).toBe(secondTrack.id)
        expect(audioStore.duration).toBe(240)
        expect(context?.sourceNodes).toHaveLength(1)
    })

    it('sets playback error state when audio loading fails', async () => {
        fetchMock.mockRejectedValue(new Error('network failed'))

        const audioStore = useAudioStore()
        audioStore.play(buildTrack(5))

        await flushPromises()

        expect(audioStore.isLoading).toBe(false)
        expect(audioStore.isPlaying).toBe(false)
        expect(audioStore.error).toBe('Unable to play audio')
        expect(audioStore.currentTrack?.id).toBe(5)
    })

    it('surfaces audio-context resume failures as playback errors', async () => {
        const deferredFetch = createDeferred<Response>()
        resumeError = new Error('resume blocked')
        fetchMock.mockReturnValue(deferredFetch.promise)

        const audioStore = useAudioStore()
        audioStore.play(buildTrack(7))

        await flushPromises()

        expect(audioStore.isPlaying).toBe(false)
        expect(audioStore.isLoading).toBe(false)
        expect(audioStore.error).toBe('Unable to play audio')

        deferredFetch.resolve(createResponse(60))
        await flushPromises()

        const context = MockAudioContext.instances[0]
        expect(audioStore.duration).toBe(60)
        expect(context?.sourceNodes).toHaveLength(0)
    })

    it('clamps seek time to zero for zero-duration buffers', async () => {
        fetchMock.mockResolvedValue(createResponse(0))

        const audioStore = useAudioStore()
        audioStore.play(buildTrack(8))

        await flushPromises()

        const context = MockAudioContext.instances[0]
        audioStore.seek(15)

        expect(audioStore.currentTime).toBe(0)
        expect(context?.sourceNodes[1]?.start).toHaveBeenCalledWith(0, 0)
    })

    it('resets playhead when the current source ends naturally', async () => {
        fetchMock.mockResolvedValue(createResponse(75))

        const audioStore = useAudioStore()
        audioStore.play(buildTrack(6))

        await flushPromises()

        const context = MockAudioContext.instances[0]
        const source = context?.sourceNodes[0]

        setContextTime(12)
        runAnimationFrame()
        source?.emitEnded()

        expect(audioStore.isPlaying).toBe(false)
        expect(audioStore.currentTime).toBe(0)
    })
})
