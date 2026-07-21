import { ref } from 'vue'
import { api } from '@/ApiInstance'
import { i18n } from '@/i18n'
import { resolveErrorMessage } from '@/i18n/errors'
import type { TaskDefinitionView, TaskStatisticsResponse } from '@/__generated/model/static'

export type FileProviderType = 'FILE_SYSTEM' | 'OSS'

export type TaskProviderOption = {
    id: number
    name: string
    type: FileProviderType
    readonly: boolean
    isSystemNode: boolean
}

export const BUILT_IN_NAMESPACE = 'app.unirhy.built-in'

export const taskKeyOf = (namespace: string, taskType: string) => `${namespace}:${taskType}`

const builtinTaskTypeLabel = (namespace: string, taskType: string): string | undefined => {
    if (namespace !== BUILT_IN_NAMESPACE) return undefined
    if (taskType === 'METADATA_PARSE') return i18n.global.t('taskSubmission.metadataParse')
    if (taskType === 'TRANSCODE') return i18n.global.t('taskSubmission.transcode')
    return undefined
}

export const useTaskManagement = () => {
    const taskStatistics = ref<ReadonlyArray<TaskStatisticsResponse>>([])
    const taskDefinitions = ref<ReadonlyArray<TaskDefinitionView>>([])
    const providerOptions = ref<TaskProviderOption[]>([])
    const hasLoadedProviders = ref(false)

    const isLoadingTaskStatistics = ref(false)
    const isLoadingProviders = ref(false)
    const isLoadingDefinitions = ref(false)
    const isSubmitting = ref(false)

    const taskError = ref('')
    const submitError = ref('')

    const fetchTaskStatistics = async () => {
        isLoadingTaskStatistics.value = true
        taskError.value = ''
        try {
            taskStatistics.value = await api.taskStatisticsController.getTaskStatistics({})
        } catch (error) {
            taskError.value = resolveErrorMessage(error, 'errors.fallback.taskStatus')
        } finally {
            isLoadingTaskStatistics.value = false
        }
    }

    const refreshSubmittedTaskData = () => {
        setTimeout(() => {
            void fetchTaskStatistics()
        }, 1000)
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
                    name: i18n.global.t('taskSubmission.fsProviderLabel', { name: node.name }),
                    type: 'FILE_SYSTEM',
                    readonly: node.readonly,
                    isSystemNode: node.id === systemConfig.fsProviderId,
                })
            })

            ossNodes.forEach((node) => {
                options.push({
                    id: node.id,
                    name: i18n.global.t('taskSubmission.ossProviderLabel', { name: node.name }),
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

    const fetchTaskDefinitions = async () => {
        if (isLoadingDefinitions.value) return
        isLoadingDefinitions.value = true
        try {
            taskDefinitions.value = await api.taskDefinitionController.listTaskDefinitions()
        } catch (error) {
            console.error('Failed to fetch task definitions', error)
        } finally {
            isLoadingDefinitions.value = false
        }
    }

    const submitTask = async (
        namespace: string,
        taskType: string,
        params: Record<string, unknown>,
    ) => {
        isSubmitting.value = true
        submitError.value = ''
        try {
            await api.taskSubmissionController.createSubmission({
                body: { namespace, taskType, params },
            })
            refreshSubmittedTaskData()
            return true
        } catch (error) {
            submitError.value = resolveErrorMessage(error, 'errors.fallback.taskSubmit')
            return false
        } finally {
            isSubmitting.value = false
        }
    }

    const resolveTaskLabel = (namespace: string, taskType: string): string => {
        const builtin = builtinTaskTypeLabel(namespace, taskType)
        if (builtin) return builtin
        const definition = taskDefinitions.value.find(
            (d) => d.namespace === namespace && d.taskType === taskType,
        )
        return definition?.name ?? taskKeyOf(namespace, taskType)
    }

    const init = () => {
        void fetchTaskStatistics()
        void fetchTaskDefinitions()
    }

    const clearSubmitError = () => {
        submitError.value = ''
    }

    return {
        taskStatistics,
        taskDefinitions,
        providerOptions,
        isLoadingTaskStatistics,
        isLoadingProviders,
        isLoadingDefinitions,
        isSubmitting,
        taskError,
        submitError,
        fetchTaskStatistics,
        fetchProviders,
        fetchTaskDefinitions,
        submitTask,
        resolveTaskLabel,
        clearSubmitError,
        init,
    }
}
