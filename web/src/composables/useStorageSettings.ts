import { computed, reactive, ref } from 'vue'
import { api, normalizeApiError } from '@/ApiInstance'

export type StorageNode = {
    id: number
    name: string
    parentPath: string
    readonly: boolean
}

export type SystemConfig = {
    ossProviderId: number | null
    fsProviderId: number | null
}

export type StorageNodeForm = {
    name: string
    parentPath: string
    readonly: boolean
}

export const useStorageSettings = () => {
    const storageNodes = ref<StorageNode[]>([])
    const systemConfig = ref<SystemConfig>({
        ossProviderId: null,
        fsProviderId: null,
    })

    const isEditing = ref<number | null>(null)
    const isCreating = ref(false)
    const isDeleting = ref<number | null>(null)
    const isSaving = ref(false)
    const isLoadingSystem = ref(false)
    const isLoadingStorage = ref(false)

    const systemError = ref('')
    const storageError = ref('')

    const editForm = reactive<StorageNodeForm>({
        name: '',
        parentPath: '',
        readonly: true,
    })

    const activeFsLabel = computed(() => {
        const activeId = systemConfig.value.fsProviderId
        if (activeId === null) {
            return '未选择'
        }
        const node = storageNodes.value.find((item) => item.id === activeId)
        return node ? node.name : `ID ${activeId}`
    })

    const resetForm = () => {
        editForm.name = ''
        editForm.parentPath = ''
        editForm.readonly = true
    }

    const updateEditForm = (patch: Partial<StorageNodeForm>) => {
        Object.assign(editForm, patch)
    }

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
            const list = await api.fileSystemStorageController.list()
            storageNodes.value = list.map((item) => ({
                id: item.id,
                name: item.name,
                parentPath: item.parentPath,
                readonly: item.readonly,
            }))
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

    const startDelete = (id: number) => {
        if (isSaving.value) return
        isDeleting.value = id
    }

    const cancelDelete = () => {
        isDeleting.value = null
    }

    const confirmDelete = async () => {
        if (isDeleting.value === null || isSaving.value) {
            return
        }
        isSaving.value = true
        storageError.value = ''
        try {
            await api.fileSystemStorageController.delete({ id: isDeleting.value })
            // 删除节点可能影响当前生效的配置，需要同步刷新
            await Promise.all([fetchStorageNodes(), fetchSystemConfig()])
            isDeleting.value = null
        } catch (error) {
            const normalized = normalizeApiError(error)
            storageError.value = normalized.message ?? '删除失败'
        } finally {
            isSaving.value = false
        }
    }

    const startEdit = (node: StorageNode) => {
        isCreating.value = false
        isEditing.value = node.id
        editForm.name = node.name
        editForm.parentPath = node.parentPath
        editForm.readonly = node.readonly
    }

    const cancelEdit = () => {
        isEditing.value = null
        isCreating.value = false
        resetForm()
    }

    const saveEdit = async () => {
        if (isEditing.value === null) {
            return
        }
        const name = editForm.name.trim()
        const parentPath = editForm.parentPath.trim()
        if (!name || !parentPath) {
            storageError.value = '请填写名称与路径'
            return
        }
        if (isSaving.value) {
            return
        }
        isSaving.value = true
        storageError.value = ''
        try {
            await api.fileSystemStorageController.update({
                id: isEditing.value,
                body: {
                    name,
                    parentPath,
                    readonly: editForm.readonly,
                },
            })
            await fetchStorageNodes()
            isEditing.value = null
            resetForm()
        } catch (error) {
            const normalized = normalizeApiError(error)
            storageError.value = normalized.message ?? '更新失败'
        } finally {
            isSaving.value = false
        }
    }

    const startCreate = () => {
        isEditing.value = null
        isCreating.value = true
        resetForm()
    }

    const saveCreate = async () => {
        const name = editForm.name.trim()
        const parentPath = editForm.parentPath.trim()
        if (!name || !parentPath) {
            storageError.value = '请填写名称与路径'
            return
        }
        if (isSaving.value) {
            return
        }
        isSaving.value = true
        storageError.value = ''
        try {
            await api.fileSystemStorageController.create({
                body: {
                    name,
                    parentPath,
                    readonly: editForm.readonly,
                },
            })
            await fetchStorageNodes()
            isCreating.value = false
            resetForm()
        } catch (error) {
            const normalized = normalizeApiError(error)
            storageError.value = normalized.message ?? '创建失败'
        } finally {
            isSaving.value = false
        }
    }

    return {
        storageNodes,
        systemConfig,
        editForm,
        activeFsLabel,
        isEditing,
        isCreating,
        isDeleting,
        isSaving,
        isLoadingSystem,
        isLoadingStorage,
        systemError,
        storageError,
        loadData,
        updateEditForm,
        startDelete,
        cancelDelete,
        confirmDelete,
        startEdit,
        cancelEdit,
        saveEdit,
        startCreate,
        saveCreate,
    }
}
