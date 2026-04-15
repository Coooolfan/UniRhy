import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { effectScope, nextTick, reactive } from 'vue'
import { useMediaSession } from '@/composables/useMediaSession'

type TestTrack = {
    title: string
    artist: string
    cover?: string
} | null

class MockMediaMetadata {
    album = ''
    title?: string
    artist?: string
    artwork?: MediaImage[]

    constructor(init?: MediaMetadataInit) {
        this.title = init?.title
        this.artist = init?.artist
        this.artwork = init?.artwork
    }
}

type MockMediaSession = {
    metadata: MediaMetadata | null
    playbackState: MediaSessionPlaybackState
    handlers: Partial<Record<string, MediaSessionActionHandler | null>>
    setActionHandler: ReturnType<typeof vi.fn>
    setPositionState: ReturnType<typeof vi.fn>
}

const createAudioStore = () =>
    reactive({
        currentTrack: null as TestTrack,
        isPlaying: false,
        currentTime: 0,
        duration: 0,
        canNavigateQueue: false,
        resume: vi.fn<() => void>(),
        pause: vi.fn<() => void>(),
        stop: vi.fn<() => void>(),
        playPrevious: vi.fn<() => void>(),
        playNext: vi.fn<() => void>(),
        seek: vi.fn<(time: number) => void>(),
    })

const createMediaSession = (): MockMediaSession => {
    const handlers: MockMediaSession['handlers'] = {}

    return {
        metadata: null,
        playbackState: 'none',
        handlers,
        setActionHandler: vi.fn((action: string, handler: MediaSessionActionHandler | null) => {
            handlers[action] = handler
        }),
        setPositionState: vi.fn(),
    }
}

const originalMediaMetadata = globalThis.MediaMetadata
const originalNavigatorMediaSession = navigator.mediaSession

describe('useMediaSession', () => {
    beforeEach(() => {
        vi.restoreAllMocks()
        Object.defineProperty(globalThis, 'MediaMetadata', {
            configurable: true,
            writable: true,
            value: MockMediaMetadata,
        })
    })

    afterEach(() => {
        globalThis.MediaMetadata = originalMediaMetadata
        Object.defineProperty(navigator, 'mediaSession', {
            configurable: true,
            writable: true,
            value: originalNavigatorMediaSession,
        })
    })

    it('publishes metadata and playback state for the current track', async () => {
        const audioStore = createAudioStore()
        const mediaSession = createMediaSession()
        Object.defineProperty(navigator, 'mediaSession', {
            configurable: true,
            writable: true,
            value: mediaSession,
        })

        const scope = effectScope()
        scope.run(() => {
            useMediaSession(audioStore)
        })

        audioStore.currentTrack = {
            title: 'Track One',
            artist: 'Artist One',
            cover: 'https://example.com/cover.jpg',
        }
        audioStore.isPlaying = true
        await nextTick()

        expect(mediaSession.metadata).toBeInstanceOf(MockMediaMetadata)
        expect(mediaSession.metadata).toMatchObject({
            title: 'Track One',
            artist: 'Artist One',
            artwork: [{ src: 'https://example.com/cover.jpg' }],
        })
        expect(mediaSession.playbackState).toBe('playing')

        scope.stop()
    })

    it('updates position state when playback time changes', async () => {
        const audioStore = createAudioStore()
        const mediaSession = createMediaSession()
        Object.defineProperty(navigator, 'mediaSession', {
            configurable: true,
            writable: true,
            value: mediaSession,
        })

        const scope = effectScope()
        scope.run(() => {
            useMediaSession(audioStore)
        })

        audioStore.currentTrack = {
            title: 'Track Two',
            artist: 'Artist Two',
            cover: '',
        }
        audioStore.duration = 120
        audioStore.currentTime = 45
        await nextTick()

        expect(mediaSession.setPositionState).toHaveBeenLastCalledWith({
            duration: 120,
            playbackRate: 1,
            position: 45,
        })

        audioStore.currentTime = 240
        await nextTick()

        expect(mediaSession.setPositionState).toHaveBeenLastCalledWith({
            duration: 120,
            playbackRate: 1,
            position: 120,
        })

        scope.stop()
    })

    it('maps system media actions to audio store controls', async () => {
        const audioStore = createAudioStore()
        const mediaSession = createMediaSession()
        Object.defineProperty(navigator, 'mediaSession', {
            configurable: true,
            writable: true,
            value: mediaSession,
        })

        const scope = effectScope()
        scope.run(() => {
            useMediaSession(audioStore)
        })

        audioStore.currentTrack = {
            title: 'Track Three',
            artist: 'Artist Three',
            cover: 'https://example.com/cover-3.jpg',
        }
        audioStore.canNavigateQueue = true
        audioStore.currentTime = 50
        await nextTick()

        mediaSession.handlers.play?.({ action: 'play' })
        mediaSession.handlers.pause?.({ action: 'pause' })
        mediaSession.handlers.stop?.({ action: 'stop' })
        mediaSession.handlers.previoustrack?.({ action: 'previoustrack' })
        mediaSession.handlers.nexttrack?.({ action: 'nexttrack' })
        mediaSession.handlers.seekto?.({ action: 'seekto', seekTime: 25 })
        mediaSession.handlers.seekbackward?.({ action: 'seekbackward', seekOffset: 15 })
        mediaSession.handlers.seekforward?.({ action: 'seekforward' })

        expect(audioStore.resume).toHaveBeenCalledTimes(1)
        expect(audioStore.pause).toHaveBeenCalledTimes(1)
        expect(audioStore.stop).toHaveBeenCalledTimes(1)
        expect(audioStore.playPrevious).toHaveBeenCalledTimes(1)
        expect(audioStore.playNext).toHaveBeenCalledTimes(1)
        expect(audioStore.seek).toHaveBeenNthCalledWith(1, 25)
        expect(audioStore.seek).toHaveBeenNthCalledWith(2, 35)
        expect(audioStore.seek).toHaveBeenNthCalledWith(3, 60)

        scope.stop()
    })

    it('clears metadata and handlers when the current track is removed or scope is disposed', async () => {
        const audioStore = createAudioStore()
        const mediaSession = createMediaSession()
        Object.defineProperty(navigator, 'mediaSession', {
            configurable: true,
            writable: true,
            value: mediaSession,
        })

        const scope = effectScope()
        scope.run(() => {
            useMediaSession(audioStore)
        })

        audioStore.currentTrack = {
            title: 'Track Four',
            artist: 'Artist Four',
            cover: 'https://example.com/cover-4.jpg',
        }
        audioStore.canNavigateQueue = true
        await nextTick()

        expect(mediaSession.handlers.play).toBeTypeOf('function')
        audioStore.currentTrack = null
        await nextTick()

        expect(mediaSession.metadata).toBeNull()
        expect(mediaSession.playbackState).toBe('none')
        expect(mediaSession.handlers.play).toBeNull()
        expect(mediaSession.handlers.nexttrack).toBeNull()

        audioStore.currentTrack = {
            title: 'Track Five',
            artist: 'Artist Five',
            cover: 'https://example.com/cover-5.jpg',
        }
        await nextTick()

        scope.stop()

        expect(mediaSession.metadata).toBeNull()
        expect(mediaSession.playbackState).toBe('none')
        expect(mediaSession.handlers.play).toBeNull()
        expect(mediaSession.handlers.seekto).toBeNull()
    })

    it('degrades gracefully when media session is unsupported or position state throws', async () => {
        const audioStore = createAudioStore()

        Object.defineProperty(navigator, 'mediaSession', {
            configurable: true,
            writable: true,
            value: undefined,
        })

        const unsupportedScope = effectScope()
        expect(() => {
            unsupportedScope.run(() => {
                useMediaSession(audioStore)
            })
        }).not.toThrow()
        unsupportedScope.stop()

        const mediaSession = createMediaSession()
        mediaSession.setPositionState.mockImplementation(() => {
            throw new Error('unsupported')
        })
        Object.defineProperty(navigator, 'mediaSession', {
            configurable: true,
            writable: true,
            value: mediaSession,
        })

        const scope = effectScope()
        expect(() => {
            scope.run(() => {
                useMediaSession(audioStore)
            })
        }).not.toThrow()

        audioStore.currentTrack = {
            title: 'Track Six',
            artist: 'Artist Six',
            cover: '',
        }
        audioStore.duration = 30
        audioStore.currentTime = 10
        await nextTick()

        expect(mediaSession.playbackState).toBe('paused')

        scope.stop()
    })
})
