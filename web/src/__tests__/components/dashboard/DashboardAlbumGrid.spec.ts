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
            albumController: {
                listAlbums: vi.fn(),
                getAlbum: vi.fn(),
            },
        },
    }
})

import { api } from '@/ApiInstance'
import DashboardAlbumGrid from '@/components/dashboard/DashboardAlbumGrid.vue'
import { useAudioStore } from '@/stores/audio'
import { useUserStore } from '@/stores/user'

const listAlbumsMock = vi.mocked(api.albumController.listAlbums)
const getAlbumMock = vi.mocked(api.albumController.getAlbum)

const flushView = async () => {
    await Promise.resolve()
    await nextTick()
    await Promise.resolve()
    await nextTick()
}

const buildAlbumPage = () => ({
    rows: [
        {
            id: 101,
            title: 'Album A',
            kind: 'Album',
            comment: 'Test Album',
            recordings: [{ id: 301, label: 'Artist A' }],
            cover: { id: 401, url: '/api/media/401?_sig=a&_exp=9999999999' },
        },
    ],
    totalPageCount: 1,
    totalRowCount: 1,
})

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

describe('DashboardAlbumGrid', () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        listAlbumsMock.mockReset()
        getAlbumMock.mockReset()
        pushMock.mockReset()
        setPreferredAssetFormat('audio/opus')
    })

    it('shows centered empty state and navigates to settings when album list is empty', async () => {
        listAlbumsMock.mockResolvedValueOnce({
            rows: [],
            totalPageCount: 0,
            totalRowCount: 0,
        })

        const wrapper = mount(DashboardAlbumGrid)
        await flushView()

        expect(wrapper.text()).toContain('唱片架空空如也')
        expect(wrapper.text()).toContain('前往设置')

        const settingsButton = wrapper
            .findAll('button')
            .find((button) => button.text().includes('前往设置'))
        expect(settingsButton).toBeTruthy()

        await settingsButton!.trigger('click')

        expect(pushMock).toHaveBeenCalledTimes(1)
        expect(pushMock).toHaveBeenCalledWith({ name: 'settings' })
    })

    it('shows centered empty state when album request fails', async () => {
        listAlbumsMock.mockRejectedValueOnce(new Error('request failed'))

        const wrapper = mount(DashboardAlbumGrid)
        await flushView()

        expect(wrapper.text()).toContain('唱片架空空如也')
        expect(wrapper.text()).toContain('数据加载失败，请检查配置后重试。')
    })

    it('renders album cards and hides empty note when data exists', async () => {
        listAlbumsMock.mockResolvedValueOnce(buildAlbumPage())

        const wrapper = mount(DashboardAlbumGrid)
        await flushView()

        expect(wrapper.text()).toContain('Album A')
        expect(wrapper.text()).toContain('Artist A')
        expect(wrapper.text()).not.toContain('唱片架空空如也')
        expect(listAlbumsMock).toHaveBeenCalledWith({ pageIndex: 0, pageSize: 10 })
    })

    it('prefers default playable recording when loading album playback details', async () => {
        listAlbumsMock.mockResolvedValueOnce(buildAlbumPage())
        getAlbumMock.mockResolvedValueOnce({
            id: 101,
            title: 'Album A',
            kind: 'Album',
            comment: 'Album Comment',
            recordings: [
                {
                    id: 501,
                    kind: 'Demo',
                    title: 'Unplayable Default',
                    comment: '',
                    durationMs: 180000,
                    defaultInWork: true,
                    assets: [],
                    artists: [{ id: 801, displayName: 'Artist A', alias: [], comment: '' }],
                    cover: undefined,
                },
                {
                    id: 502,
                    kind: 'Studio',
                    title: 'Playable Default',
                    comment: '',
                    durationMs: 200000,
                    defaultInWork: true,
                    assets: [
                        {
                            id: 901,
                            comment: 'Audio 602',
                            mediaFile: {
                                id: 602,
                                sha256: 'hash-602',
                                mimeType: 'audio/mpeg',
                                size: 123,
                                objectKey: 'track-602.mp3',
                                url: '/api/media/602?_sig=b&_exp=9999999999',
                            },
                        },
                    ],
                    artists: [{ id: 802, displayName: 'Artist B', alias: [], comment: '' }],
                    cover: {
                        id: 702,
                        sha256: 'cover-702',
                        objectKey: 'cover-702.jpg',
                        mimeType: 'image/jpeg',
                        size: 456,
                        url: '/api/media/702?_sig=c&_exp=9999999999',
                    },
                },
                {
                    id: 503,
                    kind: 'Live',
                    title: 'Playable Fallback',
                    comment: '',
                    durationMs: 210000,
                    defaultInWork: false,
                    assets: [
                        {
                            id: 902,
                            comment: 'Audio 603',
                            mediaFile: {
                                id: 603,
                                sha256: 'hash-603',
                                mimeType: 'audio/mpeg',
                                size: 124,
                                objectKey: 'track-603.mp3',
                                url: '/api/media/603?_sig=d&_exp=9999999999',
                            },
                        },
                    ],
                    artists: [{ id: 803, displayName: 'Artist C', alias: [], comment: '' }],
                    cover: undefined,
                },
            ],
        })

        const wrapper = mount(DashboardAlbumGrid)
        await flushView()

        const playButtons = wrapper.findAll('button')
        const albumPlayButton = playButtons.find((button) => button.attributes('type') === 'button')
        expect(albumPlayButton).toBeTruthy()

        await albumPlayButton!.trigger('click')
        await flushView()

        const audioStore = useAudioStore()
        expect(audioStore.currentTrack).toEqual(
            expect.objectContaining({
                id: 502,
                title: 'Playable Default',
                artist: 'Artist B',
                cover: '/api/media/702?_sig=c&_exp=9999999999',
                src: '/api/media/602?_sig=b&_exp=9999999999',
                mediaFileId: 602,
            }),
        )
    })

    it('prefers the user selected asset format when album playback has multiple audio assets', async () => {
        setPreferredAssetFormat('audio/flac')
        listAlbumsMock.mockResolvedValueOnce(buildAlbumPage())
        getAlbumMock.mockResolvedValueOnce({
            id: 101,
            title: 'Album A',
            kind: 'Album',
            comment: 'Album Comment',
            recordings: [
                {
                    id: 502,
                    kind: 'Studio',
                    title: 'Playable Default',
                    comment: '',
                    durationMs: 200000,
                    defaultInWork: true,
                    assets: [
                        {
                            id: 901,
                            comment: 'Audio mp3',
                            mediaFile: {
                                id: 602,
                                sha256: 'hash-602',
                                mimeType: 'audio/mpeg',
                                size: 123,
                                objectKey: 'track-602.mp3',
                                url: '/api/media/602?_sig=b&_exp=9999999999',
                            },
                        },
                        {
                            id: 902,
                            comment: 'Audio flac',
                            mediaFile: {
                                id: 603,
                                sha256: 'hash-603',
                                mimeType: 'audio/flac',
                                size: 456,
                                objectKey: 'track-603.flac',
                                url: '/api/media/603?_sig=c&_exp=9999999999',
                            },
                        },
                    ],
                    artists: [{ id: 802, displayName: 'Artist B', alias: [], comment: '' }],
                    cover: {
                        id: 702,
                        sha256: 'cover-702',
                        objectKey: 'cover-702.jpg',
                        mimeType: 'image/jpeg',
                        size: 456,
                        url: '/api/media/702?_sig=d&_exp=9999999999',
                    },
                },
            ],
        })

        const wrapper = mount(DashboardAlbumGrid)
        await flushView()

        const playButtons = wrapper.findAll('button')
        const albumPlayButton = playButtons.find((button) => button.attributes('type') === 'button')
        expect(albumPlayButton).toBeTruthy()

        await albumPlayButton!.trigger('click')
        await flushView()

        const audioStore = useAudioStore()
        expect(audioStore.currentTrack).toEqual(
            expect.objectContaining({
                id: 502,
                src: '/api/media/603?_sig=c&_exp=9999999999',
                mediaFileId: 603,
            }),
        )
    })
})
