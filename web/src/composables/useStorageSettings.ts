import { computed, ref } from 'vue'
import { api, normalizeApiError } from '@/ApiInstance'
import type { FileProviderType } from '@/__generated/model/enums/FileProviderType'
import type { SystemConfigUpdate } from '@/__generated/model/static'

export type StorageNode = {
    id: number
    type: FileProviderType
    name: string
    parentPath: string
    readonly: boolean
    host?: string
    bucket?: string
    accessKey?: string
}

export type SystemConfig = {
    ossProviderId: number | null
    fsProviderId: number | null
}

export type StorageNodeForm = {
    type: FileProviderType
    name: string
    parentPath: string
    readonly: boolean
    host: string
    bucket: string
    accessKey: string
    secretKey: string
}

type SystemConfigProviderUpdate = {
    readonly fsProviderId: number | null
    readonly ossProviderId: number | null
}

type StorageNodeFormValidationResult =
    | { error: string }
    | {
          payload: {
              type: FileProviderType
              name: string
              parentPath: string
              readonly: boolean
              host?: string
              bucket?: string
              accessKey?: string
              secretKey?: string
          }
      }

const validateStorageNodeForm = (
    form: StorageNodeForm,
    options: { mode: 'create' | 'update' },
): StorageNodeFormValidationResult => {
    const name = form.name.trim()
    const parentPath = form.parentPath.trim()
    const host = form.host.trim()
    const bucket = form.bucket.trim()
    const accessKey = form.accessKey.trim()
    const secretKey = form.secretKey.trim()

    if (!name) {
        return { error: '请填写节点名称' }
    }

    if (form.type === 'FILE_SYSTEM' && !parentPath) {
        return { error: '请填写本地存储根路径' }
    }

    if (form.type === 'OSS') {
        if (!host || !bucket || !accessKey) {
            return { error: '请填写对象存储 Endpoint、Bucket 与 Access Key' }
        }
        if (options.mode === 'create' && !secretKey) {
            return { error: '请填写对象存储 Secret Key' }
        }
    }

    return {
        payload: {
            type: form.type,
            name,
            parentPath,
            readonly: form.readonly,
            host,
            bucket,
            accessKey,
            ...(secretKey ? { secretKey } : {}),
        },
    }
}

export const useStorageSettings = () => {
    const storageNodes = ref<StorageNode[]>([])
    const systemConfig = ref<SystemConfig>({
        ossProviderId: null,
        fsProviderId: null,
    })

    const isSaving = ref(false)
    const isLoadingSystem = ref(false)
    const isLoadingStorage = ref(false)

    const systemError = ref('')
    const storageError = ref('')

    const activeStorageLabel = computed(() => {
        const activeId = systemConfig.value.fsProviderId ?? systemConfig.value.ossProviderId
        if (activeId === null) {
            return '未选择'
        }
        const activeType: FileProviderType =
            systemConfig.value.fsProviderId === null ? 'OSS' : 'FILE_SYSTEM'
        const node = storageNodes.value.find(
            (item) => item.id === activeId && item.type === activeType,
        )
        return node ? node.name : `ID ${activeId}`
    })

    const fetchSystemConfig = async () => {
        isLoadingSystem.value = true
        systemError.value = ''
        try {
            const config = await api.systemConfigController.get()
            systemConfig.value.fsProviderId = config.fsProviderId ?? null
            systemConfig.value.ossProviderId = config.ossProviderId ?? null
        } catch (error) {
            const normalized = normalizeApiError(error)
            systemConfig.value.fsProviderId = null
            systemConfig.value.ossProviderId = null
            systemError.value = normalized.message ?? '系统配置加载失败'
        } finally {
            isLoadingSystem.value = false
        }
    }

    const fetchStorageNodes = async () => {
        isLoadingStorage.value = true
        storageError.value = ''
        try {
            const [fsList, ossList] = await Promise.all([
                api.fileSystemStorageController.list(),
                api.ossStorageController.list(),
            ])
            storageNodes.value = [
                ...fsList.map((item) => ({
                    id: item.id,
                    type: 'FILE_SYSTEM' as const,
                    name: item.name,
                    parentPath: item.parentPath,
                    readonly: item.readonly,
                })),
                ...ossList.map((item) => ({
                    id: item.id,
                    type: 'OSS' as const,
                    name: item.name,
                    parentPath: item.parentPath ?? '',
                    readonly: item.readonly,
                    host: item.host,
                    bucket: item.bucket,
                    accessKey: item.accessKey,
                })),
            ]
        } catch (error) {
            const normalized = normalizeApiError(error)
            storageError.value = normalized.message ?? '存储节点加载失败'
        } finally {
            isLoadingStorage.value = false
        }
    }

    const loadData = async () => {
        await Promise.all([fetchSystemConfig(), fetchStorageNodes()])
    }

    const createStorageNode = async (form: StorageNodeForm) => {
        const validated = validateStorageNodeForm(form, { mode: 'create' })
        if ('error' in validated) {
            return validated.error
        }
        if (isSaving.value) {
            return '已有保存操作正在执行'
        }

        isSaving.value = true
        storageError.value = ''

        try {
            await (validated.payload.type === 'FILE_SYSTEM'
                ? api.fileSystemStorageController.create({
                      body: {
                          name: validated.payload.name,
                          parentPath: validated.payload.parentPath,
                          readonly: validated.payload.readonly,
                      },
                  })
                : api.ossStorageController.create({
                      body: {
                          name: validated.payload.name,
                          host: validated.payload.host ?? '',
                          bucket: validated.payload.bucket ?? '',
                          accessKey: validated.payload.accessKey ?? '',
                          secretKey: validated.payload.secretKey ?? '',
                          parentPath: validated.payload.parentPath || undefined,
                          readonly: validated.payload.readonly,
                      },
                  }))
            await fetchStorageNodes()
            return null
        } catch (error) {
            const normalized = normalizeApiError(error)
            return normalized.message ?? '创建失败'
        } finally {
            isSaving.value = false
        }
    }

    const updateStorageNode = async (id: number, form: StorageNodeForm) => {
        const validated = validateStorageNodeForm(form, { mode: 'update' })
        if ('error' in validated) {
            return validated.error
        }
        if (isSaving.value) {
            return '已有保存操作正在执行'
        }

        isSaving.value = true
        storageError.value = ''

        try {
            await (validated.payload.type === 'FILE_SYSTEM'
                ? api.fileSystemStorageController.update({
                      id,
                      body: {
                          name: validated.payload.name,
                          parentPath: validated.payload.parentPath,
                          readonly: validated.payload.readonly,
                      },
                  })
                : api.ossStorageController.update({
                      id,
                      body: {
                          name: validated.payload.name,
                          host: validated.payload.host,
                          bucket: validated.payload.bucket,
                          accessKey: validated.payload.accessKey,
                          secretKey: validated.payload.secretKey,
                          parentPath: validated.payload.parentPath || undefined,
                          readonly: validated.payload.readonly,
                      },
                  }))
            await fetchStorageNodes()
            return null
        } catch (error) {
            const normalized = normalizeApiError(error)
            return normalized.message ?? '更新失败'
        } finally {
            isSaving.value = false
        }
    }

    const deleteStorageNode = async (node: StorageNode) => {
        if (isSaving.value) {
            return '已有保存操作正在执行'
        }

        isSaving.value = true
        storageError.value = ''

        try {
            await (node.type === 'FILE_SYSTEM'
                ? api.fileSystemStorageController.delete({ id: node.id })
                : api.ossStorageController.delete({ id: node.id }))
            await Promise.all([fetchStorageNodes(), fetchSystemConfig()])
            return null
        } catch (error) {
            const normalized = normalizeApiError(error)
            storageError.value = normalized.message ?? '删除失败'
            return storageError.value
        } finally {
            isSaving.value = false
        }
    }

    const setSystemStorageNode = async (node: StorageNode) => {
        if (isSaving.value) {
            return '已有保存操作正在执行'
        }
        if (node.readonly) {
            return '只读节点不能设置为系统节点'
        }

        isSaving.value = true
        systemError.value = ''

        try {
            const body: SystemConfigProviderUpdate =
                node.type === 'FILE_SYSTEM'
                    ? { fsProviderId: node.id, ossProviderId: null }
                    : { fsProviderId: null, ossProviderId: node.id }
            const config = await api.systemConfigController.update({
                // oxlint-disable-next-line typescript-eslint/no-unsafe-type-assertion
                body: body as unknown as SystemConfigUpdate,
            })
            systemConfig.value.fsProviderId = config.fsProviderId ?? null
            systemConfig.value.ossProviderId = config.ossProviderId ?? null
            return null
        } catch (error) {
            const normalized = normalizeApiError(error)
            systemError.value = normalized.message ?? '设置系统节点失败'
            return systemError.value
        } finally {
            isSaving.value = false
        }
    }

    return {
        storageNodes,
        systemConfig,
        activeStorageLabel,
        isSaving,
        isLoadingSystem,
        isLoadingStorage,
        systemError,
        storageError,
        loadData,
        createStorageNode,
        updateStorageNode,
        deleteStorageNode,
        setSystemStorageNode,
    }
}
