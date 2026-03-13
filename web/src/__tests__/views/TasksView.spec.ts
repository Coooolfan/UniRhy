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

const TaskSubmissionModalStub = defineComponent({
    name: 'TaskSubmissionModal',
    props: {
        open: {
            type: Boolean,
            default: false,
        },
    },
    emits: ['submit-scan', 'submit-transcode', 'close'],
    template:
        '<div v-if="open"><button data-test="submit-scan" @click="$emit(\'submit-scan\', { providerType: \'FILE_SYSTEM\', providerId: 1 })">submit-scan</button></div>',
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
        vi.useRealTimers()
    })

    it('renders aggregated task status counts from the backend stats endpoint', async () => {
        listTaskLogsMock.mockResolvedValueOnce([
            { taskType: 'SCAN', status: 'PENDING', count: 2 },
            { taskType: 'SCAN', status: 'RUNNING', count: 1 },
            { taskType: 'SCAN', status: 'COMPLETED', count: 5 },
            { taskType: 'SCAN', status: 'FAILED', count: 1 },
            { taskType: 'TRANSCODE', status: 'PENDING', count: 3 },
            { taskType: 'TRANSCODE', status: 'RUNNING', count: 0 },
            { taskType: 'TRANSCODE', status: 'COMPLETED', count: 7 },
            { taskType: 'TRANSCODE', status: 'FAILED', count: 0 },
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
        expect(wrapper.text()).toContain('6 个任务待处理或执行中')
        expect(wrapper.text()).toContain('媒体库扫描')
        expect(wrapper.text()).toContain('媒体转码')
        expect(wrapper.text()).toContain('状态概览')
        expect(wrapper.text()).toContain('任务类型分布')
        expect(wrapper.text()).toContain('存在失败任务')
        expect(wrapper.text()).toContain('队列处理中')
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

        await wrapper.get('[data-test="submit-scan"]').trigger('click')
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
})
