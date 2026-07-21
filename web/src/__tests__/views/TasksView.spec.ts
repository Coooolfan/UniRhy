import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import AppModalHost from '@/components/modals/AppModalHost.vue'
import { useUserStore } from '@/stores/user'
import { i18n } from '@/i18n'
import type { TaskStatisticsResponse, TaskStatusCounts } from '@/__generated/model/static'

vi.mock('@/ApiInstance', async (importOriginal) => {
    const actual = await importOriginal<typeof import('@/ApiInstance')>()
    return {
        ...actual,
        api: {
            taskStatisticsController: {
                getTaskStatistics: vi.fn(),
            },
            taskDefinitionController: {
                listTaskDefinitions: vi.fn(),
            },
            taskSubmissionController: {
                createSubmission: vi.fn(),
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

const getTaskStatisticsMock = vi.mocked(api.taskStatisticsController.getTaskStatistics)
const listTaskDefinitionsMock = vi.mocked(api.taskDefinitionController.listTaskDefinitions)
const createSubmissionMock = vi.mocked(api.taskSubmissionController.createSubmission)
const listFileSystemStorageMock = vi.mocked(api.fileSystemStorageController.list)
const listOssStorageMock = vi.mocked(api.ossStorageController.list)
const getSystemConfigMock = vi.mocked(api.systemConfigController.get)

const BUILT_IN_NAMESPACE = 'app.unirhy.built-in'

const counts = (partial: Partial<Omit<TaskStatusCounts, 'total'>> = {}): TaskStatusCounts => {
    const active = partial.active ?? 0
    const completed = partial.completed ?? 0
    const failed = partial.failed ?? 0
    const cancelled = partial.cancelled ?? 0
    return { active, completed, failed, cancelled, total: active + completed + failed + cancelled }
}

const statsRow = (
    namespace: string,
    taskType: string,
    tasks: Partial<Omit<TaskStatusCounts, 'total'>> = {},
): TaskStatisticsResponse => ({
    namespace,
    taskType,
    submissions: counts(),
    tasks: counts(tasks),
})

const emptyBuiltInStats = () => [
    statsRow(BUILT_IN_NAMESPACE, 'METADATA_PARSE'),
    statsRow(BUILT_IN_NAMESPACE, 'TRANSCODE'),
]

const builtInDefinitions = [
    {
        namespace: BUILT_IN_NAMESPACE,
        taskType: 'METADATA_PARSE',
        name: '元数据解析',
        formDefinition: {
            schema: {
                type: 'object',
                properties: {},
                required: [],
                additionalProperties: false,
            },
            order: [],
        },
    },
    {
        namespace: BUILT_IN_NAMESPACE,
        taskType: 'TRANSCODE',
        name: '音频转码',
        formDefinition: {
            schema: {
                type: 'object',
                properties: {},
                required: [],
                additionalProperties: false,
            },
            order: [],
        },
    },
]

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
        i18n.global.locale.value = 'zh-CN'
        setActivePinia(createPinia())
        setUser(true)
        getTaskStatisticsMock.mockReset()
        listTaskDefinitionsMock.mockReset()
        createSubmissionMock.mockReset()
        listFileSystemStorageMock.mockReset()
        listOssStorageMock.mockReset()
        getSystemConfigMock.mockReset()
        listTaskDefinitionsMock.mockResolvedValue(builtInDefinitions)
        vi.useRealTimers()
    })

    it('renders aggregated task status counts from the statistics endpoint', async () => {
        getTaskStatisticsMock.mockResolvedValueOnce([
            statsRow(BUILT_IN_NAMESPACE, 'METADATA_PARSE', {
                active: 3,
                completed: 5,
                failed: 1,
            }),
            statsRow(BUILT_IN_NAMESPACE, 'TRANSCODE', {
                active: 3,
                completed: 7,
            }),
        ])
        const wrapper = mountWithModalHost()

        await flushView()

        expect(getTaskStatisticsMock).toHaveBeenCalledTimes(1)
        expect(listFileSystemStorageMock).not.toHaveBeenCalled()
        expect(listOssStorageMock).not.toHaveBeenCalled()
        expect(getSystemConfigMock).not.toHaveBeenCalled()
        expect(wrapper.text()).toContain('6 个任务待处理或执行中')
        expect(wrapper.text()).toContain('元数据解析')
        expect(wrapper.text()).toContain('媒体转码')
        expect(wrapper.text()).toContain('状态概览')
        expect(wrapper.text()).toContain('任务类型分布')
        expect(wrapper.text()).toMatch(/1\s*Failed\s*失败/u)
    })

    it('updates status labels when the locale changes', async () => {
        getTaskStatisticsMock.mockResolvedValue([
            statsRow(BUILT_IN_NAMESPACE, 'METADATA_PARSE', { active: 1 }),
        ])
        const wrapper = mountWithModalHost()

        await flushView()
        expect(wrapper.text()).toMatch(/Active\s*进行中/u)

        i18n.global.locale.value = 'en'
        await nextTick()

        expect(wrapper.text()).toMatch(/Active\s*Active/u)
        expect(wrapper.text()).not.toContain('进行中')
    })

    it('shows submit feedback on the action button for two seconds after a successful submission', async () => {
        vi.useFakeTimers()
        getTaskStatisticsMock.mockResolvedValue(emptyBuiltInStats())
        createSubmissionMock.mockResolvedValue({ submissionId: 1 })
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

        expect(listTaskDefinitionsMock).toHaveBeenCalledTimes(1)
        expect(listFileSystemStorageMock).toHaveBeenCalledTimes(1)
        expect(listOssStorageMock).toHaveBeenCalledTimes(1)
        expect(getSystemConfigMock).toHaveBeenCalledTimes(1)

        await wrapper.get('[data-test="task-submit-button"]').trigger('click')
        await flushView()

        expect(createSubmissionMock).toHaveBeenCalledWith({
            body: {
                namespace: BUILT_IN_NAMESPACE,
                taskType: 'METADATA_PARSE',
                params: { providerType: 'FILE_SYSTEM', providerId: 1 },
            },
        })
        expect(actionButton.text()).toContain('任务已提交')
        expect(actionButton.attributes('disabled')).toBeDefined()

        await vi.advanceTimersByTimeAsync(2000)
        await flushView()

        expect(actionButton.text()).toContain('发起新任务')
        expect(actionButton.attributes()).not.toHaveProperty('disabled')
    })

    it('auto refreshes while there are active tasks', async () => {
        vi.useFakeTimers()
        getTaskStatisticsMock
            .mockResolvedValueOnce([statsRow(BUILT_IN_NAMESPACE, 'METADATA_PARSE', { active: 1 })])
            .mockResolvedValueOnce(emptyBuiltInStats())

        mountWithModalHost()

        await flushView()
        expect(getTaskStatisticsMock).toHaveBeenCalledTimes(1)

        await vi.advanceTimersByTimeAsync(5000)
        await flushView()
        expect(getTaskStatisticsMock).toHaveBeenCalledTimes(2)

        await vi.advanceTimersByTimeAsync(5000)
        await flushView()
        expect(getTaskStatisticsMock).toHaveBeenCalledTimes(2)
    })

    it('refreshes statistics once when the task modal closes', async () => {
        getTaskStatisticsMock.mockResolvedValue(emptyBuiltInStats())
        listFileSystemStorageMock.mockResolvedValue([])
        listOssStorageMock.mockResolvedValue([])
        getSystemConfigMock.mockResolvedValue({
            id: 0,
            fsProviderId: 1,
            ossProviderId: undefined,
        })

        const wrapper = mountWithModalHost()

        await flushView()
        expect(getTaskStatisticsMock).toHaveBeenCalledTimes(1)

        await wrapper.get('[data-test="open-task-button"]').trigger('click')
        await flushView()
        const cancelButton = wrapper
            .findAll('button')
            .find((button) => button.text().includes('取消'))
        expect(cancelButton).toBeTruthy()
        await cancelButton!.trigger('click')
        await flushView()

        expect(getTaskStatisticsMock).toHaveBeenCalledTimes(2)
        expect(listFileSystemStorageMock).toHaveBeenCalledTimes(1)
        expect(listOssStorageMock).toHaveBeenCalledTimes(1)
        expect(getSystemConfigMock).toHaveBeenCalledTimes(1)
    })

    it('reuses provider data when reopening the task modal', async () => {
        getTaskStatisticsMock.mockResolvedValue(emptyBuiltInStats())
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
        getTaskStatisticsMock.mockResolvedValue(emptyBuiltInStats())

        const wrapper = mountWithModalHost()

        await flushView()

        expect(getTaskStatisticsMock).toHaveBeenCalledTimes(1)
        expect(wrapper.find('[data-test="open-task-button"]').exists()).toBe(false)
        expect(wrapper.text()).toContain('状态概览')
        expect(wrapper.text()).toContain('任务类型分布')
    })
})
