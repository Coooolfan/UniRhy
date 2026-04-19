import { beforeEach, describe, expect, it, vi } from 'vitest'
import { reactive, ref } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { useRecordingPlayback, type PlayableRecording } from '@/composables/useRecordingPlayback'
import { resetRecordingPlaybackResolverCaches } from '@/services/recordingPlaybackResolver'
import { useUserStore } from '@/stores/user'

type TestRecording = PlayableRecording
type TestTrack = {
    id: number
    title: string
    artist: string
    cover: string
    src: string
    mediaFileId: number
    workId?: number
}

const audioStore = reactive({
    currentTrack: null as TestTrack | null,
    isPlaying: false,
    replaceQueueAndPlay: vi.fn((tracks: TestTrack[], currentIndex: number) => {
        const targetTrack = tracks[currentIndex]
        if (!targetTrack) {
            return Promise.resolve()
        }

        audioStore.currentTrack = {
            id: targetTrack.id,
            title: targetTrack.title,
            artist: targetTrack.artist,
            cover: targetTrack.cover,
            src: targetTrack.src,
            mediaFileId: targetTrack.mediaFileId,
            ...(targetTrack.workId === undefined ? {} : { workId: targetTrack.workId }),
        }
        return Promise.resolve()
    }),
})

vi.mock('@/stores/audio', () => ({
    useAudioStore: () => audioStore,
}))

const buildAudioAssets = (id: number, mimeType = 'audio/mpeg', url = `/audio/${id}.mp3`) => [
    {
        mediaFile: {
            id: id + 2_000,
            mimeType,
            objectKey: `track-${id}.${mimeType.split('/')[1] ?? 'bin'}`,
            url,
        },
    },
]

describe('useRecordingPlayback', () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        resetRecordingPlaybackResolverCaches()
        audioStore.currentTrack = null
        audioStore.isPlaying = false
        audioStore.replaceQueueAndPlay.mockReset()
        audioStore.replaceQueueAndPlay.mockImplementation((tracks: TestTrack[], currentIndex) => {
            const targetTrack = tracks[currentIndex]
            if (!targetTrack) {
                return Promise.resolve()
            }

            audioStore.currentTrack = {
                id: targetTrack.id,
                title: targetTrack.title,
                artist: targetTrack.artist,
                cover: targetTrack.cover,
                src: targetTrack.src,
                mediaFileId: targetTrack.mediaFileId,
                ...(targetTrack.workId === undefined ? {} : { workId: targetTrack.workId }),
            }
            return Promise.resolve()
        })

        const userStore = useUserStore()
        userStore.user = {
            id: 1,
            name: 'Tester',
            email: 'tester@example.com',
            admin: false,
            preferences: {
                preferredAssetFormat: 'audio/opus',
            },
        }
    })

    it('does not play when there is no target or no audio asset', async () => {
        const recordings = ref<TestRecording[]>([
            { id: 1, title: 'A', artist: 'B', cover: '', assets: [] },
        ])
        const currentRecordingId = ref<number | null>(null)

        const { audioStore: store, handlePlay } = useRecordingPlayback({
            recordings,
            currentRecordingId,
            fallbackCover: () => '/fallback.jpg',
        })

        await handlePlay()
        expect(store.currentTrack).toBeNull()
        expect(store.replaceQueueAndPlay).not.toHaveBeenCalled()

        currentRecordingId.value = 1
        await handlePlay()
        expect(store.currentTrack).toBeNull()
        expect(store.isPlaying).toBe(false)
        expect(store.replaceQueueAndPlay).not.toHaveBeenCalled()
    })

    it('queues target recording with fallback cover and trackExtra', async () => {
        const recordings = ref<TestRecording[]>([
            {
                id: 1,
                title: 'Rec 1',
                artist: 'Artist 1',
                cover: '',
                assets: buildAudioAssets(1),
            },
        ])
        const currentRecordingId = ref<number | null>(1)

        const { audioStore: store, handlePlay } = useRecordingPlayback({
            recordings,
            currentRecordingId,
            fallbackCover: () => '/fallback.jpg',
            trackExtra: () => ({ workId: 42 }),
        })

        await handlePlay()

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

    it('double click updates current selection and queues the chosen recording', async () => {
        const recordings = ref<TestRecording[]>([
            {
                id: 1,
                title: 'Rec 1',
                artist: 'Artist 1',
                cover: '',
                assets: buildAudioAssets(1),
            },
            {
                id: 2,
                title: 'Rec 2',
                artist: 'Artist 2',
                cover: '/cover/2.jpg',
                assets: buildAudioAssets(2, 'audio/mpeg', '/audio/2.mp3'),
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
        await vi.waitFor(() => {
            expect(store.replaceQueueAndPlay).toHaveBeenCalled()
        })

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

    it('computes playable and current-playing states from raw recordings', async () => {
        const recordings = ref<TestRecording[]>([
            {
                id: 1,
                title: 'Rec 1',
                artist: 'Artist 1',
                cover: '',
                assets: buildAudioAssets(1),
            },
            { id: 2, title: 'Rec 2', artist: 'Artist 2', cover: '', assets: [] },
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

        await handlePlay()
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

        audioStore.isPlaying = true
        expect(isCurrentRecordingPlaying.value).toBe(true)

        currentRecordingId.value = 2
        expect(isCurrentRecordingPlaying.value).toBe(false)
    })

    it('falls back from an unplayable default selection to the first playable recording', async () => {
        const recordings = ref<TestRecording[]>([
            {
                id: 1,
                title: 'Default Recording',
                artist: 'Artist 1',
                cover: '',
                defaultInWork: true,
                assets: [],
            },
            {
                id: 2,
                title: 'Playable Recording',
                artist: 'Artist 2',
                cover: '',
                assets: buildAudioAssets(2),
            },
        ])
        const currentRecordingId = ref<number | null>(1)

        const { audioStore: store, handlePlay } = useRecordingPlayback({
            recordings,
            currentRecordingId,
            fallbackCover: () => '/fallback.jpg',
            initialStrategy: 'default-first',
        })

        await handlePlay()

        expect(store.replaceQueueAndPlay).toHaveBeenCalledWith(
            [
                {
                    id: 2,
                    title: 'Playable Recording',
                    artist: 'Artist 2',
                    cover: '/fallback.jpg',
                    src: '/audio/2.mp3',
                    mediaFileId: 2002,
                },
            ],
            0,
        )
        expect(currentRecordingId.value).toBe(2)
    })

    it('loads the saved asset format before choosing the playback target and queue', async () => {
        const recordings = ref<TestRecording[]>([
            {
                id: 1,
                title: 'Opus Recording',
                artist: 'Artist 1',
                cover: '',
                assets: buildAudioAssets(1, 'audio/opus', '/audio/1.opus'),
            },
            {
                id: 2,
                title: 'Flac Recording',
                artist: 'Artist 2',
                cover: '',
                assets: buildAudioAssets(2, 'audio/flac', '/audio/2.flac'),
            },
        ])
        const currentRecordingId = ref<number | null>(1)
        const userStore = useUserStore()
        userStore.user = null
        vi.spyOn(userStore, 'getPreferredAssetFormat').mockResolvedValue('audio/flac')

        const { audioStore: store, handlePlay } = useRecordingPlayback({
            recordings,
            currentRecordingId,
            fallbackCover: () => '/fallback.jpg',
        })

        await handlePlay()

        expect(store.replaceQueueAndPlay).toHaveBeenCalledWith(
            [
                {
                    id: 1,
                    title: 'Opus Recording',
                    artist: 'Artist 1',
                    cover: '/fallback.jpg',
                    src: '/audio/1.opus',
                    mediaFileId: 2001,
                },
                {
                    id: 2,
                    title: 'Flac Recording',
                    artist: 'Artist 2',
                    cover: '/fallback.jpg',
                    src: '/audio/2.flac',
                    mediaFileId: 2002,
                },
            ],
            1,
        )
        expect(currentRecordingId.value).toBe(2)
    })

    it('uses the latest preferred asset format for the next playback without hot-switching current track', async () => {
        const recordings = ref<TestRecording[]>([
            {
                id: 1,
                title: 'Rec 1',
                artist: 'Artist 1',
                cover: '',
                assets: [
                    ...buildAudioAssets(1, 'audio/mpeg', '/audio/1.mp3'),
                    ...buildAudioAssets(10, 'audio/flac', '/audio/1.flac'),
                ],
            },
        ])
        const currentRecordingId = ref<number | null>(1)
        const userStore = useUserStore()

        const { audioStore: store, handlePlay } = useRecordingPlayback({
            recordings,
            currentRecordingId,
            fallbackCover: () => '/fallback.jpg',
        })

        userStore.user = {
            ...userStore.user!,
            preferences: {
                preferredAssetFormat: 'audio/flac',
            },
        }

        await handlePlay()
        expect(store.currentTrack?.mediaFileId).toBe(2010)
        expect(store.currentTrack?.src).toBe('/audio/1.flac')

        const currentUser = userStore.user
        if (!currentUser) {
            throw new Error('Missing user in test setup')
        }
        userStore.user = {
            ...currentUser,
            preferences: {
                preferredAssetFormat: 'audio/mpeg',
            },
        }

        expect(store.currentTrack?.mediaFileId).toBe(2010)

        await handlePlay()
        expect(store.currentTrack?.mediaFileId).toBe(2001)
        expect(store.currentTrack?.src).toBe('/audio/1.mp3')
    })
})
