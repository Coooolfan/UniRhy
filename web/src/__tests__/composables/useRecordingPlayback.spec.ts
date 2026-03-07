import { describe, it, expect, beforeEach, vi } from 'vitest'
import { ref } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { useRecordingPlayback, type PlayableRecording } from '@/composables/useRecordingPlayback'

type TestRecording = PlayableRecording

describe('useRecordingPlayback', () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        vi.stubGlobal(
            'WebSocket',
            class MockWebSocket {
                static readonly CONNECTING = 0
                static readonly OPEN = 1
                static readonly CLOSING = 2
                static readonly CLOSED = 3
                readonly readyState = MockWebSocket.CONNECTING
                readonly listeners: unknown[] = []

                readonly addEventListener = vi.fn((_type: string, listener: unknown) => {
                    this.listeners.push(listener)
                })

                readonly close = vi.fn(() => {
                    this.listeners.length = 0
                })

                readonly send = vi.fn(() => {
                    return undefined
                })
            },
        )
    })

    it('does not play when there is no target or no audio source', () => {
        const recordings = ref<TestRecording[]>([
            { id: 1, title: 'A', artist: 'B', cover: '', audioSrc: undefined },
        ])
        const currentRecordingId = ref<number | null>(null)

        const { audioStore, handlePlay } = useRecordingPlayback({
            recordings,
            currentRecordingId,
            fallbackCover: () => '/fallback.jpg',
        })

        handlePlay()
        expect(audioStore.currentTrack).toBeNull()

        currentRecordingId.value = 1
        handlePlay()
        expect(audioStore.currentTrack).toBeNull()
        expect(audioStore.isPlaying).toBe(false)
    })

    it('queues target recording with fallback cover and trackExtra while sync is not ready', () => {
        const recordings = ref<TestRecording[]>([
            {
                id: 1,
                title: 'Rec 1',
                artist: 'Artist 1',
                cover: '',
                audioSrc: '/audio/1.mp3',
                mediaFileId: 2001,
            },
        ])
        const currentRecordingId = ref<number | null>(1)

        const { audioStore, handlePlay } = useRecordingPlayback({
            recordings,
            currentRecordingId,
            fallbackCover: () => '/fallback.jpg',
            trackExtra: () => ({ workId: 42 }),
        })

        handlePlay()

        expect(audioStore.currentTrack).toEqual({
            id: 1,
            title: 'Rec 1',
            artist: 'Artist 1',
            cover: '/fallback.jpg',
            src: '/audio/1.mp3',
            mediaFileId: 2001,
            workId: 42,
        })
        expect(audioStore.isPlaying).toBe(false)
    })

    it('double click updates current selection and queues the chosen recording', () => {
        const recordings = ref<TestRecording[]>([
            {
                id: 1,
                title: 'Rec 1',
                artist: 'Artist 1',
                cover: '',
                audioSrc: '/audio/1.mp3',
                mediaFileId: 2001,
            },
            {
                id: 2,
                title: 'Rec 2',
                artist: 'Artist 2',
                cover: '/cover/2.jpg',
                audioSrc: '/audio/2.mp3',
                mediaFileId: 2002,
            },
        ])
        const currentRecordingId = ref<number | null>(null)

        const { audioStore, onRecordingDoubleClick } = useRecordingPlayback({
            recordings,
            currentRecordingId,
            fallbackCover: () => '/fallback.jpg',
        })

        const secondRecording = recordings.value[1]
        if (!secondRecording) {
            throw new Error('Missing second recording in test setup')
        }

        onRecordingDoubleClick(secondRecording)

        expect(currentRecordingId.value).toBe(2)
        expect(audioStore.currentTrack?.id).toBe(2)
        expect(audioStore.currentTrack?.cover).toBe('/cover/2.jpg')
    })

    it('computes playable and current-playing states', () => {
        const recordings = ref<TestRecording[]>([
            {
                id: 1,
                title: 'Rec 1',
                artist: 'Artist 1',
                cover: '',
                audioSrc: '/audio/1.mp3',
                mediaFileId: 2001,
            },
            { id: 2, title: 'Rec 2', artist: 'Artist 2', cover: '', audioSrc: undefined },
        ])
        const currentRecordingId = ref<number | null>(1)

        const { audioStore, hasPlayableRecording, isCurrentRecordingPlaying, handlePlay } =
            useRecordingPlayback({
                recordings,
                currentRecordingId,
                fallbackCover: () => '/fallback.jpg',
            })

        expect(hasPlayableRecording.value).toBe(true)
        expect(isCurrentRecordingPlaying.value).toBe(false)

        handlePlay()
        expect(audioStore.currentTrack?.id).toBe(1)
        expect(isCurrentRecordingPlaying.value).toBe(false)

        currentRecordingId.value = 2
        expect(isCurrentRecordingPlaying.value).toBe(false)
    })
})
