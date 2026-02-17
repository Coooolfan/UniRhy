import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { nextTick } from 'vue'

vi.mock('vue-router', () => ({
    useRoute: () => ({
        params: { id: '9' },
    }),
}))

vi.mock('@/ApiInstance', async (importOriginal) => {
    const actual = await importOriginal<typeof import('@/ApiInstance')>()
    return {
        ...actual,
        api: {
            workController: {
                getWorkById: vi.fn(),
                updateWork: vi.fn(),
            },
            recordingController: {
                updateRecording: vi.fn(),
            },
        },
    }
})

import { api } from '@/ApiInstance'
import WorkDetailView from '@/views/WorkDetailView.vue'

const getWorkByIdMock = vi.mocked(api.workController.getWorkById)
const updateRecordingMock = vi.mocked(api.recordingController.updateRecording)

const flushView = async () => {
    await Promise.resolve()
    await nextTick()
    await Promise.resolve()
    await nextTick()
}

const buildWorkResponse = () => ({
    id: 9,
    title: 'Work One',
    recordings: [
        {
            id: 21,
            kind: 'Live',
            label: 'Label X',
            title: 'Version A',
            comment: 'Comment A',
            defaultInWork: true,
            assets: [],
            artists: [{ id: 1, name: 'Artist A', comment: '' }],
            cover: undefined,
        },
        {
            id: 22,
            kind: 'Studio',
            label: 'Label Y',
            title: 'Version B',
            comment: 'Comment B',
            defaultInWork: false,
            assets: [],
            artists: [{ id: 2, name: 'Artist B', comment: '' }],
            cover: undefined,
        },
    ],
})

describe('WorkDetailView', () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        getWorkByIdMock.mockReset()
        updateRecordingMock.mockReset()
    })

    it('disables hero play button when no playable recordings exist', async () => {
        getWorkByIdMock.mockResolvedValueOnce(buildWorkResponse())

        const wrapper = mount(WorkDetailView, {
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

    it('submits defaultInWork=true and keeps a single default recording in list', async () => {
        getWorkByIdMock.mockResolvedValueOnce(buildWorkResponse())
        updateRecordingMock.mockResolvedValueOnce()

        const wrapper = mount(WorkDetailView, {
            global: {
                stubs: {
                    teleport: true,
                    transition: false,
                    DashboardTopBar: true,
                },
            },
        })

        await flushView()

        const editButtons = wrapper.findAll('button[title="关于录音"]')
        expect(editButtons.length).toBeGreaterThan(1)
        const secondEditButton = editButtons[1]
        if (!secondEditButton) {
            throw new Error('Missing second edit button in test setup')
        }
        await secondEditButton.trigger('click')
        await nextTick()

        const defaultCheckbox = wrapper.find('input[type="checkbox"]')
        expect(defaultCheckbox.exists()).toBe(true)
        await defaultCheckbox.setValue(true)

        const submitButton = wrapper
            .findAll('button')
            .find((button) => button.text().includes('保存更改'))
        expect(submitButton).toBeTruthy()
        await submitButton!.trigger('click')

        await flushView()

        expect(updateRecordingMock).toHaveBeenCalledTimes(1)
        expect(updateRecordingMock).toHaveBeenCalledWith({
            id: 22,
            body: {
                title: 'Version B',
                label: 'Label Y',
                comment: 'Comment B',
                kind: 'Studio',
                defaultInWork: true,
            },
        })

        const defaultBadges = wrapper.findAll('span').filter((span) => span.text() === 'Default')
        expect(defaultBadges).toHaveLength(1)
    })
})
