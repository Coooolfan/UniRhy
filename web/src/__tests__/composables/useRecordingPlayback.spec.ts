import { describe, it, expect, beforeEach } from 'vitest'
import { ref } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { useRecordingPlayback, type PlayableRecording } from '@/composables/useRecordingPlayback'

type TestRecording = PlayableRecording

describe('useRecordingPlayback', () => {
    beforeEach(() => {
        setActivePinia(createPinia())
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

    it('plays target recording with fallback cover and trackExtra', () => {
        const recordings = ref<TestRecording[]>([
            { id: 1, title: 'Rec 1', artist: 'Artist 1', cover: '', audioSrc: '/audio/1.mp3' },
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
            workId: 42,
        })
        expect(audioStore.isPlaying).toBe(true)
    })

    it('double click updates current selection and starts playing', () => {
        const recordings = ref<TestRecording[]>([
            { id: 1, title: 'Rec 1', artist: 'Artist 1', cover: '', audioSrc: '/audio/1.mp3' },
            {
                id: 2,
                title: 'Rec 2',
                artist: 'Artist 2',
                cover: '/cover/2.jpg',
                audioSrc: '/audio/2.mp3',
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
            { id: 1, title: 'Rec 1', artist: 'Artist 1', cover: '', audioSrc: '/audio/1.mp3' },
            { id: 2, title: 'Rec 2', artist: 'Artist 2', cover: '', audioSrc: undefined },
        ])
        const currentRecordingId = ref<number | null>(1)

        const { hasPlayableRecording, isCurrentRecordingPlaying, handlePlay } =
            useRecordingPlayback({
                recordings,
                currentRecordingId,
                fallbackCover: () => '/fallback.jpg',
            })

        expect(hasPlayableRecording.value).toBe(true)
        expect(isCurrentRecordingPlaying.value).toBe(false)

        handlePlay()
        expect(isCurrentRecordingPlaying.value).toBe(true)

        currentRecordingId.value = 2
        expect(isCurrentRecordingPlaying.value).toBe(false)
    })
})
