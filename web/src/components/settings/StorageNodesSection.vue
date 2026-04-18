<script setup lang="ts">
import { Plus } from 'lucide-vue-next'
import { useModal } from '@/composables/useModal'
import StorageNodeFormDialogContent from '@/components/settings/StorageNodeFormDialogContent.vue'
import StorageNodeCard from '@/components/settings/StorageNodeCard.vue'
import StorageNodeEditCard from '@/components/settings/StorageNodeEditCard.vue'
import type { StorageNode, StorageNodeForm, SystemConfig } from '@/composables/useStorageSettings'

type Props = {
    storageNodes: StorageNode[]
    systemConfig: SystemConfig
    isLoading: boolean
    error: string
    isEditing: number | null
    isSaving: boolean
    editForm: StorageNodeForm
    createStorageNode: (value: StorageNodeForm) => Promise<string | null>
    deleteStorageNode: (id: number) => Promise<string | null>
}

const props = defineProps<Props>()
const emit = defineEmits<{
    (event: 'cancel-edit'): void
    (event: 'start-edit', node: StorageNode): void
    (event: 'save-edit'): void
    (event: 'update-form', value: Partial<StorageNodeForm>): void
}>()

const modal = useModal()

const handleUpdateName = (value: string) => {
    emit('update-form', { name: value })
}

const handleUpdateParentPath = (value: string) => {
    emit('update-form', { parentPath: value })
}

const handleUpdateReadonly = (value: boolean) => {
    emit('update-form', { readonly: value })
}

const openCreateStorageNodeModal = async () => {
    if (props.isSaving) {
        return
    }

    await modal.open(StorageNodeFormDialogContent, {
        title: '新增存储节点',
        size: 'md',
        closable: false,
        closeOnBackdrop: false,
        closeOnEscape: false,
        props: {
            initialReadonly: true,
            submit: props.createStorageNode,
        },
    })
}

const confirmDeleteStorageNode = async (nodeId: number) => {
    if (props.isSaving) {
        return
    }

    const confirmed = await modal.confirm({
        title: '移除节点',
        content: '确定要永久移除此存储节点配置吗？',
        confirmText: '确认移除',
        cancelText: '取消',
        tone: 'danger',
    })

    if (!confirmed) {
        return
    }

    const error = await props.deleteStorageNode(nodeId)
    if (!error) {
        return
    }

    await modal.alert({
        title: '移除失败',
        content: error,
        confirmText: '确认',
        tone: 'danger',
    })
}
</script>

<template>
    <section class="animate-in fade-in duration-500 font-serif">
        <div
            class="mb-6 flex flex-col gap-4 border-b border-[#E0Dcd0] pb-3 sm:flex-row sm:items-center sm:justify-between sm:pb-2"
        >
            <h2 class="text-2xl font-serif text-[#4A3B32] tracking-wide">存储节点</h2>
            <button
                class="group flex w-full items-center justify-center gap-2 bg-[#C67C4E] px-6 py-2 text-[#F7F5F0] transition-all duration-300 shadow-md hover:bg-[#A6633C] hover:shadow-lg disabled:cursor-not-allowed disabled:opacity-50 sm:w-auto sm:justify-start"
                :disabled="isSaving"
                @click="openCreateStorageNodeModal"
            >
                <Plus :size="16" />
                <span>新增节点</span>
            </button>
        </div>

        <div v-if="error" class="text-sm text-[#B95D5D] mb-4">
            {{ error }}
        </div>
        <div v-else-if="isLoading" class="text-sm text-[#8A8A8A] mb-4">加载中...</div>

        <div class="grid grid-cols-1 md:grid-cols-2 gap-6 items-start">
            <!-- 节点列表 -->
            <div v-for="node in storageNodes" :key="node.id">
                <StorageNodeEditCard
                    v-if="isEditing === node.id"
                    :name="editForm.name"
                    :parent-path="editForm.parentPath"
                    :readonly="editForm.readonly"
                    :is-saving="isSaving"
                    @update:name="handleUpdateName"
                    @update:parent-path="handleUpdateParentPath"
                    @update:readonly="handleUpdateReadonly"
                    @cancel="emit('cancel-edit')"
                    @save="emit('save-edit')"
                />
                <StorageNodeCard
                    v-else
                    :node="node"
                    :active-fs-id="systemConfig.fsProviderId"
                    :is-saving="isSaving"
                    @edit="emit('start-edit', node)"
                    @delete="confirmDeleteStorageNode(node.id)"
                />
            </div>
        </div>
    </section>
</template>
