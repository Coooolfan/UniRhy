import { ref } from 'vue'
import { api, normalizeApiError } from '@/ApiInstance'
import type { AsyncTaskLogDto } from '@/__generated/model/dto/AsyncTaskLogDto'
import { type FileProviderType } from '@/__generated/model/enums/FileProviderType'
import type { TaskType } from '@/__generated/model/enums/TaskType'
import type { ScanTaskRequest, TranscodeTaskRequest } from '@/__generated/model/static'

export type TaskProviderOption = {
    id: number
    name: string
    type: FileProviderType
}

export const TASK_TYPE_LABEL_MAP: Record<TaskType, string> = {
    SCAN: '媒体库扫描',
    TRANSCODE: '媒体转码',
}

type TaskLogItem = AsyncTaskLogDto['TaskController/DEFAULT_ASYNC_TASK_LOG_FETCHER']

const RUNNING_TASK_PAGE_SIZE = 50
const TASK_LOG_PAGE_SIZE = 5

export const useTaskManagement = () => {
    const runningTasks = ref<ReadonlyArray<TaskLogItem>>([])
    const providerOptions = ref<TaskProviderOption[]>([])
    const taskLogs = ref<ReadonlyArray<TaskLogItem>>([])
    const taskLogPageIndex = ref(0)
    const taskLogTotalPageCount = ref(0)
    const taskLogTotalRowCount = ref(0)

    const isLoadingTasks = ref(false)
    const isLoadingTaskLogs = ref(false)
    const isLoadingProviders = ref(false)
    const isSubmitting = ref(false)

    const taskError = ref('')
    const submitError = ref('')

    const refreshSubmittedTaskData = () => {
        setTimeout(() => {
            void Promise.all([fetchRunningTasks(), fetchTaskLogs(0)])
        }, 1000)
    }

    const executeTask = async (executor: () => Promise<void>) => {
        isSubmitting.value = true
        submitError.value = ''
        try {
            await executor()
            refreshSubmittedTaskData()
            return true
        } catch (error) {
            const normalized = normalizeApiError(error)
            submitError.value = normalized.message ?? '提交任务失败'
            return false
        } finally {
            isSubmitting.value = false
        }
    }

    const fetchRunningTasks = async () => {
        isLoadingTasks.value = true
        taskError.value = ''
        try {
            const page = await api.taskController.listTaskLogs({
                pageIndex: 0,
                pageSize: RUNNING_TASK_PAGE_SIZE,
                status: 'RUNNING',
            })
            runningTasks.value = page.rows
        } catch (error) {
            const normalized = normalizeApiError(error)
            taskError.value = normalized.message ?? '获取运行中任务失败'
        } finally {
            isLoadingTasks.value = false
        }
    }

    const fetchTaskLogs = async (pageIndex = taskLogPageIndex.value) => {
        isLoadingTaskLogs.value = true
        taskError.value = ''
        try {
            const page = await api.taskController.listTaskLogs({
                pageIndex,
                pageSize: TASK_LOG_PAGE_SIZE,
            })
            taskLogs.value = page.rows
            taskLogPageIndex.value = pageIndex
            taskLogTotalPageCount.value = page.totalPageCount
            taskLogTotalRowCount.value = page.totalRowCount
        } catch (error) {
            const normalized = normalizeApiError(error)
            taskError.value = normalized.message ?? '获取任务日志失败'
        } finally {
            isLoadingTaskLogs.value = false
        }
    }

    const fetchProviders = async () => {
        isLoadingProviders.value = true
        try {
            const [fsNodes, ossNodes] = await Promise.all([
                api.fileSystemStorageController.list(),
                api.ossStorageController.list(),
            ])

            const options: TaskProviderOption[] = []

            fsNodes.forEach((node) => {
                options.push({
                    id: node.id,
                    name: `[本地] ${node.name}`,
                    type: 'FILE_SYSTEM',
                })
            })

            ossNodes.forEach((node) => {
                options.push({
                    id: node.id,
                    name: `[OSS] ${node.name}`,
                    type: 'OSS',
                })
            })

            providerOptions.value = options
        } catch (error) {
            console.error('Failed to fetch providers', error)
            // Non-critical, just empty list
        } finally {
            isLoadingProviders.value = false
        }
    }

    const startScanTask = (providerType: FileProviderType, providerId: number) => {
        const request: ScanTaskRequest = { providerType, providerId }
        return executeTask(() =>
            api.taskController.executeScanTask({
                body: request,
            }),
        )
    }

    const startTranscodeTask = (request: TranscodeTaskRequest) =>
        executeTask(() =>
            api.taskController.executeTranscodeTask({
                body: request,
            }),
        )

    const init = () => {
        void Promise.all([fetchRunningTasks(), fetchProviders(), fetchTaskLogs(0)])
    }

    const clearSubmitError = () => {
        submitError.value = ''
    }

    return {
        runningTasks,
        providerOptions,
        taskLogs,
        taskLogPageIndex,
        taskLogTotalPageCount,
        taskLogTotalRowCount,
        taskLogPageSize: TASK_LOG_PAGE_SIZE,
        isLoadingTasks,
        isLoadingTaskLogs,
        isLoadingProviders,
        isSubmitting,
        taskError,
        submitError,
        fetchRunningTasks,
        fetchTaskLogs,
        fetchProviders,
        startScanTask,
        startTranscodeTask,
        clearSubmitError,
        init,
    }
}
