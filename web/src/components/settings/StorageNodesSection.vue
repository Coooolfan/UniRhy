<script setup lang="ts">
import { Plus } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
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
    deleteStorageNode: (node: StorageNode) => Promise<string | null>
    setSystemStorageNode: (node: StorageNode) => Promise<string | null>
    canManage?: boolean
}

const props = defineProps<Props>()
const { t } = useI18n()
const modal = useModal()

const openCreateStorageNodeModal = async () => {
    if (props.isSaving) {
        return
    }

    await modal.open(StorageNodeFormDialogContent, {
        title: t('storageNode.addNodeTitle'),
        size: 'md',
        closable: false,
        closeOnBackdrop: false,
        closeOnEscape: false,
        props: {
            initialType: 'FILE_SYSTEM',
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
        title: t('storageNode.editNodeTitle'),
        size: 'md',
        closable: false,
        closeOnBackdrop: false,
        closeOnEscape: false,
        props: {
            initialType: node.type,
            initialName: node.name,
            initialParentPath: node.parentPath,
            initialReadonly: node.readonly,
            initialHost: node.host,
            initialBucket: node.bucket,
            initialAccessKey: node.accessKey,
            submit: (form: StorageNodeForm) => props.updateStorageNode(node.id, form),
        },
    })
}

const confirmDeleteStorageNode = async (node: StorageNode) => {
    if (props.isSaving) {
        return
    }

    const confirmed = await modal.confirm({
        title: t('storageNode.removeTitle'),
        content: t('storageNode.removeConfirm'),
        confirmText: t('storageNode.confirmRemove'),
        cancelText: t('common.cancel'),
        tone: 'danger',
    })

    if (!confirmed) {
        return
    }

    const error = await props.deleteStorageNode(node)
    if (!error) {
        return
    }

    await modal.alert({
        title: t('storageNode.removeFailed'),
        content: error,
        confirmText: t('common.confirm'),
        tone: 'danger',
    })
}

const confirmSetSystemStorageNode = async (node: StorageNode) => {
    if (props.isSaving) {
        return
    }

    const confirmed = await modal.confirm({
        title: t('storageNode.setSystemTitle'),
        content: t('storageNode.setSystemConfirm', { name: node.name }),
        confirmText: t('storageNode.setSystemConfirmText'),
        cancelText: t('common.cancel'),
        tone: 'default',
    })

    if (!confirmed) {
        return
    }

    const error = await props.setSystemStorageNode(node)
    if (!error) {
        return
    }

    await modal.alert({
        title: t('storageNode.setSystemFailed'),
        content: error,
        confirmText: t('common.confirm'),
        tone: 'danger',
    })
}
</script>

<template>
    <section class="animate-in fade-in duration-500 font-serif">
        <div
            class="mb-4 flex items-center justify-between gap-3 border-b border-[#E0Dcd0] pb-2 sm:mb-6"
        >
            <h2 class="text-2xl font-serif text-[#4A3B32] tracking-wide">
                {{ t('storageNode.title') }}
            </h2>
            <button
                v-if="canManage"
                class="group flex w-auto shrink-0 items-center justify-center gap-2 bg-[#C67C4E] px-3 py-2 text-sm text-[#F7F5F0] shadow-md transition-all duration-300 hover:bg-[#A6633C] hover:shadow-lg disabled:cursor-not-allowed disabled:opacity-50 sm:px-6 sm:text-base"
                :disabled="isSaving"
                @click="openCreateStorageNodeModal"
            >
                <Plus :size="16" />
                <span>{{ t('storageNode.addNode') }}</span>
            </button>
        </div>

        <div v-if="isLoading" class="text-sm text-[#8A8A8A] mb-4">{{ t('common.loading') }}</div>

        <div class="grid grid-cols-1 md:grid-cols-2 gap-6 items-start">
            <!-- 节点列表 -->
            <div v-for="node in storageNodes" :key="`${node.type}:${node.id}`">
                <StorageNodeCard
                    :node="node"
                    :active-fs-id="systemConfig.fsProviderId"
                    :active-oss-id="systemConfig.ossProviderId"
                    :is-saving="isSaving"
                    :can-manage="canManage"
                    @edit="openEditStorageNodeModal(node)"
                    @delete="confirmDeleteStorageNode(node)"
                    @set-system="confirmSetSystemStorageNode(node)"
                />
            </div>
        </div>
    </section>
</template>
