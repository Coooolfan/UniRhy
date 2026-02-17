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
                mergeRecording: vi.fn(),
            },
        },
    }
})

import { api } from '@/ApiInstance'
import WorkDetailView from '@/views/WorkDetailView.vue'

const getWorkByIdMock = vi.mocked(api.workController.getWorkById)
const updateRecordingMock = vi.mocked(api.recordingController.updateRecording)
const mergeRecordingMock = vi.mocked(api.recordingController.mergeRecording)

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
        mergeRecordingMock.mockReset()
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

        const defaultToggleLabel = wrapper
            .findAll('label')
            .find((label) => label.text().includes('默认版本'))
        expect(defaultToggleLabel).toBeTruthy()
        const defaultCheckbox = defaultToggleLabel!.find('input[type="checkbox"]')
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

    it('shows merge button only when at least two recordings are selected and modal target options are selected items only', async () => {
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

        expect(wrapper.find('[data-testid="open-merge-recording-button"]').exists()).toBe(false)

        const selectionCheckboxes = wrapper.findAll('[data-testid="recording-select-checkbox"]')
        expect(selectionCheckboxes).toHaveLength(2)

        const firstSelectionCheckbox = selectionCheckboxes[0]
        const secondSelectionCheckbox = selectionCheckboxes[1]
        if (!firstSelectionCheckbox || !secondSelectionCheckbox) {
            throw new Error('Missing selection checkboxes in test setup')
        }

        await firstSelectionCheckbox.setValue(true)
        await nextTick()
        expect(wrapper.find('[data-testid="open-merge-recording-button"]').exists()).toBe(false)

        await secondSelectionCheckbox.setValue(true)
        await nextTick()

        const openMergeButton = wrapper.find('[data-testid="open-merge-recording-button"]')
        expect(openMergeButton.exists()).toBe(true)
        await openMergeButton.trigger('click')
        await nextTick()

        const targetRadios = wrapper.findAll('[data-testid="recording-merge-target-radio"]')
        expect(targetRadios).toHaveLength(2)
    })

    it('submits merge request with selected target and refreshes work detail on success', async () => {
        getWorkByIdMock.mockResolvedValue(buildWorkResponse())
        mergeRecordingMock.mockResolvedValueOnce()

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

        const selectionCheckboxes = wrapper.findAll('[data-testid="recording-select-checkbox"]')
        const firstSelectionCheckbox = selectionCheckboxes[0]
        const secondSelectionCheckbox = selectionCheckboxes[1]
        if (!firstSelectionCheckbox || !secondSelectionCheckbox) {
            throw new Error('Missing selection checkboxes in test setup')
        }

        await firstSelectionCheckbox.setValue(true)
        await secondSelectionCheckbox.setValue(true)
        await nextTick()

        await wrapper.find('[data-testid="open-merge-recording-button"]').trigger('click')
        await nextTick()

        const targetRadios = wrapper.findAll('[data-testid="recording-merge-target-radio"]')
        const secondTargetRadio = targetRadios[1]
        if (!secondTargetRadio) {
            throw new Error('Missing second target radio in test setup')
        }
        await secondTargetRadio.setValue(true)

        await wrapper.find('[data-testid="submit-recording-merge-button"]').trigger('click')
        await flushView()

        expect(mergeRecordingMock).toHaveBeenCalledTimes(1)
        expect(mergeRecordingMock).toHaveBeenCalledWith({
            body: {
                targetId: 22,
                needMergeIds: [21],
            },
        })
        expect(getWorkByIdMock).toHaveBeenCalledTimes(2)
        expect(wrapper.find('[data-testid="recording-merge-modal"]').exists()).toBe(false)
    })

    it('shows merge error and keeps selected recordings when merge fails', async () => {
        getWorkByIdMock.mockResolvedValue(buildWorkResponse())
        mergeRecordingMock.mockRejectedValueOnce({ message: '合并录音失败（测试）' })

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

        const selectionCheckboxes = wrapper.findAll('[data-testid="recording-select-checkbox"]')
        const firstSelectionCheckbox = selectionCheckboxes[0]
        const secondSelectionCheckbox = selectionCheckboxes[1]
        if (!firstSelectionCheckbox || !secondSelectionCheckbox) {
            throw new Error('Missing selection checkboxes in test setup')
        }

        await firstSelectionCheckbox.setValue(true)
        await secondSelectionCheckbox.setValue(true)
        await nextTick()

        await wrapper.find('[data-testid="open-merge-recording-button"]').trigger('click')
        await nextTick()

        await wrapper.find('[data-testid="submit-recording-merge-button"]').trigger('click')
        await flushView()

        expect(mergeRecordingMock).toHaveBeenCalledTimes(1)
        expect(getWorkByIdMock).toHaveBeenCalledTimes(1)
        expect(wrapper.text()).toContain('合并录音失败（测试）')
        expect(wrapper.findAll('[data-testid="recording-select-checkbox"]:checked')).toHaveLength(2)
    })
})
