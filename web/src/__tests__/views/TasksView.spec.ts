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
                executeVectorizeTask: vi.fn(),
            },
            fileSystemStorageController: {
                list: vi.fn(),
            },
            ossStorageController: {
                list: vi.fn(),
            },
        },
    }
})

import { api } from '@/ApiInstance'
import TasksView from '@/views/TasksView.vue'

const listTaskLogsMock = vi.mocked(api.taskController.listTaskLogs)
const executeScanTaskMock = vi.mocked(api.taskController.executeScanTask)
const executeTranscodeTaskMock = vi.mocked(api.taskController.executeTranscodeTask)
const executeVectorizeTaskMock = vi.mocked(api.taskController.executeVectorizeTask)
const listFileSystemStorageMock = vi.mocked(api.fileSystemStorageController.list)
const listOssStorageMock = vi.mocked(api.ossStorageController.list)

const TaskSubmissionModalStub = defineComponent({
    name: 'TaskSubmissionModal',
    props: {
        open: {
            type: Boolean,
            default: false,
        },
    },
    emits: ['submit-metadata-parse', 'submit-transcode', 'submit-vectorize', 'close'],
    template:
        "<div v-if=\"open\"><button data-test=\"submit-metadata-parse\" @click=\"$emit('submit-metadata-parse', { providerType: 'FILE_SYSTEM', providerId: 1 })\">submit-metadata-parse</button><button data-test=\"submit-vectorize\" @click=\"$emit('submit-vectorize', { srcProviderType: 'FILE_SYSTEM', srcProviderId: 1, apiEndpoint: 'https://api.example.com/v1/embeddings', apiKey: 'secret-key', modelName: 'bge-m3' })\">submit-vectorize</button><button data-test=\"close-task-modal\" @click=\"$emit('close')\">close</button></div>",
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
        executeVectorizeTaskMock.mockReset()
        listFileSystemStorageMock.mockReset()
        listOssStorageMock.mockReset()
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
            { taskType: 'VECTORIZE', status: 'PENDING', count: 1 },
            { taskType: 'VECTORIZE', status: 'RUNNING', count: 2 },
            { taskType: 'VECTORIZE', status: 'COMPLETED', count: 4 },
            { taskType: 'VECTORIZE', status: 'FAILED', count: 0 },
        ])
        listFileSystemStorageMock.mockResolvedValueOnce([])
        listOssStorageMock.mockResolvedValueOnce([])

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
        expect(wrapper.text()).toContain('9 个任务待处理或执行中')
        expect(wrapper.text()).toContain('元数据解析')
        expect(wrapper.text()).toContain('媒体转码')
        expect(wrapper.text()).toContain('向量化')
        expect(wrapper.text()).toContain('状态概览')
        expect(wrapper.text()).toContain('任务类型分布')
        expect(wrapper.text()).toMatch(/其中\s*6\s*个排队，3\s*个正在执行。?/)
        expect(wrapper.text()).toMatch(/1\s*Failed\s*失败/)
    })

    it('shows submit feedback on the action button for two seconds after a successful submission', async () => {
        vi.useFakeTimers()
        listTaskLogsMock.mockResolvedValue([])
        executeScanTaskMock.mockResolvedValue(undefined)
        listFileSystemStorageMock.mockResolvedValue([])
        listOssStorageMock.mockResolvedValue([])

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

        await actionButton.trigger('click')
        await flushView()

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

    it('submits vectorize tasks through the generated task controller and reuses success feedback', async () => {
        vi.useFakeTimers()
        listTaskLogsMock.mockResolvedValue([])
        executeVectorizeTaskMock.mockResolvedValue(undefined)
        listFileSystemStorageMock.mockResolvedValue([])
        listOssStorageMock.mockResolvedValue([])

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
        await actionButton.trigger('click')
        await flushView()

        await wrapper.get('[data-test="submit-vectorize"]').trigger('click')
        await flushView()

        expect(executeVectorizeTaskMock).toHaveBeenCalledWith({
            body: {
                srcProviderType: 'FILE_SYSTEM',
                srcProviderId: 1,
                apiEndpoint: 'https://api.example.com/v1/embeddings',
                apiKey: 'secret-key',
                modelName: 'bge-m3',
            },
        })
        expect(actionButton.text()).toContain('任务已提交')
        expect(actionButton.attributes('disabled')).toBeDefined()

        await vi.advanceTimersByTimeAsync(2000)
        await flushView()

        expect(actionButton.text()).toContain('发起新任务')
    })

    it('auto refreshes while there are pending or running tasks', async () => {
        vi.useFakeTimers()
        listTaskLogsMock
            .mockResolvedValueOnce([{ taskType: 'METADATA_PARSE', status: 'PENDING', count: 1 }])
            .mockResolvedValueOnce([])
        listFileSystemStorageMock.mockResolvedValue([])
        listOssStorageMock.mockResolvedValue([])

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
    })
})
