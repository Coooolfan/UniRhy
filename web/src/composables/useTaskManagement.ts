import { ref } from 'vue'
import { api, normalizeApiError } from '@/ApiInstance'
import { type FileProviderType } from '@/__generated/model/enums/FileProviderType'
import type { TaskType } from '@/__generated/model/enums/TaskType'
import type { PluginInfoResponse } from '@/__generated/model/static/PluginInfoResponse'
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
    isSystemNode: boolean
}

export const BUILTIN_TASK_TYPE_LABEL_MAP: Partial<Record<TaskType, string>> = {
    METADATA_PARSE: '元数据解析',
    TRANSCODE: '媒体转码',
}

export const useTaskManagement = () => {
    const taskCounts = ref<ReadonlyArray<AsyncTaskLogCountRow>>([])
    const providerOptions = ref<TaskProviderOption[]>([])
    const pluginList = ref<ReadonlyArray<PluginInfoResponse>>([])
    const hasLoadedProviders = ref(false)

    const isLoadingTaskCounts = ref(false)
    const isLoadingProviders = ref(false)
    const isLoadingPlugins = ref(false)
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

    const fetchProviders = async (force = false) => {
        if (isLoadingProviders.value) {
            return
        }
        if (!force && hasLoadedProviders.value) {
            return
        }

        isLoadingProviders.value = true
        try {
            const [fsNodes, ossNodes, systemConfig] = await Promise.all([
                api.fileSystemStorageController.list(),
                api.ossStorageController.list(),
                api.systemConfigController.get(),
            ])

            const options: TaskProviderOption[] = []

            fsNodes.forEach((node) => {
                options.push({
                    id: node.id,
                    name: `[本地] ${node.name}`,
                    type: 'FILE_SYSTEM',
                    readonly: node.readonly,
                    isSystemNode: node.id === systemConfig.fsProviderId,
                })
            })

            ossNodes.forEach((node) => {
                options.push({
                    id: node.id,
                    name: `[OSS] ${node.name}`,
                    type: 'OSS',
                    readonly: node.readonly,
                    isSystemNode: node.id === systemConfig.ossProviderId,
                })
            })

            providerOptions.value = options
            hasLoadedProviders.value = true
        } catch (error) {
            console.error('Failed to fetch providers', error)
        } finally {
            isLoadingProviders.value = false
        }
    }

    const fetchPlugins = async () => {
        if (isLoadingPlugins.value) return
        isLoadingPlugins.value = true
        try {
            pluginList.value = await api.pluginController.listPlugins()
        } catch (error) {
            console.error('Failed to fetch plugins', error)
        } finally {
            isLoadingPlugins.value = false
        }
    }

    const startMetadataParseTask = (providerType: FileProviderType, providerId: number) => {
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

    const startPluginTask = (taskType: string, params: Record<string, string>) =>
        executeTask(() => api.pluginController.submitPluginTask({ taskType, body: params }))

    const resolveTaskLabel = (taskType: string): string => {
        const builtin = (BUILTIN_TASK_TYPE_LABEL_MAP as Record<string, string | undefined>)[
            taskType
        ]
        if (builtin) return builtin
        const plugin = pluginList.value.find((p) => p.taskType === taskType)
        return plugin?.name ?? plugin?.id ?? taskType
    }

    const init = () => {
        void fetchTaskCounts()
        void fetchPlugins()
    }

    const clearSubmitError = () => {
        submitError.value = ''
    }

    return {
        taskCounts,
        providerOptions,
        pluginList,
        isLoadingTaskCounts,
        isLoadingProviders,
        isLoadingPlugins,
        isSubmitting,
        taskError,
        submitError,
        fetchTaskCounts,
        fetchProviders,
        fetchPlugins,
        startMetadataParseTask,
        startTranscodeTask,
        startPluginTask,
        resolveTaskLabel,
        clearSubmitError,
        init,
    }
}
