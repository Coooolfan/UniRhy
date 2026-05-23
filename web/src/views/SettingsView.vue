<script setup lang="ts">
import { computed, onMounted } from 'vue'
import DashboardTopBar from '@/components/dashboard/DashboardTopBar.vue'
import StorageNodesSection from '@/components/settings/StorageNodesSection.vue'
import SystemStatusSection from '@/components/settings/SystemStatusSection.vue'
import PluginsSection from '@/components/settings/PluginsSection.vue'
import { useStorageSettings } from '@/composables/useStorageSettings'
import { usePluginSettings } from '@/composables/usePluginSettings'

const {
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
} = useStorageSettings()

const {
    plugins,
    isLoading: isLoadingPlugins,
    isUploading,
    error: pluginError,
    fetch: fetchPlugins,
    upload,
    setEnabled,
    deletePlugin,
    downloadPlugin,
} = usePluginSettings()

const activeNode = computed(
    () =>
        storageNodes.value.find(
            (node) =>
                (node.type === 'FILE_SYSTEM' && node.id === systemConfig.value.fsProviderId) ||
                (node.type === 'OSS' && node.id === systemConfig.value.ossProviderId),
        ) ?? null,
)

onMounted(() => {
    loadData()
    void fetchPlugins()
})
</script>

<template>
    <div class="pb-32 font-sans text-[#3D3D3D] selection:bg-[#C67C4E] selection:text-white">
        <DashboardTopBar />

        <div class="mx-auto max-w-5xl px-4 pt-4 sm:px-6 sm:pt-6 lg:px-8">
            <header class="mb-10 sm:mb-12">
                <h1 class="mb-2 font-serif text-3xl tracking-tight text-[#2B221B]">系统设置</h1>
                <p class="font-serif text-sm italic text-[#8A8A8A]">
                    管理实例级配置、存储节点与插件
                </p>
            </header>
        </div>

        <div class="mx-auto mt-10 max-w-5xl px-8">
            <SystemStatusSection
                :active-storage-label="activeStorageLabel"
                :system-config="systemConfig"
                :active-node="activeNode"
                :is-loading="isLoadingSystem"
                :error="systemError"
            />

            <StorageNodesSection
                class="mt-16"
                :storage-nodes="storageNodes"
                :system-config="systemConfig"
                :is-loading="isLoadingStorage"
                :error="storageError"
                :is-saving="isSaving"
                :create-storage-node="createStorageNode"
                :update-storage-node="updateStorageNode"
                :delete-storage-node="deleteStorageNode"
                :set-system-storage-node="setSystemStorageNode"
            />

            <PluginsSection
                class="mt-16"
                :plugins="plugins"
                :is-loading="isLoadingPlugins"
                :is-uploading="isUploading"
                :error="pluginError"
                :on-upload="upload"
                :on-set-enabled="setEnabled"
                :on-delete="deletePlugin"
                :on-download="downloadPlugin"
            />
        </div>
    </div>
</template>
