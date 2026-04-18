<script setup lang="ts">
import { Plus } from 'lucide-vue-next'
import { useModal } from '@/composables/useModal'
import StorageNodeEditDialogContent from '@/components/settings/StorageNodeEditDialogContent.vue'
import StorageNodeFormDialogContent from '@/components/settings/StorageNodeFormDialogContent.vue'
import StorageNodeCard from '@/components/settings/StorageNodeCard.vue'
import type { StorageNode, StorageNodeForm, SystemConfig } from '@/composables/useStorageSettings'

type Props = {
    storageNodes: StorageNode[]
    systemConfig: SystemConfig
    isLoading: boolean
    error: string
    isSaving: boolean
    createStorageNode: (value: StorageNodeForm) => Promise<string | null>
    updateStorageNode: (id: number, value: StorageNodeForm) => Promise<string | null>
    deleteStorageNode: (id: number) => Promise<string | null>
}

const props = defineProps<Props>()
const modal = useModal()

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

const openEditStorageNodeModal = async (node: StorageNode) => {
    if (props.isSaving) {
        return
    }

    await modal.open(StorageNodeEditDialogContent, {
        title: '编辑存储节点',
        size: 'md',
        closable: false,
        closeOnBackdrop: false,
        closeOnEscape: false,
        props: {
            initialName: node.name,
            initialParentPath: node.parentPath,
            initialReadonly: node.readonly,
            submit: (form: StorageNodeForm) => props.updateStorageNode(node.id, form),
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

        <div v-if="isLoading" class="text-sm text-[#8A8A8A] mb-4">加载中...</div>

        <div class="grid grid-cols-1 md:grid-cols-2 gap-6 items-start">
            <!-- 节点列表 -->
            <div v-for="node in storageNodes" :key="node.id">
                <StorageNodeCard
                    :node="node"
                    :active-fs-id="systemConfig.fsProviderId"
                    :is-saving="isSaving"
                    @edit="openEditStorageNodeModal(node)"
                    @delete="confirmDeleteStorageNode(node.id)"
                />
            </div>
        </div>
    </section>
</template>
