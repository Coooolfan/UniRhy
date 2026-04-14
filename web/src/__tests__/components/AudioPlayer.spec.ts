import { beforeEach, describe, expect, it, vi } from 'vitest'
import { reactive } from 'vue'
import { mount } from '@vue/test-utils'

const pushMock = vi.fn()

const audioStore = reactive({
    currentTrack: {
        id: 1,
        title: 'Track 1',
        artist: 'Artist 1',
        cover: '/cover/1.jpg',
        src: '/audio/1.mp3',
        mediaFileId: 2_001,
    },
    isPlaying: true,
    isPlayerHidden: false,
    currentTime: 12,
    duration: 120,
    volume: 1,
    isLoading: false,
    syncState: 'reconnecting',
    syncStatusText: '同步重连中',
    canSendRealtimeControl: false,
    canNavigateQueue: false,
    playbackStrategy: 'SEQUENTIAL',
    stopStrategy: 'LIST',
    currentQueueEntry: {
        entryId: 101,
    },
    queueEntries: [
        {
            entryId: 101,
            recordingId: 1,
            mediaFileId: 2_001,
            title: 'Track 1',
            artist: 'Artist 1',
            cover: '/cover/1.jpg',
            durationMs: 120_000,
        },
    ],
    pause: vi.fn(),
    resume: vi.fn(),
    playNext: vi.fn(),
    playPrevious: vi.fn(),
    updateQueueStrategies: vi.fn(),
    playQueueEntry: vi.fn(),
    clearQueue: vi.fn(),
    reorderQueue: vi.fn(),
    removeQueueEntry: vi.fn(),
    stop: vi.fn(),
    seek: vi.fn(),
    setVolume: vi.fn(),
    hidePlayer: vi.fn(),
    showPlayer: vi.fn(),
})

vi.mock('@/stores/audio', () => ({
    useAudioStore: () => audioStore,
}))

vi.mock('vue-router', () => ({
    useRouter: () => ({
        push: pushMock,
    }),
}))

import AudioPlayer from '@/components/AudioPlayer.vue'

describe('AudioPlayer', () => {
    beforeEach(() => {
        audioStore.currentTrack = {
            id: 1,
            title: 'Track 1',
            artist: 'Artist 1',
            cover: '/cover/1.jpg',
            src: '/audio/1.mp3',
            mediaFileId: 2_001,
        }
        audioStore.isPlaying = true
        audioStore.isPlayerHidden = false
        audioStore.currentTime = 12
        audioStore.duration = 120
        audioStore.volume = 1
        audioStore.isLoading = false
        audioStore.syncState = 'reconnecting'
        audioStore.syncStatusText = '同步重连中'
        audioStore.canSendRealtimeControl = false
        audioStore.canNavigateQueue = false
        audioStore.playbackStrategy = 'SEQUENTIAL'
        audioStore.stopStrategy = 'LIST'
        audioStore.currentQueueEntry = {
            entryId: 101,
        }
        audioStore.queueEntries = [
            {
                entryId: 101,
                recordingId: 1,
                mediaFileId: 2_001,
                title: 'Track 1',
                artist: 'Artist 1',
                cover: '/cover/1.jpg',
                durationMs: 120_000,
            },
        ]
        audioStore.pause.mockReset()
        audioStore.resume.mockReset()
        audioStore.playNext.mockReset()
        audioStore.playPrevious.mockReset()
        audioStore.updateQueueStrategies.mockReset()
        audioStore.playQueueEntry.mockReset()
        audioStore.clearQueue.mockReset()
        audioStore.reorderQueue.mockReset()
        audioStore.removeQueueEntry.mockReset()
        audioStore.stop.mockReset()
        audioStore.seek.mockReset()
        audioStore.setVolume.mockReset()
        audioStore.hidePlayer.mockReset()
        audioStore.showPlayer.mockReset()
        pushMock.mockReset()
    })

    it('renders sync status and disables pause/seek while realtime control is unavailable', () => {
        const wrapper = mount(AudioPlayer)

        expect(wrapper.get('[data-test="sync-status"]').text()).toBe('同步重连中')
        expect(wrapper.get('[data-test="transport-button"]').attributes('disabled')).toBeDefined()
        expect(wrapper.get('[data-test="seek-input"]').attributes('disabled')).toBeDefined()
    })

    it('keeps the play button enabled when playback is paused', async () => {
        audioStore.isPlaying = false
        audioStore.syncState = 'audio_locked'
        audioStore.syncStatusText = '等待启用音频'

        const wrapper = mount(AudioPlayer)

        expect(wrapper.get('[data-test="transport-button"]').attributes('disabled')).toBeUndefined()
        await wrapper.get('[data-test="transport-button"]').trigger('click')

        expect(audioStore.resume).toHaveBeenCalledTimes(1)
    })

    it('opens the playback sync debug page when the sync badge is clicked', async () => {
        const wrapper = mount(AudioPlayer)
        expect(wrapper.get('[data-test="sync-status"]').classes()).toContain('cursor-pointer')

        await wrapper.get('[data-test="sync-status"]').trigger('click')

        expect(pushMock).toHaveBeenCalledWith({ name: 'playback-sync-debug' })
    })

    it('removes border and background when sync is ready', () => {
        audioStore.syncState = 'ready'
        audioStore.syncStatusText = '同步已就绪'

        const wrapper = mount(AudioPlayer)
        const syncStatus = wrapper.get('[data-test="sync-status"]')
        const classes = syncStatus.classes()

        expect(syncStatus.text()).toBe('同步已就绪')
        expect(classes).not.toContain('bg-[#EDF5EC]')
        expect(classes).not.toContain('border')
    })

    it('removes border and background while sync is calibrating', () => {
        audioStore.syncState = 'calibrating'
        audioStore.syncStatusText = '同步校时中'

        const wrapper = mount(AudioPlayer)
        const syncStatus = wrapper.get('[data-test="sync-status"]')
        const classes = syncStatus.classes()

        expect(syncStatus.text()).toBe('同步校时中')
        expect(classes).not.toContain('bg-[#F3EEE6]')
        expect(classes).not.toContain('border')
    })

    it('reacts to passive metadata hydration updates from the store', async () => {
        audioStore.currentTrack = {
            id: 7,
            title: 'Recording #7',
            artist: 'Unknown Artist',
            cover: '',
            src: '/api/media/2007',
            mediaFileId: 2_007,
        }

        const wrapper = mount(AudioPlayer)

        expect(wrapper.text()).toContain('Recording #7')
        expect(wrapper.text()).toContain('Unknown Artist')
        expect(wrapper.find('img[alt="Cover"]').exists()).toBe(false)

        audioStore.currentTrack = {
            ...audioStore.currentTrack,
            title: 'Hydrated Track 7',
            artist: 'Hydrated Artist 7',
            cover: '/api/media/30007',
        }
        await wrapper.vm.$nextTick()

        expect(wrapper.text()).toContain('Hydrated Track 7')
        expect(wrapper.text()).toContain('Hydrated Artist 7')
        expect(wrapper.get('img[alt="Cover"]').attributes('src')).toBe('/api/media/30007')
    })

    it('uses queue navigation controls when realtime control is available', async () => {
        audioStore.canSendRealtimeControl = true
        audioStore.canNavigateQueue = true
        audioStore.queueEntries = [
            {
                entryId: 101,
                recordingId: 1,
                mediaFileId: 2_001,
                title: 'Track 1',
                artist: 'Artist 1',
                cover: '/cover/1.jpg',
                durationMs: 120_000,
            },
            {
                entryId: 102,
                recordingId: 2,
                mediaFileId: 2_002,
                title: 'Track 2',
                artist: 'Artist 2',
                cover: '/cover/2.jpg',
                durationMs: 180_000,
            },
        ]

        const wrapper = mount(AudioPlayer)

        await wrapper.get('[data-test="previous-button"]').trigger('click')
        await wrapper.get('[data-test="next-button"]').trigger('click')
        await wrapper.get('[data-test="queue-toggle-button"]').trigger('click')

        expect(audioStore.playPrevious).toHaveBeenCalledTimes(1)
        expect(audioStore.playNext).toHaveBeenCalledTimes(1)
        expect(wrapper.find('[data-test="queue-sidebar"]').exists()).toBe(true)
        expect(wrapper.text()).toContain('Current Queue')
        expect(wrapper.text()).toContain('2 tracks')
    })

    it('updates playback and stop strategies through the store', async () => {
        audioStore.canSendRealtimeControl = true
        audioStore.canNavigateQueue = true

        const wrapper = mount(AudioPlayer)

        await wrapper.get('[data-test="queue-toggle-button"]').trigger('click')
        await wrapper.get('[data-test="playback-strategy-select"]').setValue('SHUFFLE')
        await wrapper.get('[data-test="stop-strategy-select"]').setValue('TRACK')

        expect(audioStore.updateQueueStrategies).toHaveBeenNthCalledWith(1, 'SHUFFLE', 'LIST')
        expect(audioStore.updateQueueStrategies).toHaveBeenNthCalledWith(2, 'SEQUENTIAL', 'TRACK')
    })
})
