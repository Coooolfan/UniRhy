import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, nextTick } from 'vue'

vi.mock('@/ApiInstance', async (importOriginal) => {
    const actual = await importOriginal<typeof import('@/ApiInstance')>()
    return {
        ...actual,
        api: {
            taskController: {
                listTaskLogs: vi.fn(),
                executeScanTask: vi.fn(),
                executeTranscodeTask: vi.fn(),
            },
            fileSystemStorageController: {
                list: vi.fn(),
            },
            ossStorageController: {
                list: vi.fn(),
            },
            systemConfigController: {
                get: vi.fn(),
            },
        },
    }
})

import { api } from '@/ApiInstance'
import TasksView from '@/views/TasksView.vue'

const listTaskLogsMock = vi.mocked(api.taskController.listTaskLogs)
const executeScanTaskMock = vi.mocked(api.taskController.executeScanTask)
const executeTranscodeTaskMock = vi.mocked(api.taskController.executeTranscodeTask)
const listFileSystemStorageMock = vi.mocked(api.fileSystemStorageController.list)
const listOssStorageMock = vi.mocked(api.ossStorageController.list)
const getSystemConfigMock = vi.mocked(api.systemConfigController.get)

const TaskSubmissionModalStub = defineComponent({
    name: 'TaskSubmissionModal',
    props: {
        open: {
            type: Boolean,
            default: false,
        },
    },
    emits: ['submit-metadata-parse', 'submit-transcode', 'close'],
    template:
        '<div v-if="open"><button data-test="submit-metadata-parse" @click="$emit(\'submit-metadata-parse\', { providerType: \'FILE_SYSTEM\', providerId: 1 })">submit-metadata-parse</button><button data-test="close-task-modal" @click="$emit(\'close\')">close</button></div>',
})

const flushView = async () => {
    await Promise.resolve()
    await nextTick()
    await Promise.resolve()
    await nextTick()
}

describe('TasksView', () => {
    beforeEach(() => {
        listTaskLogsMock.mockReset()
        executeScanTaskMock.mockReset()
        executeTranscodeTaskMock.mockReset()
        listFileSystemStorageMock.mockReset()
        listOssStorageMock.mockReset()
        getSystemConfigMock.mockReset()
        vi.useRealTimers()
    })

    it('renders aggregated task status counts from the backend stats endpoint', async () => {
        listTaskLogsMock.mockResolvedValueOnce([
            { taskType: 'METADATA_PARSE', status: 'PENDING', count: 2 },
            { taskType: 'METADATA_PARSE', status: 'RUNNING', count: 1 },
            { taskType: 'METADATA_PARSE', status: 'COMPLETED', count: 5 },
            { taskType: 'METADATA_PARSE', status: 'FAILED', count: 1 },
            { taskType: 'TRANSCODE', status: 'PENDING', count: 3 },
            { taskType: 'TRANSCODE', status: 'RUNNING', count: 0 },
            { taskType: 'TRANSCODE', status: 'COMPLETED', count: 7 },
            { taskType: 'TRANSCODE', status: 'FAILED', count: 0 },
        ])
        const wrapper = mount(TasksView, {
            global: {
                stubs: {
                    DashboardTopBar: true,
                    TaskSubmissionModal: TaskSubmissionModalStub,
                },
            },
        })

        await flushView()

        expect(listTaskLogsMock).toHaveBeenCalledTimes(1)
        expect(listFileSystemStorageMock).not.toHaveBeenCalled()
        expect(listOssStorageMock).not.toHaveBeenCalled()
        expect(getSystemConfigMock).not.toHaveBeenCalled()
        expect(wrapper.text()).toContain('6 个任务待处理或执行中')
        expect(wrapper.text()).toContain('元数据解析')
        expect(wrapper.text()).toContain('媒体转码')
        expect(wrapper.text()).toContain('状态概览')
        expect(wrapper.text()).toContain('任务类型分布')
        expect(wrapper.text()).toMatch(/其中\s*5\s*个排队，1\s*个正在执行。?/)
        expect(wrapper.text()).toMatch(/1\s*Failed\s*失败/)
    })

    it('shows submit feedback on the action button for two seconds after a successful submission', async () => {
        vi.useFakeTimers()
        listTaskLogsMock.mockResolvedValue([])
        executeScanTaskMock.mockResolvedValue(undefined)
        listFileSystemStorageMock.mockResolvedValue([])
        listOssStorageMock.mockResolvedValue([])
        getSystemConfigMock.mockResolvedValue({
            id: 0,
            fsProviderId: 1,
            ossProviderId: undefined,
        })

        const wrapper = mount(TasksView, {
            global: {
                stubs: {
                    DashboardTopBar: true,
                    TaskSubmissionModal: TaskSubmissionModalStub,
                },
            },
        })

        await flushView()

        const actionButton = wrapper.get('[data-test="open-task-button"]')
        expect(actionButton.text()).toContain('发起新任务')
        expect(actionButton.attributes()).not.toHaveProperty('disabled')
        expect(listFileSystemStorageMock).not.toHaveBeenCalled()
        expect(listOssStorageMock).not.toHaveBeenCalled()
        expect(getSystemConfigMock).not.toHaveBeenCalled()

        await actionButton.trigger('click')
        await flushView()

        expect(listFileSystemStorageMock).toHaveBeenCalledTimes(1)
        expect(listOssStorageMock).toHaveBeenCalledTimes(1)
        expect(getSystemConfigMock).toHaveBeenCalledTimes(1)

        await wrapper.get('[data-test="submit-metadata-parse"]').trigger('click')
        await flushView()

        expect(executeScanTaskMock).toHaveBeenCalledWith({
            body: { providerType: 'FILE_SYSTEM', providerId: 1 },
        })
        expect(wrapper.text()).not.toContain('任务已加入后台队列，状态统计将在稍后刷新。')
        expect(actionButton.text()).toContain('任务已提交')
        expect(actionButton.attributes('disabled')).toBeDefined()

        await vi.advanceTimersByTimeAsync(2000)
        await flushView()

        expect(actionButton.text()).toContain('发起新任务')
        expect(actionButton.attributes()).not.toHaveProperty('disabled')
    })

    it('auto refreshes while there are pending or running tasks', async () => {
        vi.useFakeTimers()
        listTaskLogsMock
            .mockResolvedValueOnce([{ taskType: 'METADATA_PARSE', status: 'PENDING', count: 1 }])
            .mockResolvedValueOnce([])

        mount(TasksView, {
            global: {
                stubs: {
                    DashboardTopBar: true,
                    TaskSubmissionModal: TaskSubmissionModalStub,
                },
            },
        })

        await flushView()
        expect(listTaskLogsMock).toHaveBeenCalledTimes(1)

        await vi.advanceTimersByTimeAsync(5000)
        await flushView()
        expect(listTaskLogsMock).toHaveBeenCalledTimes(2)

        await vi.advanceTimersByTimeAsync(5000)
        await flushView()
        expect(listTaskLogsMock).toHaveBeenCalledTimes(2)
    })

    it('refreshes task counts once when the task modal closes', async () => {
        listTaskLogsMock.mockResolvedValue([])
        listFileSystemStorageMock.mockResolvedValue([])
        listOssStorageMock.mockResolvedValue([])
        getSystemConfigMock.mockResolvedValue({
            id: 0,
            fsProviderId: 1,
            ossProviderId: undefined,
        })

        const wrapper = mount(TasksView, {
            global: {
                stubs: {
                    DashboardTopBar: true,
                    TaskSubmissionModal: TaskSubmissionModalStub,
                },
            },
        })

        await flushView()
        expect(listTaskLogsMock).toHaveBeenCalledTimes(1)

        await wrapper.get('[data-test="open-task-button"]').trigger('click')
        await flushView()
        await wrapper.get('[data-test="close-task-modal"]').trigger('click')
        await flushView()

        expect(listTaskLogsMock).toHaveBeenCalledTimes(2)
        expect(listFileSystemStorageMock).toHaveBeenCalledTimes(1)
        expect(listOssStorageMock).toHaveBeenCalledTimes(1)
        expect(getSystemConfigMock).toHaveBeenCalledTimes(1)
    })

    it('reuses provider data when reopening the task modal', async () => {
        listTaskLogsMock.mockResolvedValue([])
        listFileSystemStorageMock.mockResolvedValue([])
        listOssStorageMock.mockResolvedValue([])
        getSystemConfigMock.mockResolvedValue({
            id: 0,
            fsProviderId: 1,
            ossProviderId: undefined,
        })

        const wrapper = mount(TasksView, {
            global: {
                stubs: {
                    DashboardTopBar: true,
                    TaskSubmissionModal: TaskSubmissionModalStub,
                },
            },
        })

        await flushView()

        await wrapper.get('[data-test="open-task-button"]').trigger('click')
        await flushView()
        await wrapper.get('[data-test="close-task-modal"]').trigger('click')
        await flushView()
        await wrapper.get('[data-test="open-task-button"]').trigger('click')
        await flushView()

        expect(listFileSystemStorageMock).toHaveBeenCalledTimes(1)
        expect(listOssStorageMock).toHaveBeenCalledTimes(1)
        expect(getSystemConfigMock).toHaveBeenCalledTimes(1)
    })
})
