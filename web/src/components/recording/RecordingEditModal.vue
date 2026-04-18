<script setup lang="ts">
import { reactive, ref } from 'vue'
import { Disc, FileAudio, Image as ImageIcon, Users } from 'lucide-vue-next'
import { normalizeApiError } from '@/ApiInstance'
import { useModalContext } from '@/components/modals/modalContext'

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
    cover: string
    rawArtists: readonly { id: number; displayName?: string; name?: string }[]
    assets: readonly RecordingAsset[]
}

export type RecordingEditForm = {
    title: string
    label: string
    comment: string
    type: string
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
    label: props.initialForm.label,
    comment: props.initialForm.comment,
    type: props.initialForm.type,
    isDefault: props.initialForm.isDefault,
})
const error = ref('')
const isSaving = ref(false)

const closeModal = () => {
    if (isSaving.value) {
        return
    }

    modal.close()
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
        await props.onSubmit({
            title: form.title,
            label: form.label,
            comment: form.comment,
            type: form.type,
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
    <div class="grid grid-cols-1 gap-8 md:grid-cols-2">
        <div class="space-y-6">
            <div
                v-if="recording"
                class="flex h-full flex-col rounded-sm border border-[#D6D1C4] bg-[#F7F5F0] p-5"
            >
                <div class="mb-6 flex gap-5">
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
                            <div
                                class="mb-1 flex items-center gap-1.5 text-xs uppercase tracking-wider text-[#8C857B]"
                            >
                                <Users :size="12" />
                                <span>Artists</span>
                            </div>
                            <div class="font-medium leading-snug text-[#2C2420]">
                                {{
                                    recording.rawArtists
                                        .map((artist) => artist.displayName || artist.name)
                                        .join(', ') || 'Unknown Artist'
                                }}
                            </div>
                        </div>
                        <div>
                            <div
                                class="mb-1 flex items-center gap-1.5 text-xs uppercase tracking-wider text-[#8C857B]"
                            >
                                <FileAudio :size="12" />
                                <span>Assets</span>
                            </div>
                            <div class="truncate text-[#2C2420]">
                                {{ recording.assets.length }} file(s) attached
                            </div>
                        </div>
                    </div>
                </div>

                <div
                    v-if="recording.assets.length > 0"
                    class="flex-1 border-t border-[#D6D1C4] pt-4"
                >
                    <div class="mb-3 text-xs uppercase tracking-wider text-[#8C857B]">
                        Attached Files
                    </div>
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
                    No audio assets attached
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
                <div>
                    <div class="font-serif text-lg">关于曲目</div>
                    <div class="text-xs italic text-[#8A8A8A]">About Track</div>
                </div>
            </div>

            <label class="block">
                <span class="mb-2 block font-serif text-xs uppercase tracking-wider text-[#8A8A8A]">
                    Title
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

            <div class="grid grid-cols-2 gap-4">
                <label class="block">
                    <span
                        class="mb-2 block font-serif text-xs uppercase tracking-wider text-[#8A8A8A]"
                    >
                        Type
                    </span>
                    <input
                        v-model="form.type"
                        type="text"
                        maxlength="50"
                        class="w-full border-b border-[#D6D1C4] bg-[#F7F5F0] p-3 font-serif text-[#3D3D3D] transition-colors placeholder:text-[#BDB9AE] focus:border-[#C67C4E] focus:outline-none"
                        placeholder="e.g. Live, Studio"
                        :disabled="isSaving"
                    />
                </label>
                <label class="block">
                    <span
                        class="mb-2 block font-serif text-xs uppercase tracking-wider text-[#8A8A8A]"
                    >
                        Label
                    </span>
                    <input
                        v-model="form.label"
                        type="text"
                        maxlength="50"
                        class="w-full border-b border-[#D6D1C4] bg-[#F7F5F0] p-3 font-serif text-[#3D3D3D] transition-colors placeholder:text-[#BDB9AE] focus:border-[#C67C4E] focus:outline-none"
                        placeholder="Optional label"
                        :disabled="isSaving"
                    />
                </label>
            </div>

            <label class="flex min-h-[100px] flex-1 flex-col">
                <span class="mb-2 block font-serif text-xs uppercase tracking-wider text-[#8A8A8A]">
                    Comment
                </span>
                <textarea
                    v-model="form.comment"
                    class="flex-1 resize-none border-b border-[#D6D1C4] bg-[#F7F5F0] p-3 font-serif text-[#3D3D3D] transition-colors placeholder:text-[#BDB9AE] focus:border-[#C67C4E] focus:outline-none"
                    placeholder="Add a comment..."
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
                    默认版本 (Default Version)
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
