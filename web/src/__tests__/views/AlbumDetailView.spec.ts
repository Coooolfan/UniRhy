import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { nextTick } from 'vue'

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
            },
            recordingController: {
                updateRecording: vi.fn(),
            },
        },
    }
})

import { api } from '@/ApiInstance'
import AlbumDetailView from '@/views/AlbumDetailView.vue'
import { buildRecordingOrderStorageKey } from '@/utils/recordingOrder'

const getAlbumMock = vi.mocked(api.albumController.getAlbum)
const updateRecordingMock = vi.mocked(api.recordingController.updateRecording)

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

const buildAlbumResponse = () => ({
    id: 1,
    title: 'Album One',
    kind: 'Album',
    releaseDate: '2024-01-01',
    comment: 'Album Comment',
    cover: undefined,
    recordings: [
        {
            id: 11,
            kind: 'Studio',
            label: 'Label A',
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
            kind: 'Live',
            label: 'Label B',
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

describe('AlbumDetailView', () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        window.localStorage.clear()
        getAlbumMock.mockReset()
        updateRecordingMock.mockReset()
    })

    it('disables hero play button when no playable recordings exist', async () => {
        getAlbumMock.mockResolvedValueOnce(buildAlbumResponse())

        const wrapper = mount(AlbumDetailView, {
            global: {
                stubs: {
                    teleport: true,
                    transition: false,
                    DashboardTopBar: true,
                },
            },
        })

        await flushView()

        const playButton = wrapper
            .findAll('button')
            .find((button) => button.text().includes('立即播放'))
        expect(playButton).toBeTruthy()
        expect(playButton?.attributes('disabled')).toBeDefined()
    })

    it('shows recording duration in list', async () => {
        getAlbumMock.mockResolvedValueOnce(buildAlbumResponse())

        const wrapper = mount(AlbumDetailView, {
            global: {
                stubs: {
                    teleport: true,
                    transition: false,
                    DashboardTopBar: true,
                },
            },
        })

        await flushView()

        expect(wrapper.text()).toContain('4:00')
    })

    it('opens recording edit modal and submits recording update payload', async () => {
        getAlbumMock.mockResolvedValueOnce(buildAlbumResponse())
        updateRecordingMock.mockResolvedValueOnce()

        const wrapper = mount(AlbumDetailView, {
            global: {
                stubs: {
                    teleport: true,
                    transition: false,
                    DashboardTopBar: true,
                },
            },
        })

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
                label: 'Label A',
                comment: 'Track Comment',
                kind: 'Studio',
            },
        })
    })

    it('reorders recordings and persists order locally', async () => {
        getAlbumMock.mockResolvedValueOnce(buildAlbumResponse())

        const wrapper = mount(AlbumDetailView, {
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
        const secondRow = rows[1]
        if (!firstRow || !secondRow) {
            throw new Error('Missing media list rows in test setup')
        }

        const dataTransfer = createDragTransfer()
        await secondRow.trigger('dragstart', { dataTransfer })
        await firstRow.trigger('dragover', { clientY: 0, dataTransfer })
        await firstRow.trigger('drop', { clientY: 0, dataTransfer })
        await nextTick()

        const itemIds = wrapper
            .findAll('[data-testid="media-list-row"]')
            .map((row) => Number(row.attributes('data-item-id')))
        expect(itemIds).toEqual([12, 11])
        expect(window.localStorage.getItem(buildRecordingOrderStorageKey('album', 1))).toBe(
            '[12,11]',
        )
    })
})
