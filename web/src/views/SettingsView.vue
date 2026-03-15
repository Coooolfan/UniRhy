<script setup lang="ts">
import { computed, onMounted } from 'vue'
import DashboardTopBar from '@/components/dashboard/DashboardTopBar.vue'
import StorageNodesSection from '@/components/settings/StorageNodesSection.vue'
import SystemStatusSection from '@/components/settings/SystemStatusSection.vue'
import { useStorageSettings } from '@/composables/useStorageSettings'

const {
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
} = useStorageSettings()

const activeNode = computed(
    () => storageNodes.value.find((node) => node.id === systemConfig.value.fsProviderId) ?? null,
)

onMounted(() => {
    loadData()
})
</script>

<template>
    <div class="pb-32 text-[#3D3D3D] font-sans selection:bg-[#C67C4E] selection:text-white">
        <DashboardTopBar />

        <div class="mx-auto max-w-5xl px-4 pt-4 sm:px-6 sm:pt-6 lg:px-8">
            <header class="mb-10 sm:mb-12">
                <h1 class="font-serif text-3xl text-[#2B221B] tracking-tight mb-2">系统设置</h1>
                <p class="text-[#8A8A8A] font-serif italic text-sm">
                    System Configuration & Storage Management
                </p>
            </header>
        </div>

        <div class="max-w-5xl mx-auto px-8 mt-10">
            <SystemStatusSection
                :active-fs-label="activeFsLabel"
                :system-config="systemConfig"
                :active-node="activeNode"
                :is-loading="isLoadingSystem"
                :error="systemError"
            />

            <StorageNodesSection
                :storage-nodes="storageNodes"
                :system-config="systemConfig"
                :is-loading="isLoadingStorage"
                :error="storageError"
                :is-creating="isCreating"
                :is-editing="isEditing"
                :is-deleting="isDeleting"
                :is-saving="isSaving"
                :edit-form="editForm"
                @start-create="startCreate"
                @cancel-edit="cancelEdit"
                @save-create="saveCreate"
                @start-edit="startEdit"
                @save-edit="saveEdit"
                @start-delete="startDelete"
                @cancel-delete="cancelDelete"
                @confirm-delete="confirmDelete"
                @update-form="updateEditForm"
            />
        </div>
    </div>
</template>
