import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import AppModalHost from '@/components/modals/AppModalHost.vue'
import { useUserStore } from '@/stores/user'

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
            pluginController: {
                listPlugins: vi.fn(),
                submitPluginTask: vi.fn(),
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
const listPluginsMock = vi.mocked(api.pluginController.listPlugins)
const submitPluginTaskMock = vi.mocked(api.pluginController.submitPluginTask)

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
                TasksView,
                AppModalHost,
            },
            template: `
                <div>
                    <TasksView />
                    <AppModalHost />
                </div>
            `,
        },
        {
            global: {
                stubs: {
                    DashboardTopBar: true,
                    teleport: true,
                    transition: false,
                },
            },
        },
    )

const setUser = (admin: boolean) => {
    const userStore = useUserStore()
    userStore.user = {
        id: 1,
        name: 'Tester',
        email: 'tester@example.com',
        admin,
        preferences: {
            preferredAssetFormat: 'audio/opus',
        },
    }
}

describe('TasksView', () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        setUser(true)
        listTaskLogsMock.mockReset()
        executeScanTaskMock.mockReset()
        executeTranscodeTaskMock.mockReset()
        listFileSystemStorageMock.mockReset()
        listOssStorageMock.mockReset()
        getSystemConfigMock.mockReset()
        listPluginsMock.mockReset()
        submitPluginTaskMock.mockReset()
        listPluginsMock.mockResolvedValue([])
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
        const wrapper = mountWithModalHost()

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
        expect(wrapper.text()).toMatch(/其中\s*5\s*个排队，1\s*个正在执行。?/u)
        expect(wrapper.text()).toMatch(/1\s*Failed\s*失败/u)
    })

    it('shows submit feedback on the action button for two seconds after a successful submission', async () => {
        vi.useFakeTimers()
        listTaskLogsMock.mockResolvedValue([])
        executeScanTaskMock.mockResolvedValue(undefined)
        listFileSystemStorageMock.mockResolvedValue([
            {
                id: 1,
                name: 'Library',
                parentPath: '/music/library',
                readonly: false,
            },
        ])
        listOssStorageMock.mockResolvedValue([])
        getSystemConfigMock.mockResolvedValue({
            id: 0,
            fsProviderId: undefined,
            ossProviderId: undefined,
        })

        const wrapper = mountWithModalHost()

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

        await wrapper.get('[data-test="task-submit-button"]').trigger('click')
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

        mountWithModalHost()

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

        const wrapper = mountWithModalHost()

        await flushView()
        expect(listTaskLogsMock).toHaveBeenCalledTimes(1)

        await wrapper.get('[data-test="open-task-button"]').trigger('click')
        await flushView()
        const cancelButton = wrapper
            .findAll('button')
            .find((button) => button.text().includes('取消'))
        expect(cancelButton).toBeTruthy()
        await cancelButton!.trigger('click')
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

        const wrapper = mountWithModalHost()

        await flushView()

        await wrapper.get('[data-test="open-task-button"]').trigger('click')
        await flushView()
        const cancelButton = wrapper
            .findAll('button')
            .find((button) => button.text().includes('取消'))
        expect(cancelButton).toBeTruthy()
        await cancelButton!.trigger('click')
        await flushView()
        await wrapper.get('[data-test="open-task-button"]').trigger('click')
        await flushView()

        expect(listFileSystemStorageMock).toHaveBeenCalledTimes(1)
        expect(listOssStorageMock).toHaveBeenCalledTimes(1)
        expect(getSystemConfigMock).toHaveBeenCalledTimes(1)
    })

    it('hides task submission entry for ordinary users while keeping status readable', async () => {
        setUser(false)
        listTaskLogsMock.mockResolvedValue([])

        const wrapper = mountWithModalHost()

        await flushView()

        expect(listTaskLogsMock).toHaveBeenCalledTimes(1)
        expect(wrapper.find('[data-test="open-task-button"]').exists()).toBe(false)
        expect(wrapper.text()).toContain('状态概览')
        expect(wrapper.text()).toContain('任务类型分布')
    })
})
