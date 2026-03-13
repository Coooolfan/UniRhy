import { ref } from 'vue'
import { api, normalizeApiError } from '@/ApiInstance'
import { type FileProviderType } from '@/__generated/model/enums/FileProviderType'
import type { TaskType } from '@/__generated/model/enums/TaskType'
import type {
    AsyncTaskLogCountRow,
    ScanTaskRequest,
    TranscodeTaskRequest,
} from '@/__generated/model/static'

export type TaskProviderOption = {
    id: number
    name: string
    type: FileProviderType
    readonly: boolean
}

export const TASK_TYPE_LABEL_MAP: Record<TaskType, string> = {
    SCAN: '媒体库扫描',
    TRANSCODE: '媒体转码',
}

export const useTaskManagement = () => {
    const taskCounts = ref<ReadonlyArray<AsyncTaskLogCountRow>>([])
    const providerOptions = ref<TaskProviderOption[]>([])

    const isLoadingTaskCounts = ref(false)
    const isLoadingProviders = ref(false)
    const isSubmitting = ref(false)

    const taskError = ref('')
    const submitError = ref('')

    const fetchTaskCounts = async () => {
        isLoadingTaskCounts.value = true
        taskError.value = ''
        try {
            taskCounts.value = await api.taskController.listTaskLogs()
        } catch (error) {
            const normalized = normalizeApiError(error)
            taskError.value = normalized.message ?? '获取任务状态失败'
        } finally {
            isLoadingTaskCounts.value = false
        }
    }

    const refreshSubmittedTaskData = () => {
        setTimeout(() => {
            void fetchTaskCounts()
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
                    readonly: node.readonly,
                })
            })

            ossNodes.forEach((node) => {
                options.push({
                    id: node.id,
                    name: `[OSS] ${node.name}`,
                    type: 'OSS',
                    readonly: node.readonly,
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
        void Promise.all([fetchTaskCounts(), fetchProviders()])
    }

    const clearSubmitError = () => {
        submitError.value = ''
    }

    return {
        taskCounts,
        providerOptions,
        isLoadingTaskCounts,
        isLoadingProviders,
        isSubmitting,
        taskError,
        submitError,
        fetchTaskCounts,
        fetchProviders,
        startScanTask,
        startTranscodeTask,
        clearSubmitError,
        init,
    }
}
