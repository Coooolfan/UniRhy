import { ref } from 'vue'
import { api, normalizeApiError } from '@/ApiInstance'
import type { RunningTaskView } from '@/__generated/model/static/RunningTaskView'
import { type FileProviderType } from '@/__generated/model/enums/FileProviderType'

export type TaskProviderOption = {
    id: number
    name: string
    type: FileProviderType
}

export const useTaskManagement = () => {
    const runningTasks = ref<ReadonlyArray<RunningTaskView>>([])
    const providerOptions = ref<TaskProviderOption[]>([])

    const isLoadingTasks = ref(false)
    const isLoadingProviders = ref(false)
    const isSubmitting = ref(false)

    const taskError = ref('')
    const submitError = ref('')
    const submitSuccess = ref('')

    const fetchRunningTasks = async () => {
        isLoadingTasks.value = true
        taskError.value = ''
        try {
            runningTasks.value = await api.taskController.listRunningTasks()
        } catch (error) {
            const normalized = normalizeApiError(error)
            taskError.value = normalized.message ?? '获取运行中任务失败'
        } finally {
            isLoadingTasks.value = false
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

    const startScanTask = async (providerType: FileProviderType, providerId: number) => {
        isSubmitting.value = true
        submitError.value = ''
        submitSuccess.value = ''
        try {
            await api.taskController.executeScanTask({
                body: {
                    providerType,
                    providerId,
                },
            })
            submitSuccess.value = '扫描任务已提交'
            // Refresh running tasks after short delay
            setTimeout(() => {
                fetchRunningTasks()
            }, 1000)
        } catch (error) {
            const normalized = normalizeApiError(error)
            submitError.value = normalized.message ?? '提交任务失败'
        } finally {
            isSubmitting.value = false
        }
    }

    const init = () => {
        fetchRunningTasks()
        fetchProviders()
    }

    return {
        runningTasks,
        providerOptions,
        isLoadingTasks,
        isLoadingProviders,
        isSubmitting,
        taskError,
        submitError,
        submitSuccess,
        fetchRunningTasks,
        startScanTask,
        init,
    }
}
