<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import DashboardTopBar from '@/components/dashboard/DashboardTopBar.vue'
import StorageNodesSection from '@/components/settings/StorageNodesSection.vue'
import SystemStatusSection from '@/components/settings/SystemStatusSection.vue'
import PluginsSection from '@/components/settings/PluginsSection.vue'
import AccountsSection from '@/components/settings/AccountsSection.vue'
import { useStorageSettings } from '@/composables/useStorageSettings'
import { usePluginSettings } from '@/composables/usePluginSettings'
import { useAccountSettings } from '@/composables/useAccountSettings'
import { api } from '@/ApiInstance'
import type { SystemStatus } from '@/__generated/model/static'
import { useUserStore } from '@/stores/user'
import { getClientVersion } from '@/runtime/platform'

const userStore = useUserStore()

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
    accounts,
    isLoading: isLoadingAccounts,
    isSaving: isSavingAccount,
    error: accountError,
    fetchAccounts,
    createAccount,
    updateAccount,
    deleteAccount,
} = useAccountSettings()

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

const buildInfo = ref<SystemStatus | null>(null)
const clientVersion = ref<string | null>(null)

const formattedBuildTime = computed(() => {
    const t = buildInfo.value?.buildTime
    if (!t) return null
    const d = new Date(t)
    if (Number.isNaN(d.getTime())) return t
    return d.toLocaleString(undefined, { timeZoneName: 'short' })
})

const shortCommit = computed(() => buildInfo.value?.gitCommit?.slice(0, 7) ?? null)

const fetchAdminAccounts = async () => {
    const loadedUser = await userStore.ensureUserLoaded()
    if (loadedUser?.admin === true) {
        await fetchAccounts()
    }
}

onMounted(() => {
    void loadData()
    void fetchPlugins()
    void fetchAdminAccounts()
    void api.systemConfigController.isInitialized().then((status) => {
        buildInfo.value = status
    })
    void getClientVersion().then((version) => {
        clientVersion.value = version
    })
})
</script>

<template>
    <div class="pb-32 font-sans text-[#3D3D3D] selection:bg-[#C67C4E] selection:text-white">
        <DashboardTopBar />

        <div class="mx-auto max-w-5xl px-4 pt-4 sm:px-6 sm:pt-6 lg:px-8">
            <header class="mb-6 sm:mb-12">
                <h1 class="mb-2 font-serif text-3xl tracking-tight text-[#2B221B]">系统设置</h1>
                <p class="font-serif text-sm italic text-[#8A8A8A]">
                    管理实例级配置、存储节点与插件
                </p>
            </header>
        </div>

        <div class="mx-auto mt-6 max-w-5xl px-4 sm:mt-10 sm:px-8">
            <!-- <SystemStatusSection
                :active-storage-label="activeStorageLabel"
                :system-config="systemConfig"
                :active-node="activeNode"
                :is-loading="isLoadingSystem"
                :error="systemError"
            /> -->

            <StorageNodesSection
                class="mt-8 sm:mt-16"
                :storage-nodes="storageNodes"
                :system-config="systemConfig"
                :is-loading="isLoadingStorage"
                :error="storageError"
                :is-saving="isSaving"
                :create-storage-node="createStorageNode"
                :update-storage-node="updateStorageNode"
                :delete-storage-node="deleteStorageNode"
                :set-system-storage-node="setSystemStorageNode"
                :can-manage="userStore.isAdmin"
            />

            <AccountsSection
                v-if="userStore.isAdmin"
                class="mt-8 sm:mt-16"
                :accounts="accounts"
                :current-account-id="userStore.user?.id ?? null"
                :is-loading="isLoadingAccounts"
                :is-saving="isSavingAccount"
                :error="accountError"
                :create-account="createAccount"
                :update-account="updateAccount"
                :delete-account="deleteAccount"
                :can-manage="userStore.isAdmin"
            />

            <PluginsSection
                class="mt-8 sm:mt-16"
                :plugins="plugins"
                :is-loading="isLoadingPlugins"
                :is-uploading="isUploading"
                :error="pluginError"
                :on-upload="upload"
                :on-set-enabled="setEnabled"
                :on-delete="deletePlugin"
                :on-download="downloadPlugin"
                :can-manage="userStore.isAdmin"
            />

            <footer
                v-if="buildInfo || clientVersion"
                class="mt-16 border-t border-[#E5DED5] pt-6 font-serif text-xs text-[#8A8A8A]"
            >
                <div class="flex flex-wrap items-center gap-x-3 gap-y-1">
                    <span v-if="buildInfo?.version">服务端 v{{ buildInfo.version }}</span>
                    <span v-if="clientVersion">客户端 v{{ clientVersion }}</span>
                    <a
                        v-if="buildInfo?.gitUrl && shortCommit"
                        :href="buildInfo.gitUrl"
                        target="_blank"
                        rel="noopener noreferrer"
                        class="underline-offset-2 hover:text-[#C67C4E] hover:underline"
                    >
                        {{ buildInfo.gitBranch ?? 'unknown' }}@{{ shortCommit }}
                    </a>
                    <span v-else-if="shortCommit">
                        {{ buildInfo?.gitBranch ?? 'unknown' }}@{{ shortCommit }}
                    </span>
                    <span v-if="formattedBuildTime">构建于 {{ formattedBuildTime }}</span>
                </div>
            </footer>
        </div>
    </div>
</template>
