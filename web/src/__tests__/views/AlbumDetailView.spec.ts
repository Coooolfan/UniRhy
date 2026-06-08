import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { nextTick } from 'vue'
import AppModalHost from '@/components/modals/AppModalHost.vue'

vi.mock('vue-router', () => ({
    useRoute: () => ({
        params: { id: '1' },
    }),
}))

vi.mock('@/ApiInstance', async (importOriginal) => {
    const actual = await importOriginal<typeof import('@/ApiInstance')>()
    return {
        ...actual,
        api: {
            albumController: {
                getAlbum: vi.fn(),
                reorderAlbumRecordings: vi.fn(),
            },
            recordingController: {
                updateRecording: vi.fn(),
            },
        },
    }
})

import { api } from '@/ApiInstance'
import AlbumDetailView from '@/views/AlbumDetailView.vue'
import { useUserStore } from '@/stores/user'

const getAlbumMock = vi.mocked(api.albumController.getAlbum)
const reorderAlbumRecordingsMock = vi.mocked(api.albumController.reorderAlbumRecordings)
const updateRecordingMock = vi.mocked(api.recordingController.updateRecording)

const flushView = async () => {
    await Promise.resolve()
    await nextTick()
    await Promise.resolve()
    await nextTick()
}

const mountWithModalHost = () =>
    mount(
        {
            components: {
                AlbumDetailView,
                AppModalHost,
            },
            template: `
                <div>
                    <AlbumDetailView />
                    <AppModalHost />
                </div>
            `,
        },
        {
            global: {
                stubs: {
                    teleport: true,
                    transition: false,
                    DashboardTopBar: true,
                },
            },
        },
    )

const createDragTransfer = () => ({
    effectAllowed: '',
    dropEffect: '',
    setData: vi.fn(),
})

const buildAlbumResponse = () => ({
    id: 1,
    title: 'Album One',
    releaseDate: '2024-01-01',
    comment: 'Album Comment',
    cover: undefined,
    recordings: [
        {
            id: 11,
            label: ['Label A'],
            title: 'Track One',
            comment: 'Track Comment',
            durationMs: 240000,
            defaultInWork: false,
            lyrics: '',
            assets: [],
            artists: [{ id: 101, displayName: 'Artist A', alias: [], comment: '' }],
            cover: undefined,
        },
        {
            id: 12,
            label: ['Label B'],
            title: 'Track Two',
            comment: 'Track Comment B',
            durationMs: 180000,
            defaultInWork: false,
            lyrics: '',
            assets: [],
            artists: [{ id: 102, displayName: 'Artist B', alias: [], comment: '' }],
            cover: undefined,
        },
    ],
})

const setUser = ({
    preferredAssetFormat = 'audio/opus',
    admin = false,
}: {
    preferredAssetFormat?: string
    admin?: boolean
} = {}) => {
    const userStore = useUserStore()
    userStore.user = {
        id: 1,
        name: 'Tester',
        email: 'tester@example.com',
        admin,
        preferences: {
            preferredAssetFormat,
        },
    }
}

describe('AlbumDetailView', () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        window.localStorage.clear()
        getAlbumMock.mockReset()
        reorderAlbumRecordingsMock.mockReset()
        updateRecordingMock.mockReset()
        setUser()
    })

    it('disables hero play button when no playable recordings exist', async () => {
        getAlbumMock.mockResolvedValueOnce(buildAlbumResponse())

        const wrapper = mountWithModalHost()

        await flushView()

        const playButton = wrapper
            .findAll('button')
            .find((button) => button.text().includes('立即播放'))
        expect(playButton).toBeTruthy()
        expect(playButton?.attributes('disabled')).toBeDefined()
    })

    it('shows recording duration in list', async () => {
        getAlbumMock.mockResolvedValueOnce(buildAlbumResponse())

        const wrapper = mountWithModalHost()

        await flushView()

        expect(wrapper.text()).toContain('4:00')
    })

    it('opens recording edit modal and submits recording update payload', async () => {
        setUser({ admin: true })
        getAlbumMock.mockResolvedValueOnce(buildAlbumResponse())
        updateRecordingMock.mockResolvedValueOnce()

        const wrapper = mountWithModalHost()

        await flushView()

        const editButton = wrapper.find('button[title="关于曲目"]')
        expect(editButton.exists()).toBe(true)
        await editButton.trigger('click')
        await nextTick()

        const titleInput = wrapper.find('input[placeholder="Track Title"]')
        expect(titleInput.exists()).toBe(true)
        await titleInput.setValue('Track One Updated')

        const submitButton = wrapper
            .findAll('button')
            .find((button) => button.text().includes('保存更改'))
        expect(submitButton).toBeTruthy()
        await submitButton!.trigger('click')

        await flushView()

        expect(updateRecordingMock).toHaveBeenCalledTimes(1)
        expect(updateRecordingMock).toHaveBeenCalledWith({
            id: 11,
            body: {
                title: 'Track One Updated',
                label: ['Label A'],
                comment: 'Track Comment',
            },
        })
    })

    it('reorders recordings via reorderAlbumRecordings API', async () => {
        setUser({ admin: true })
        getAlbumMock.mockResolvedValueOnce(buildAlbumResponse())
        reorderAlbumRecordingsMock.mockResolvedValue(undefined)

        const wrapper = mountWithModalHost()

        await flushView()

        const rows = wrapper.findAll('[data-testid="media-list-row"]')
        const firstRow = rows[0]
        const secondRow = rows[1]
        const dragHandles = wrapper.findAll('[data-testid="media-list-drag-handle"]')
        const secondHandle = dragHandles[1]
        if (!firstRow || !secondRow || !secondHandle) {
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
        expect(itemIds).toEqual([12, 11])

        expect(reorderAlbumRecordingsMock).toHaveBeenCalledTimes(1)
        expect(reorderAlbumRecordingsMock).toHaveBeenCalledWith({
            id: 1,
            body: { recordingIds: [12, 11] },
        })
    })

    it('hides album and recording management actions for ordinary users', async () => {
        getAlbumMock.mockResolvedValueOnce(buildAlbumResponse())

        const wrapper = mountWithModalHost()

        await flushView()

        expect(wrapper.find('button[title="编辑专辑"]').exists()).toBe(false)
        expect(wrapper.find('button[title="关于曲目"]').exists()).toBe(false)
        expect(
            wrapper
                .findAll('[data-testid="media-list-drag-handle"]')
                .some((handle) => handle.attributes('draggable') === 'true'),
        ).toBe(false)
    })
})
