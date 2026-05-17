<script setup lang="ts">
import { ref } from 'vue'
import { CheckCircle2, Download, Loader2, Puzzle, Trash2, Upload, XCircle } from 'lucide-vue-next'
import type { PluginInfoResponse } from '@/__generated/model/static/PluginInfoResponse'

const props = defineProps<{
    plugins: ReadonlyArray<PluginInfoResponse>
    isLoading: boolean
    isUploading: boolean
    error: string
    onUpload: (file: File) => Promise<void>
    onSetEnabled: (id: string, enabled: boolean) => Promise<void>
    onDelete: (id: string) => Promise<void>
    onDownload: (plugin: PluginInfoResponse) => Promise<void>
}>()

const fileInputRef = ref<HTMLInputElement | null>(null)
const togglingId = ref<string | null>(null)
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

const handleSetEnabled = async (id: string, enabled: boolean) => {
    togglingId.value = id
    try {
        await props.onSetEnabled(id, enabled)
    } finally {
        togglingId.value = null
    }
}

const handleDelete = async (id: string) => {
    deletingId.value = id
    try {
        await props.onDelete(id)
    } finally {
        deletingId.value = null
    }
}

const handleDownload = async (plugin: PluginInfoResponse) => {
    downloadingId.value = plugin.id
    try {
        await props.onDownload(plugin)
    } finally {
        downloadingId.value = null
    }
}
</script>

<template>
    <section class="mb-16 animate-in fade-in duration-500">
        <h2 class="mb-2 font-serif text-2xl text-[#2C2A28]">插件</h2>
        <div class="mb-6 h-px w-full bg-[#E8E4D9]"></div>

        <div class="mb-6 flex items-center gap-4">
            <input
                ref="fileInputRef"
                type="file"
                accept=".up"
                class="hidden"
                @change="handleFileChange"
            />
            <button
                type="button"
                class="flex items-center gap-2 border border-[#C27E46] px-4 py-2 text-sm text-[#C27E46] transition-colors hover:bg-[#C27E46] hover:text-white disabled:cursor-not-allowed disabled:opacity-50"
                :disabled="isUploading"
                @click="fileInputRef?.click()"
            >
                <Loader2 v-if="isUploading" class="h-4 w-4 animate-spin" />
                <Upload v-else class="h-4 w-4" />
                <span>{{ isUploading ? '上传中...' : '上传插件 (.up)' }}</span>
            </button>
        </div>

        <div
            v-if="error"
            class="mb-4 border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700"
        >
            {{ error }}
        </div>

        <div v-if="isLoading" class="flex items-center justify-center py-10 text-sm text-[#6B635B]">
            <Loader2 class="mr-2 h-4 w-4 animate-spin text-[#C27E46]" />
            正在加载插件列表...
        </div>

        <div
            v-else-if="plugins.length === 0"
            class="flex flex-col items-center justify-center border border-dashed border-[#D6D1C4] py-12 text-center"
        >
            <Puzzle class="h-10 w-10 text-[#C27E46]" />
            <p class="mt-4 text-sm text-[#6B635B]">暂无已安装的插件</p>
            <p class="mt-1 text-xs text-[#9C968B]">上传 .up 文件后即可在此管理</p>
        </div>

        <div v-else class="space-y-4">
            <div
                v-for="plugin in plugins"
                :key="`${plugin.id}-${plugin.taskType}`"
                class="border border-[#E8E4D9] bg-[#FDFAF5] p-5 shadow-sm"
            >
                <div class="flex items-start justify-between gap-4">
                    <div class="flex min-w-0 items-start gap-3">
                        <Puzzle class="mt-0.5 h-5 w-5 shrink-0 text-[#C27E46]" />
                        <div class="min-w-0">
                            <div class="flex flex-wrap items-center gap-2">
                                <span class="font-medium text-[#2C2A28]">
                                    {{ plugin.name ?? plugin.id }}
                                </span>
                                <span
                                    class="rounded bg-[#EBE6D9] px-1.5 py-0.5 font-mono text-[11px] text-[#8A8177]"
                                >
                                    v{{ plugin.version }}
                                </span>
                                <span
                                    class="rounded bg-[#F0EADE] px-1.5 py-0.5 font-mono text-[11px] text-[#9C968B]"
                                >
                                    {{ plugin.taskType }}
                                </span>
                            </div>
                            <p class="mt-1 font-mono text-xs text-[#9C968B]">{{ plugin.id }}</p>

                            <div class="mt-2 flex flex-wrap items-center gap-3 text-xs">
                                <div class="flex items-center gap-1">
                                    <CheckCircle2
                                        v-if="plugin.isAvailable"
                                        class="h-3.5 w-3.5 text-emerald-500"
                                    />
                                    <XCircle v-else class="h-3.5 w-3.5 text-[#C0BAB0]" />
                                    <span
                                        :class="
                                            plugin.isAvailable
                                                ? 'text-emerald-600'
                                                : 'text-[#9C968B]'
                                        "
                                    >
                                        {{ plugin.isAvailable ? '运行中' : '未加载' }}
                                    </span>
                                </div>
                            </div>
                        </div>
                    </div>

                    <div class="flex shrink-0 items-center gap-2">
                        <button
                            type="button"
                            class="flex items-center gap-1.5 px-3 py-1.5 text-xs transition-colors disabled:cursor-not-allowed disabled:opacity-50"
                            :class="
                                plugin.enabled
                                    ? 'border border-[#E8E4D9] text-[#6B635B] hover:border-rose-300 hover:text-rose-500'
                                    : 'border border-[#C27E46] text-[#C27E46] hover:bg-[#C27E46] hover:text-white'
                            "
                            :disabled="togglingId === plugin.id"
                            @click="handleSetEnabled(plugin.id, !plugin.enabled)"
                        >
                            <Loader2 v-if="togglingId === plugin.id" class="h-3 w-3 animate-spin" />
                            <span>{{ plugin.enabled ? '禁用' : '启用' }}</span>
                        </button>

                        <button
                            type="button"
                            class="p-1.5 text-[#9C968B] transition-colors hover:text-[#C27E46] disabled:opacity-50"
                            :disabled="downloadingId === plugin.id"
                            title="导出 .up 文件"
                            @click="handleDownload(plugin)"
                        >
                            <Loader2
                                v-if="downloadingId === plugin.id"
                                class="h-4 w-4 animate-spin"
                            />
                            <Download v-else class="h-4 w-4" />
                        </button>

                        <button
                            type="button"
                            class="p-1.5 text-[#9C968B] transition-colors hover:text-rose-500 disabled:opacity-50"
                            :disabled="deletingId === plugin.id"
                            title="删除插件"
                            @click="handleDelete(plugin.id)"
                        >
                            <Loader2 v-if="deletingId === plugin.id" class="h-4 w-4 animate-spin" />
                            <Trash2 v-else class="h-4 w-4" />
                        </button>
                    </div>
                </div>

                <div
                    v-if="plugin.form.fields.length > 0"
                    class="mt-4 border-t border-[#EBE6D9] pt-4"
                >
                    <div class="mb-2 text-[11px] uppercase tracking-[0.24em] text-[#9C968B]">
                        表单参数
                    </div>
                    <div class="grid gap-2 sm:grid-cols-2">
                        <div
                            v-for="field in plugin.form.fields"
                            :key="field.name"
                            class="flex items-baseline gap-2 text-sm"
                        >
                            <span class="font-mono text-xs text-[#C27E46]">{{ field.type }}</span>
                            <span class="text-[#2C2A28]">{{ field.label }}</span>
                            <span v-if="field.default !== undefined" class="text-xs text-[#9C968B]">
                                默认 {{ field.default }}
                            </span>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </section>
</template>
