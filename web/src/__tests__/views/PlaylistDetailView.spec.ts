import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { nextTick } from 'vue'

vi.mock('vue-router', () => ({
    useRoute: () => ({
        params: { id: '5' },
    }),
    useRouter: () => ({
        push: vi.fn(),
    }),
}))

vi.mock('@/ApiInstance', async (importOriginal) => {
    const actual = await importOriginal<typeof import('@/ApiInstance')>()
    return {
        ...actual,
        api: {
            playlistController: {
                getPlaylist: vi.fn(),
                updatePlaylist: vi.fn(),
                deletePlaylist: vi.fn(),
                removeRecordingFromPlaylist: vi.fn(),
                addRecordingToPlaylist: vi.fn(),
                reorderPlaylistRecordings: vi.fn(),
            },
        },
    }
})

import { api } from '@/ApiInstance'
import PlaylistDetailView from '@/views/PlaylistDetailView.vue'
import { useUserStore } from '@/stores/user'

const getPlaylistMock = vi.mocked(api.playlistController.getPlaylist)
const reorderPlaylistRecordingsMock = vi.mocked(api.playlistController.reorderPlaylistRecordings)

const flushView = async () => {
    await Promise.resolve()
    await nextTick()
    await Promise.resolve()
    await nextTick()
}

const createDragTransfer = () => ({
    effectAllowed: '',
    dropEffect: '',
    setData: vi.fn(),
})

const buildPlaylistResponse = () => ({
    id: 5,
    name: 'Playlist One',
    comment: 'Playlist Comment',
    recordings: [
        {
            id: 31,
            kind: 'Studio',
            label: 'Label A',
            title: 'Track One',
            comment: 'Track One Comment',
            durationMs: 210000,
            defaultInWork: false,
            lyrics: '',
            embedding: undefined,
            assets: [],
            artists: [{ id: 1, displayName: 'Artist A', alias: [], comment: '' }],
            cover: undefined,
        },
        {
            id: 32,
            kind: 'Live',
            label: 'Label B',
            title: 'Track Two',
            comment: 'Track Two Comment',
            durationMs: 180000,
            defaultInWork: false,
            lyrics: '',
            embedding: undefined,
            assets: [],
            artists: [{ id: 2, displayName: 'Artist B', alias: [], comment: '' }],
            cover: undefined,
        },
    ],
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

describe('PlaylistDetailView', () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        getPlaylistMock.mockReset()
        reorderPlaylistRecordingsMock.mockReset()
        setPreferredAssetFormat('audio/opus')
    })

    it('reorders recordings via reorderPlaylistRecordings API', async () => {
        getPlaylistMock.mockResolvedValueOnce(buildPlaylistResponse())
        reorderPlaylistRecordingsMock.mockResolvedValue(undefined)

        const wrapper = mount(PlaylistDetailView, {
            global: {
                stubs: {
                    teleport: true,
                    transition: false,
                    DashboardTopBar: true,
                },
            },
        })

        await flushView()

        const rows = wrapper.findAll('[data-testid="media-list-row"]')
        const firstRow = rows[0]
        const dragHandles = wrapper.findAll('[data-testid="media-list-drag-handle"]')
        const secondHandle = dragHandles[1]
        if (!firstRow || !secondHandle) {
            throw new Error('Missing media list rows in test setup')
        }

        const dataTransfer = createDragTransfer()
        await secondHandle.trigger('dragstart', { dataTransfer })
        await firstRow.trigger('dragover', { clientY: 0, dataTransfer })
        await firstRow.trigger('drop', { clientY: 0, dataTransfer })
        await flushView()

        const itemIds = wrapper
            .findAll('[data-testid="media-list-row"]')
            .map((row) => Number(row.attributes('data-item-id')))
        expect(itemIds).toEqual([32, 31])

        expect(reorderPlaylistRecordingsMock).toHaveBeenCalledTimes(1)
        expect(reorderPlaylistRecordingsMock).toHaveBeenCalledWith({
            id: 5,
            body: { recordingIds: [32, 31] },
        })
    })
})
