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

const getAlbumMock = vi.mocked(api.albumController.getAlbum)
const updateRecordingMock = vi.mocked(api.recordingController.updateRecording)

const flushView = async () => {
    await Promise.resolve()
    await nextTick()
    await Promise.resolve()
    await nextTick()
}

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
            defaultInWork: false,
            assets: [],
            artists: [{ id: 101, name: 'Artist A', comment: '' }],
            cover: undefined,
        },
    ],
})

describe('AlbumDetailView', () => {
    beforeEach(() => {
        setActivePinia(createPinia())
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

        const editButton = wrapper.find('button[title="关于录音"]')
        expect(editButton.exists()).toBe(true)
        await editButton.trigger('click')
        await nextTick()

        const titleInput = wrapper.find('input[placeholder="Recording Title"]')
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
})
