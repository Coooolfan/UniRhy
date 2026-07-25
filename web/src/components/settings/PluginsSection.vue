<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Download, Loader2, Puzzle, Settings2, Trash2, Upload, XCircle } from 'lucide-vue-next'
import type { PluginInfoResponse } from '@/__generated/model/static/PluginInfoResponse'
import type { PluginConfigurationResponse } from '@/__generated/model/static/PluginConfigurationResponse'
import PluginDetailsDialogContent from '@/components/settings/PluginDetailsDialogContent.vue'
import { parseFormDefinition } from '@/components/tasks/schemaForm'
import { useModal } from '@/composables/useModal'

const props = defineProps<{
    plugins: ReadonlyArray<PluginInfoResponse>
    isLoading: boolean
    isUploading: boolean
    error: string
    onUpload: (file: File) => Promise<void>
    onSetEnabled: (id: string, enabled: boolean) => Promise<void>
    onUpdateConcurrency: (id: string, taskType: string, concurrency: number) => Promise<void>
    onLoadConfiguration: (id: string) => Promise<PluginConfigurationResponse>
    onSaveConfiguration: (
        id: string,
        values: Record<string, unknown>,
        clearedSecretFields: ReadonlyArray<string>,
    ) => Promise<PluginConfigurationResponse>
    onDelete: (id: string) => Promise<void>
    onDownload: (plugin: PluginInfoResponse) => Promise<void>
    canManage?: boolean
}>()

const { t } = useI18n()
const modal = useModal()

const fileInputRef = ref<HTMLInputElement | null>(null)
const deletingId = ref<string | null>(null)
const downloadingId = ref<string | null>(null)

const handleFileChange = async (event: Event) => {
    const file = (event.target as HTMLInputElement).files?.[0]
    if (!file) return
    try {
        await props.onUpload(file)
    } finally {
        if (fileInputRef.value) fileInputRef.value.value = ''
    }
}

const openPluginDetails = (plugin: PluginInfoResponse) => {
    const hasConfiguration = parseFormDefinition(plugin.configDefinition).length > 0
    void modal.open(PluginDetailsDialogContent, {
        title: plugin.name ?? plugin.id,
        size: hasConfiguration ? 'xl' : 'lg',
        fitContent: false,
        props: {
            plugin,
            canManage: props.canManage ?? false,
            setEnabled: props.onSetEnabled,
            updateConcurrency: props.onUpdateConcurrency,
            loadConfiguration: props.onLoadConfiguration,
            saveConfiguration: props.onSaveConfiguration,
        },
    })
}

const handleDelete = async (plugin: PluginInfoResponse) => {
    const confirmed = await modal.confirm({
        title: t('plugins.deleteTitle', { name: plugin.name ?? plugin.id }),
        content: t('plugins.deleteConfirm'),
        confirmText: t('plugins.confirmDelete'),
        cancelText: t('common.cancel'),
        tone: 'danger',
    })
    if (!confirmed) return

    deletingId.value = plugin.id
    try {
        await props.onDelete(plugin.id)
    } finally {
        deletingId.value = null
    }
}

const handleDownload = async (plugin: PluginInfoResponse) => {
    if (!props.canManage) return
    downloadingId.value = plugin.id
    try {
        await props.onDownload(plugin)
    } finally {
        downloadingId.value = null
    }
}
</script>

<template>
    <section class="mb-16 animate-in fade-in duration-500 font-serif">
        <div
            class="mb-4 flex items-center justify-between gap-3 border-b border-[#E8E4D9] pb-2 sm:mb-6"
        >
            <h2 class="font-serif text-2xl text-[#2C2A28]">{{ t('plugins.title') }}</h2>
            <input
                v-if="canManage"
                ref="fileInputRef"
                type="file"
                accept=".up"
                class="hidden"
                @change="handleFileChange"
            />
            <button
                v-if="canManage"
                type="button"
                class="group flex w-auto shrink-0 items-center justify-center gap-2 bg-[#C67C4E] px-3 py-2 text-sm text-[#F7F5F0] shadow-md transition-all duration-300 hover:bg-[#A6633C] hover:shadow-lg disabled:cursor-not-allowed disabled:opacity-50 sm:px-6 sm:text-base"
                :disabled="isUploading"
                @click="fileInputRef?.click()"
            >
                <Loader2 v-if="isUploading" class="h-4 w-4 animate-spin" />
                <Upload v-else class="h-4 w-4" />
                <span>{{ isUploading ? t('plugins.uploading') : t('plugins.upload') }}</span>
            </button>
        </div>

        <div
            v-if="error"
            class="mb-4 border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700"
        >
            {{ error }}
        </div>

        <p class="mb-4 text-xs italic text-[#8A8A8A]">
            {{ t('plugins.description') }}
        </p>

        <div v-if="isLoading" class="flex items-center justify-center py-10 text-sm text-[#6B635B]">
            <Loader2 class="mr-2 h-4 w-4 animate-spin text-[#C27E46]" />
            {{ t('plugins.loading') }}
        </div>

        <div
            v-else-if="plugins.length === 0"
            class="flex flex-col items-center justify-center border border-dashed border-[#D6D1C4] py-12 text-center"
        >
            <Puzzle class="h-10 w-10 text-[#C27E46]" />
            <p class="mt-4 text-sm text-[#6B635B]">{{ t('plugins.empty') }}</p>
            <p v-if="canManage" class="mt-1 text-xs text-[#9C968B]">{{ t('plugins.emptyHint') }}</p>
        </div>

        <div v-else class="space-y-4">
            <div
                v-for="plugin in plugins"
                :key="plugin.id"
                class="border border-[#E8E4D9] bg-[#FDFAF5] p-5 shadow-sm"
            >
                <div class="flex items-start justify-between gap-4">
                    <div class="flex min-w-0 items-start gap-3">
                        <Puzzle class="mt-0.5 h-5 w-5 shrink-0 text-[#C27E46]" />
                        <div class="min-w-0">
                            <div class="flex flex-wrap items-baseline gap-x-2 gap-y-1">
                                <span class="font-medium text-[#2C2A28]">
                                    {{ plugin.name ?? plugin.id }}
                                </span>
                                <span class="font-mono text-[11px] text-[#9C968B]">
                                    v{{ plugin.version }}
                                </span>
                            </div>
                            <div
                                class="mt-1 flex min-w-0 flex-wrap items-center gap-x-2 gap-y-1 font-mono text-[11px] text-[#9C968B]"
                            >
                                <span class="break-all">{{ plugin.id }}</span>
                                <template v-for="task in plugin.tasks" :key="task.taskType">
                                    <span aria-hidden="true" class="text-[#D6D1C4]">/</span>
                                    <span class="text-[#8A8177]">
                                        {{ task.taskType }}
                                    </span>
                                </template>
                            </div>

                            <div class="mt-2 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs">
                                <span :class="plugin.enabled ? 'text-[#5F7350]' : 'text-[#9C968B]'">
                                    {{
                                        plugin.enabled
                                            ? t('plugins.enabled')
                                            : t('plugins.disabled')
                                    }}
                                </span>
                                <span
                                    v-if="plugin.enabled && !plugin.isAvailable"
                                    class="flex items-center gap-1 text-[#B95D5D]"
                                >
                                    <XCircle class="h-3.5 w-3.5" />
                                    {{ t('plugins.notLoaded') }}
                                </span>
                            </div>
                        </div>
                    </div>

                    <div class="flex shrink-0 items-center gap-2">
                        <button
                            type="button"
                            class="p-1.5 text-[#9C968B] transition-colors hover:text-[#C27E46]"
                            :title="t('plugins.details')"
                            @click="openPluginDetails(plugin)"
                        >
                            <Settings2 class="h-4 w-4" />
                        </button>

                        <button
                            v-if="canManage"
                            type="button"
                            class="p-1.5 text-[#9C968B] transition-colors hover:text-[#C27E46] disabled:opacity-50"
                            :disabled="downloadingId === plugin.id"
                            :title="t('plugins.exportFile')"
                            @click="handleDownload(plugin)"
                        >
                            <Loader2
                                v-if="downloadingId === plugin.id"
                                class="h-4 w-4 animate-spin"
                            />
                            <Download v-else class="h-4 w-4" />
                        </button>

                        <button
                            v-if="canManage"
                            type="button"
                            class="p-1.5 text-[#9C968B] transition-colors hover:text-rose-500 disabled:opacity-50"
                            :disabled="deletingId === plugin.id"
                            :title="t('plugins.deletePlugin')"
                            @click="handleDelete(plugin)"
                        >
                            <Loader2 v-if="deletingId === plugin.id" class="h-4 w-4 animate-spin" />
                            <Trash2 v-else class="h-4 w-4" />
                        </button>
                    </div>
                </div>
            </div>
        </div>
    </section>
</template>
