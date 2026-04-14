import { describe, it, expect, beforeEach, vi } from 'vitest'
import { reactive, ref } from 'vue'
import { useRecordingPlayback, type PlayableRecording } from '@/composables/useRecordingPlayback'

type TestRecording = PlayableRecording

const audioStore = reactive({
    currentTrack: null as {
        id: number
        title: string
        artist: string
        cover: string
        src: string
        mediaFileId: number
        workId?: number
    } | null,
    isPlaying: false,
    replaceQueueAndPlay: vi.fn((tracks: TestRecording[], currentIndex: number) => {
        const targetTrack = tracks[currentIndex]
        if (targetTrack?.mediaFileId === undefined || targetTrack?.audioSrc === undefined) {
            return Promise.resolve()
        }

        audioStore.currentTrack = {
            id: targetTrack.id,
            title: targetTrack.title,
            artist: targetTrack.artist,
            cover: targetTrack.cover,
            src: targetTrack.audioSrc,
            mediaFileId: targetTrack.mediaFileId,
        }
        return Promise.resolve()
    }),
})

vi.mock('@/stores/audio', () => ({
    useAudioStore: () => audioStore,
}))

describe('useRecordingPlayback', () => {
    beforeEach(() => {
        audioStore.currentTrack = null
        audioStore.isPlaying = false
        audioStore.replaceQueueAndPlay.mockReset()
        audioStore.replaceQueueAndPlay.mockImplementation(
            (tracks: TestRecording[], currentIndex: number) => {
                const targetTrack = tracks[currentIndex]
                if (targetTrack?.mediaFileId === undefined || targetTrack?.audioSrc === undefined) {
                    return Promise.resolve()
                }

                audioStore.currentTrack = {
                    id: targetTrack.id,
                    title: targetTrack.title,
                    artist: targetTrack.artist,
                    cover: targetTrack.cover,
                    src: targetTrack.audioSrc,
                    mediaFileId: targetTrack.mediaFileId,
                }
                return Promise.resolve()
            },
        )
    })

    it('does not play when there is no target or no audio source', () => {
        const recordings = ref<TestRecording[]>([
            { id: 1, title: 'A', artist: 'B', cover: '', audioSrc: undefined },
        ])
        const currentRecordingId = ref<number | null>(null)

        const { audioStore: store, handlePlay } = useRecordingPlayback({
            recordings,
            currentRecordingId,
            fallbackCover: () => '/fallback.jpg',
        })

        handlePlay()
        expect(store.currentTrack).toBeNull()
        expect(store.replaceQueueAndPlay).not.toHaveBeenCalled()

        currentRecordingId.value = 1
        handlePlay()
        expect(store.currentTrack).toBeNull()
        expect(store.isPlaying).toBe(false)
        expect(store.replaceQueueAndPlay).not.toHaveBeenCalled()
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

        const { audioStore: store, handlePlay } = useRecordingPlayback({
            recordings,
            currentRecordingId,
            fallbackCover: () => '/fallback.jpg',
            trackExtra: () => ({ workId: 42 }),
        })

        handlePlay()

        expect(store.replaceQueueAndPlay).toHaveBeenCalledWith(
            [
                {
                    id: 1,
                    title: 'Rec 1',
                    artist: 'Artist 1',
                    cover: '/fallback.jpg',
                    src: '/audio/1.mp3',
                    mediaFileId: 2001,
                    workId: 42,
                },
            ],
            0,
        )
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

        const { audioStore: store, onRecordingDoubleClick } = useRecordingPlayback({
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
        expect(store.replaceQueueAndPlay).toHaveBeenCalledWith(
            [
                {
                    id: 1,
                    title: 'Rec 1',
                    artist: 'Artist 1',
                    cover: '/fallback.jpg',
                    src: '/audio/1.mp3',
                    mediaFileId: 2001,
                },
                {
                    id: 2,
                    title: 'Rec 2',
                    artist: 'Artist 2',
                    cover: '/cover/2.jpg',
                    src: '/audio/2.mp3',
                    mediaFileId: 2002,
                },
            ],
            1,
        )
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

        const {
            audioStore: store,
            hasPlayableRecording,
            isCurrentRecordingPlaying,
            handlePlay,
        } = useRecordingPlayback({
            recordings,
            currentRecordingId,
            fallbackCover: () => '/fallback.jpg',
        })

        expect(hasPlayableRecording.value).toBe(true)
        expect(isCurrentRecordingPlaying.value).toBe(false)

        handlePlay()
        expect(store.replaceQueueAndPlay).toHaveBeenCalledWith(
            [
                {
                    id: 1,
                    title: 'Rec 1',
                    artist: 'Artist 1',
                    cover: '/fallback.jpg',
                    src: '/audio/1.mp3',
                    mediaFileId: 2001,
                },
            ],
            0,
        )
        expect(isCurrentRecordingPlaying.value).toBe(false)

        currentRecordingId.value = 2
        expect(isCurrentRecordingPlaying.value).toBe(false)
    })
})
