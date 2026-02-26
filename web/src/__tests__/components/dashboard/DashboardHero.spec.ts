import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { nextTick } from 'vue'

const pushMock = vi.fn()

vi.mock('vue-router', () => ({
    useRouter: () => ({
        push: pushMock,
    }),
}))

vi.mock('@/ApiInstance', async (importOriginal) => {
    const actual = await importOriginal<typeof import('@/ApiInstance')>()
    return {
        ...actual,
        api: {
            workController: {
                randomWork: vi.fn(),
            },
        },
    }
})

import { api } from '@/ApiInstance'
import DashboardHero from '@/components/dashboard/DashboardHero.vue'
import { useAudioStore } from '@/stores/audio'

const randomWorkMock = vi.mocked(api.workController.randomWork)

const flushView = async () => {
    await Promise.resolve()
    await nextTick()
    await Promise.resolve()
    await nextTick()
}

const buildPlayableWork = () => ({
    id: 11,
    title: 'Playable Work',
    recordings: [
        {
            id: 21,
            kind: 'Studio',
            label: 'Label A',
            title: 'Track A',
            comment: 'Track A Comment',
            durationMs: 180000,
            defaultInWork: true,
            assets: [
                {
                    id: 31,
                    comment: 'Audio file',
                    mediaFile: {
                        id: 41,
                        sha256: 'hash-audio',
                        mimeType: 'audio/mpeg',
                        size: 123,
                        objectKey: 'track-a.mp3',
                    },
                },
            ],
            artists: [{ id: 51, name: 'Artist A', comment: '' }],
            cover: {
                id: 61,
                sha256: 'hash-cover',
                objectKey: 'cover-a.jpg',
                mimeType: 'image/jpeg',
                size: 345,
            },
        },
    ],
})

const buildUnplayableWork = () => ({
    id: 12,
    title: 'Unplayable Work',
    recordings: [
        {
            id: 22,
            kind: 'Studio',
            label: 'Label B',
            title: 'Track B',
            comment: 'Track B Comment',
            durationMs: 210000,
            defaultInWork: true,
            assets: [
                {
                    id: 32,
                    comment: 'Image only',
                    mediaFile: {
                        id: 42,
                        sha256: 'hash-image',
                        mimeType: 'image/png',
                        size: 222,
                        objectKey: 'cover-b.png',
                    },
                },
            ],
            artists: [{ id: 52, name: 'Artist B', comment: '' }],
            cover: {
                id: 62,
                sha256: 'hash-cover-b',
                objectKey: 'cover-b.jpg',
                mimeType: 'image/jpeg',
                size: 456,
            },
        },
    ],
})

describe('DashboardHero', () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        randomWorkMock.mockReset()
        pushMock.mockReset()
    })

    it('shows playable action when daily pick has playable audio', async () => {
        randomWorkMock.mockResolvedValueOnce(buildPlayableWork())

        const wrapper = mount(DashboardHero)
        await flushView()

        expect(wrapper.text()).toContain('Playable Work')
        expect(wrapper.text()).not.toContain('旋律不可调')

        const playButton = wrapper
            .findAll('button')
            .find((button) => button.text().includes('立即播放'))
        expect(playButton).toBeTruthy()

        const audioStore = useAudioStore()
        await playButton!.trigger('click')

        expect(audioStore.currentTrack?.id).toBe(21)
        expect(audioStore.isPlaying).toBe(true)
        expect(pushMock).not.toHaveBeenCalled()
    })

    it('shows empty-state hint and navigates to settings when request fails', async () => {
        randomWorkMock.mockRejectedValueOnce(new Error('daily pick failed'))

        const wrapper = mount(DashboardHero)
        await flushView()

        expect(wrapper.text()).toContain('旋律不可调')
        expect(wrapper.text()).toContain('资料库中未能发现可用旋律')

        const settingsButton = wrapper
            .findAll('button')
            .find((button) => button.text().includes('前往设置'))
        expect(settingsButton).toBeTruthy()

        await settingsButton!.trigger('click')

        expect(pushMock).toHaveBeenCalledTimes(1)
        expect(pushMock).toHaveBeenCalledWith({ name: 'settings' })
    })

    it('shows empty-state hint when daily pick has no playable recording', async () => {
        randomWorkMock.mockResolvedValueOnce(buildUnplayableWork())

        const wrapper = mount(DashboardHero)
        await flushView()

        expect(wrapper.text()).toContain('旋律不可调')

        const settingsButton = wrapper
            .findAll('button')
            .find((button) => button.text().includes('前往设置'))
        expect(settingsButton).toBeTruthy()

        await settingsButton!.trigger('click')

        expect(pushMock).toHaveBeenCalledTimes(1)
        expect(pushMock).toHaveBeenCalledWith({ name: 'settings' })
    })
})
