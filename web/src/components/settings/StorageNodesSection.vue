<script setup lang="ts">
import { Plus } from 'lucide-vue-next'
import StorageNodeCard from '@/components/settings/StorageNodeCard.vue'
import StorageNodeDeleteModal from '@/components/settings/StorageNodeDeleteModal.vue'
import StorageNodeEditCard from '@/components/settings/StorageNodeEditCard.vue'
import StorageNodeFormModal from '@/components/settings/StorageNodeFormModal.vue'
import type { StorageNode, StorageNodeForm, SystemConfig } from '@/composables/useStorageSettings'

type Props = {
    storageNodes: StorageNode[]
    systemConfig: SystemConfig
    isLoading: boolean
    error: string
    isCreating: boolean
    isEditing: number | null
    isDeleting: number | null
    isSaving: boolean
    editForm: StorageNodeForm
}

defineProps<Props>()
const emit = defineEmits<{
    (event: 'start-create'): void
    (event: 'cancel-edit'): void
    (event: 'save-create'): void
    (event: 'start-edit', node: StorageNode): void
    (event: 'save-edit'): void
    (event: 'start-delete', id: number): void
    (event: 'cancel-delete'): void
    (event: 'confirm-delete'): void
    (event: 'update-form', value: Partial<StorageNodeForm>): void
}>()

const handleUpdateName = (value: string) => {
    emit('update-form', { name: value })
}

const handleUpdateParentPath = (value: string) => {
    emit('update-form', { parentPath: value })
}

const handleUpdateReadonly = (value: boolean) => {
    emit('update-form', { readonly: value })
}
</script>

<template>
    <section class="animate-in fade-in duration-500 font-serif">
        <div class="flex items-center justify-between mb-6">
            <h2 class="text-3xl italic text-[#2A2A2A]">存储节点</h2>
            <button
                class="group flex items-center gap-2 px-6 py-2 bg-[#C67C4E] text-[#F7F5F0] hover:bg-[#A6633C] transition-all duration-300 shadow-md hover:shadow-lg disabled:opacity-50 disabled:cursor-not-allowed"
                :disabled="isCreating || isSaving"
                @click="emit('start-create')"
            >
                <Plus :size="16" />
                <span>新增节点</span>
            </button>
        </div>

        <div v-if="error" class="text-sm text-[#B95D5D] mb-4">
            {{ error }}
        </div>
        <div v-else-if="isLoading" class="text-sm text-[#8A8A8A] mb-4">加载中...</div>

        <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
            <!-- 新建节点弹窗 -->
            <StorageNodeFormModal
                :open="isCreating"
                :name="editForm.name"
                :parent-path="editForm.parentPath"
                :readonly="editForm.readonly"
                :is-saving="isSaving"
                @update:name="handleUpdateName"
                @update:parent-path="handleUpdateParentPath"
                @update:readonly="handleUpdateReadonly"
                @cancel="emit('cancel-edit')"
                @save="emit('save-create')"
            />

            <!-- 删除确认弹窗 -->
            <StorageNodeDeleteModal
                :open="isDeleting !== null"
                :is-saving="isSaving"
                @cancel="emit('cancel-delete')"
                @confirm="emit('confirm-delete')"
            />

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
                    @delete="emit('start-delete', node.id)"
                />
            </div>
        </div>
    </section>
</template>
