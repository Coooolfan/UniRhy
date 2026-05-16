<script setup lang="ts">
import { reactive, ref } from 'vue'
import { Disc, FileAudio, Image as ImageIcon, Pencil, Plus, Trash2 } from 'lucide-vue-next'
import { normalizeApiError } from '@/ApiInstance'
import { useModalContext } from '@/components/modals/modalContext'
import { normalizeLabels } from '@/composables/recordingMedia'

export type RecordingAsset = {
    mediaFile: {
        id: number
        mimeType: string
        objectKey: string
        ossProvider?: { id: number }
        fsProvider?: { id: number }
    }
}

export type RecordingPreview = {
    id: number
    cover: string
    rawArtists: readonly { id: number; displayName?: string; name?: string }[]
    assets: readonly RecordingAsset[]
}

export type RecordingEditForm = {
    title: string
    label: string[]
    comment: string
    isDefault: boolean
}

const props = withDefaults(
    defineProps<{
        recording: RecordingPreview | null
        initialForm: RecordingEditForm
        onSubmit: (value: RecordingEditForm) => Promise<void> | void
        showDefaultToggle?: boolean
    }>(),
    {
        showDefaultToggle: false,
    },
)

const modal = useModalContext<undefined>()

const form = reactive<RecordingEditForm>({
    title: props.initialForm.title,
    label: [...props.initialForm.label],
    comment: props.initialForm.comment,
    isDefault: props.initialForm.isDefault,
})
const error = ref('')
const isSaving = ref(false)
const editingLabelIndex = ref<number | null>(null)

const closeModal = () => {
    if (isSaving.value) {
        return
    }

    modal.close()
}

const addLabel = () => {
    form.label.push('')
    const nextIndex = form.label.length - 1
    editingLabelIndex.value = nextIndex
}

const removeLabel = (index: number) => {
    form.label.splice(index, 1)
    editingLabelIndex.value = null
}

const editLabel = (index: number) => {
    if (isSaving.value) {
        return
    }

    editingLabelIndex.value = index
}

const stopEditingLabel = () => {
    editingLabelIndex.value = null
}

const submit = async () => {
    if (isSaving.value) {
        return
    }

    if (!form.title.trim()) {
        error.value = '标题不能为空'
        return
    }

    isSaving.value = true
    error.value = ''

    try {
        const labels = normalizeLabels(form.label)
        await props.onSubmit({
            title: form.title.trim(),
            label: labels,
            comment: form.comment.trim(),
            isDefault: form.isDefault,
        })
        modal.resolve(undefined)
    } catch (submitError) {
        error.value = normalizeApiError(submitError).message ?? '更新曲目失败'
    } finally {
        isSaving.value = false
    }
}
</script>

<template>
    <div class="grid grid-cols-1 gap-8 md:grid-cols-[minmax(0,0.9fr)_minmax(0,1.1fr)]">
        <div class="space-y-6">
            <div
                v-if="recording"
                class="flex h-full flex-col rounded-sm border border-[#D6D1C4] bg-[#F7F5F0] p-5"
            >
                <div class="flex gap-5">
                    <div
                        class="h-24 w-24 shrink-0 overflow-hidden rounded-sm bg-[#EAE6DE] shadow-sm"
                    >
                        <img
                            v-if="recording.cover"
                            :src="recording.cover"
                            class="h-full w-full object-cover"
                        />
                        <div
                            v-else
                            class="flex h-full w-full items-center justify-center text-[#8C857B]"
                        >
                            <ImageIcon :size="24" />
                        </div>
                    </div>
                    <div class="min-w-0 flex-1 space-y-3 py-1">
                        <div>
                            <div class="mb-1 text-xs uppercase tracking-wider text-[#8C857B]">
                                曲目 ID
                            </div>
                            <div class="font-mono text-sm text-[#5E564D]">#{{ recording.id }}</div>
                        </div>
                        <div>
                            <div class="mb-1 text-xs uppercase tracking-wider text-[#8C857B]">
                                资产
                            </div>
                            <div class="truncate text-[#2C2420]">
                                已关联 {{ recording.assets.length }} 个文件
                            </div>
                        </div>
                    </div>
                </div>
                <div>
                    <div class="mt-2 text-xs uppercase tracking-wider text-[#8C857B]">艺术家</div>
                    <div class="font-medium leading-snug text-[#2C2420]">
                        {{
                            recording.rawArtists
                                .map((artist) => artist.displayName || artist.name)
                                .join(', ') || '未知艺术家'
                        }}
                    </div>
                </div>
                <div
                    v-if="recording.assets.length > 0"
                    class="flex-1 border-t border-[#D6D1C4] pt-4 mt-4"
                >
                    <div class="mb-3 text-xs uppercase tracking-wider text-[#8C857B]">资产文件</div>
                    <div class="flex flex-col gap-2">
                        <div
                            v-for="asset in recording.assets"
                            :key="asset.mediaFile.id"
                            class="flex w-full items-start gap-2 rounded-sm border border-[#D6D1C4]/50 bg-[#EAE6DE]/50 px-2 py-2 text-xs text-[#5E564D] transition-colors hover:bg-[#EAE6DE]"
                        >
                            <FileAudio :size="14" class="mt-0.5 shrink-0 text-[#C17D46]" />
                            <div class="min-w-0 flex-1">
                                <div
                                    class="break-all font-medium text-[#2C2420]"
                                    :title="asset.mediaFile.objectKey"
                                >
                                    {{ asset.mediaFile.objectKey }}
                                </div>
                                <div
                                    class="mt-1 flex flex-wrap items-center gap-2 font-mono text-[10px] text-[#8C857B]"
                                >
                                    <span class="rounded-xs bg-[#D6D1C4]/30 px-1">
                                        {{ asset.mediaFile.mimeType }}
                                    </span>
                                    <span class="h-2 w-px bg-[#D6D1C4]"></span>
                                    <span>
                                        {{
                                            asset.mediaFile.ossProvider
                                                ? `OSS#${asset.mediaFile.ossProvider.id}`
                                                : asset.mediaFile.fsProvider
                                                  ? `FS#${asset.mediaFile.fsProvider.id}`
                                                  : 'UNKNOWN'
                                        }}
                                    </span>
                                    <span class="h-2 w-px bg-[#D6D1C4]"></span>
                                    <span>ID: {{ asset.mediaFile.id }}</span>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
                <div
                    v-else
                    class="flex flex-1 items-center justify-center text-xs italic text-[#8C857B] opacity-70"
                >
                    无附加音频资产
                </div>
            </div>
        </div>

        <div class="flex h-full flex-col space-y-5">
            <div class="flex items-center gap-3 text-[#2B221B]">
                <div
                    class="inline-flex h-10 w-10 items-center justify-center rounded-full border border-[#EAE6DE] bg-[#FAF9F6]"
                >
                    <Disc :size="20" class="text-[#C67C4E]" />
                </div>
                <!-- 向上对齐 -->
                <div class="flex items-start">
                    <div class="font-serif text-lg">关于曲目</div>
                </div>
            </div>

            <label class="block">
                <span class="mb-2 block font-serif text-xs uppercase tracking-wider text-[#8A8A8A]">
                    曲目名
                </span>
                <input
                    v-model="form.title"
                    type="text"
                    maxlength="255"
                    class="w-full border-b border-[#D6D1C4] bg-[#F7F5F0] p-3 font-serif text-[#3D3D3D] transition-colors placeholder:text-[#BDB9AE] focus:border-[#C67C4E] focus:outline-none"
                    placeholder="Track Title"
                    :disabled="isSaving"
                />
            </label>

            <div class="block">
                <div
                    class="mb-2 flex items-center justify-between gap-3 font-serif text-xs uppercase tracking-wider text-[#8A8A8A]"
                >
                    <span> 标签 </span>
                </div>
                <ul
                    class="label-strip flex h-14 min-w-0 items-start gap-2 overflow-x-auto overflow-y-hidden pb-3"
                >
                    <li
                        v-for="(_, index) in form.label"
                        :key="index"
                        class="group flex h-10 max-w-[300px] shrink-0 items-center whitespace-nowrap rounded-sm border border-[#D6D1C4] bg-[#F7F5F0] px-2"
                    >
                        <input
                            v-if="editingLabelIndex === index"
                            v-model="form.label[index]"
                            type="text"
                            maxlength="255"
                            class="h-8 min-w-0 flex-1 truncate border-b border-[#D6D1C4] bg-transparent px-1 font-serif text-sm text-[#3D3D3D] transition-colors placeholder:text-[#BDB9AE] focus:border-[#C67C4E] focus:outline-none"
                            placeholder="未命名标签"
                            :disabled="isSaving"
                            @blur="stopEditingLabel"
                            @keydown.enter.prevent="stopEditingLabel"
                        />
                        <button
                            v-else
                            type="button"
                            class="h-8 max-w-[230px] truncate px-1 text-left font-serif text-sm text-[#3D3D3D] transition-colors hover:text-[#C67C4E]"
                            :disabled="isSaving"
                            :title="form.label[index] || '未命名标签'"
                        >
                            {{ form.label[index] || '未命名标签' }}
                        </button>
                        <div
                            v-if="editingLabelIndex !== index"
                            class="flex w-0 items-center gap-1 overflow-hidden opacity-0 transition-[width,opacity] duration-200 ease-out group-hover:w-14 group-hover:opacity-100"
                        >
                            <button
                                type="button"
                                class="inline-flex h-7 w-7 items-center justify-center rounded-sm text-[#8A8A8A] transition-colors hover:bg-[#EAE6DE] hover:text-[#C67C4E] disabled:cursor-not-allowed disabled:opacity-60"
                                :disabled="isSaving"
                                aria-label="修改标签"
                                title="修改"
                                @click.stop="editLabel(index)"
                            >
                                <Pencil :size="13" />
                            </button>
                            <button
                                type="button"
                                class="inline-flex h-7 w-7 items-center justify-center rounded-sm text-[#8A8A8A] transition-colors hover:bg-[#F1E3DF] hover:text-[#B95D5D] disabled:cursor-not-allowed disabled:opacity-60"
                                :disabled="isSaving"
                                aria-label="删除标签"
                                title="删除"
                                @click.stop="removeLabel(index)"
                            >
                                <Trash2 :size="13" />
                            </button>
                        </div>
                    </li>
                    <button
                        type="button"
                        class="inline-flex h-10 w-10 shrink-0 items-center justify-center rounded-sm border border-[#D6D1C4] text-[#8A8A8A] transition-colors hover:border-[#C67C4E] hover:text-[#C67C4E] disabled:cursor-not-allowed disabled:opacity-60"
                        :disabled="isSaving"
                        aria-label="添加标签"
                        @click="addLabel"
                    >
                        <Plus :size="14" />
                    </button>
                </ul>
            </div>

            <label class="flex min-h-[100px] flex-1 flex-col">
                <span class="mb-2 block font-serif text-xs uppercase tracking-wider text-[#8A8A8A]">
                    描述
                </span>
                <textarea
                    v-model="form.comment"
                    class="flex-1 resize-none border-b border-[#D6D1C4] bg-[#F7F5F0] p-3 font-serif text-[#3D3D3D] transition-colors placeholder:text-[#BDB9AE] focus:border-[#C67C4E] focus:outline-none"
                    placeholder="在此添加曲目描述"
                    :disabled="isSaving"
                ></textarea>
            </label>

            <label
                v-if="showDefaultToggle"
                class="group flex cursor-pointer items-center gap-3 py-1"
            >
                <div class="relative flex items-center">
                    <input v-model="form.isDefault" type="checkbox" class="peer sr-only" />
                    <div
                        class="h-5 w-5 border border-[#D6D1C4] bg-[#F7F5F0] transition-colors peer-checked:border-[#C67C4E] peer-checked:bg-[#C67C4E]"
                    ></div>
                    <svg
                        class="pointer-events-none absolute inset-0 h-5 w-5 text-white opacity-0 transition-opacity peer-checked:opacity-100"
                        viewBox="0 0 24 24"
                        fill="none"
                        stroke="currentColor"
                        stroke-width="3"
                        stroke-linecap="round"
                        stroke-linejoin="round"
                    >
                        <polyline points="20 6 9 17 4 12"></polyline>
                    </svg>
                </div>
                <span class="text-sm text-[#5E564D] transition-colors group-hover:text-[#2C2420]">
                    作品默认曲目
                </span>
            </label>

            <p v-if="error" class="text-sm text-[#B95D5D]">
                {{ error }}
            </p>

            <div class="mt-auto flex gap-3 border-t border-[#EAE6DE] pt-4">
                <button
                    type="button"
                    class="flex-1 border border-[#D6D1C4] px-4 py-3 text-sm uppercase tracking-wide text-[#8A8A8A] transition-colors hover:bg-[#F7F5F0] hover:text-[#5A5A5A]"
                    :disabled="isSaving"
                    @click="closeModal"
                >
                    取消
                </button>
                <button
                    type="button"
                    class="flex-1 bg-[#2B221B] px-4 py-3 text-sm uppercase tracking-wide text-[#F7F5F0] shadow-md transition-colors hover:bg-[#C67C4E] disabled:cursor-not-allowed disabled:opacity-60"
                    :disabled="isSaving"
                    @click="submit"
                >
                    {{ isSaving ? '保存中...' : '保存更改' }}
                </button>
            </div>
        </div>
    </div>
</template>

<style scoped>
.label-strip {
    scrollbar-color: #d6d1c4 transparent;
    scrollbar-gutter: stable;
}

.label-strip::-webkit-scrollbar {
    height: 8px;
}

.label-strip::-webkit-scrollbar-track {
    background: transparent;
}

.label-strip::-webkit-scrollbar-thumb {
    background-color: #d6d1c4;
    border: 2px solid transparent;
    border-radius: 999px;
    background-clip: content-box;
}

.label-strip::-webkit-scrollbar-thumb:hover {
    background-color: #c67c4e;
}
</style>
