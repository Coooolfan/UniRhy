<script setup lang="ts">
import { computed } from 'vue'
import { Disc, FileAudio, Users, Image as ImageIcon } from 'lucide-vue-next'

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
    rawArtists: readonly { id: number; name: string }[]
    assets: readonly RecordingAsset[]
}

export type RecordingEditForm = {
    title: string
    label: string
    comment: string
    type: string
    isDefault: boolean
}

type Props = {
    open: boolean
    recording: RecordingPreview | null
    form: RecordingEditForm
    error: string
    isSaving: boolean
    showDefaultToggle?: boolean
}

const props = withDefaults(defineProps<Props>(), {
    showDefaultToggle: false,
})

const emit = defineEmits<{
    (event: 'update:form', value: RecordingEditForm): void
    (event: 'close'): void
    (event: 'submit'): void
}>()

const titleModel = computed({
    get: () => props.form.title,
    set: (value: string) => emit('update:form', { ...props.form, title: value }),
})

const typeModel = computed({
    get: () => props.form.type,
    set: (value: string) => emit('update:form', { ...props.form, type: value }),
})

const labelModel = computed({
    get: () => props.form.label,
    set: (value: string) => emit('update:form', { ...props.form, label: value }),
})

const commentModel = computed({
    get: () => props.form.comment,
    set: (value: string) => emit('update:form', { ...props.form, comment: value }),
})

const isDefaultModel = computed({
    get: () => props.form.isDefault,
    set: (value: boolean) => emit('update:form', { ...props.form, isDefault: value }),
})

const closeModal = () => {
    if (props.isSaving) {
        return
    }
    emit('close')
}

const submit = () => {
    if (props.isSaving) {
        return
    }
    emit('submit')
}
</script>

<template>
    <Teleport to="body">
        <Transition
            enter-active-class="transition duration-200 ease-out"
            enter-from-class="opacity-0"
            enter-to-class="opacity-100"
            leave-active-class="transition duration-150 ease-in"
            leave-from-class="opacity-100"
            leave-to-class="opacity-0"
        >
            <div
                v-if="open"
                class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-[#2B221B]/60"
                @click.self="closeModal"
            >
                <div
                    class="bg-[#fffcf5] p-8 w-full max-w-4xl shadow-[0_8px_30px_rgba(0,0,0,0.12)] border border-[#EAE6DE] relative transform transition-all max-h-[90vh] overflow-y-auto"
                >
                    <div
                        class="absolute top-0 right-0 w-16 h-16 bg-linear-to-bl from-[#EAE6DE]/30 to-transparent pointer-events-none"
                    ></div>

                    <div class="mb-8 text-center">
                        <div
                            class="inline-flex items-center justify-center w-12 h-12 rounded-full bg-[#FAF9F6] mb-4 border border-[#EAE6DE]"
                        >
                            <Disc :size="24" class="text-[#C67C4E]" />
                        </div>
                        <h3 class="font-serif text-2xl text-[#2B221B]">关于录音</h3>
                        <p class="text-xs text-[#8A8A8A] mt-2 font-serif italic">About Recording</p>
                    </div>

                    <div class="grid grid-cols-1 md:grid-cols-2 gap-8">
                        <div class="space-y-6">
                            <div
                                v-if="recording"
                                class="bg-[#F7F5F0] border border-[#D6D1C4] p-5 rounded-sm h-full flex flex-col"
                            >
                                <div class="flex gap-5 mb-6">
                                    <div
                                        class="w-24 h-24 shrink-0 bg-[#EAE6DE] rounded-sm overflow-hidden shadow-sm"
                                    >
                                        <img
                                            v-if="recording.cover"
                                            :src="recording.cover"
                                            class="w-full h-full object-cover"
                                        />
                                        <div
                                            v-else
                                            class="w-full h-full flex items-center justify-center text-[#8C857B]"
                                        >
                                            <ImageIcon :size="24" />
                                        </div>
                                    </div>
                                    <div class="flex-1 min-w-0 space-y-3 py-1">
                                        <div>
                                            <div
                                                class="flex items-center gap-1.5 text-[#8C857B] text-xs uppercase tracking-wider mb-1"
                                            >
                                                <Users :size="12" />
                                                <span>Artists</span>
                                            </div>
                                            <div class="text-[#2C2420] font-medium leading-snug">
                                                {{
                                                    recording.rawArtists
                                                        .map((artist) => artist.name)
                                                        .join(', ') || 'Unknown Artist'
                                                }}
                                            </div>
                                        </div>
                                        <div>
                                            <div
                                                class="flex items-center gap-1.5 text-[#8C857B] text-xs uppercase tracking-wider mb-1"
                                            >
                                                <FileAudio :size="12" />
                                                <span>Assets</span>
                                            </div>
                                            <div class="text-[#2C2420] truncate">
                                                {{ recording.assets.length }} file(s) attached
                                            </div>
                                        </div>
                                    </div>
                                </div>

                                <div
                                    v-if="recording.assets.length > 0"
                                    class="border-t border-[#D6D1C4] pt-4 flex-1"
                                >
                                    <div
                                        class="text-xs text-[#8C857B] mb-3 uppercase tracking-wider"
                                    >
                                        Attached Files
                                    </div>
                                    <div class="flex flex-col gap-2">
                                        <div
                                            v-for="asset in recording.assets"
                                            :key="asset.mediaFile.id"
                                            class="flex items-start gap-2 text-xs bg-[#EAE6DE]/50 px-2 py-2 rounded-sm text-[#5E564D] border border-[#D6D1C4]/50 w-full hover:bg-[#EAE6DE] transition-colors"
                                        >
                                            <FileAudio
                                                :size="14"
                                                class="shrink-0 mt-0.5 text-[#C17D46]"
                                            />
                                            <div class="min-w-0 flex-1">
                                                <div
                                                    class="font-medium break-all text-[#2C2420]"
                                                    :title="asset.mediaFile.objectKey"
                                                >
                                                    {{ asset.mediaFile.objectKey }}
                                                </div>
                                                <div
                                                    class="flex flex-wrap items-center gap-2 text-[10px] text-[#8C857B] font-mono mt-1"
                                                >
                                                    <span class="bg-[#D6D1C4]/30 px-1 rounded-xs">
                                                        {{ asset.mediaFile.mimeType }}
                                                    </span>
                                                    <span class="w-px h-2 bg-[#D6D1C4]"></span>
                                                    <span>
                                                        {{
                                                            asset.mediaFile.ossProvider
                                                                ? `OSS#${asset.mediaFile.ossProvider.id}`
                                                                : asset.mediaFile.fsProvider
                                                                  ? `FS#${asset.mediaFile.fsProvider.id}`
                                                                  : 'UNKNOWN'
                                                        }}
                                                    </span>
                                                    <span class="w-px h-2 bg-[#D6D1C4]"></span>
                                                    <span>ID: {{ asset.mediaFile.id }}</span>
                                                </div>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                                <div
                                    v-else
                                    class="flex-1 flex items-center justify-center text-[#8C857B] text-xs italic opacity-70"
                                >
                                    No audio assets attached
                                </div>
                            </div>
                        </div>

                        <div class="space-y-5 flex flex-col h-full">
                            <label class="block">
                                <span
                                    class="text-xs uppercase tracking-wider text-[#8A8A8A] font-serif block mb-2"
                                >
                                    Title
                                </span>
                                <input
                                    v-model="titleModel"
                                    type="text"
                                    maxlength="255"
                                    class="w-full bg-[#F7F5F0] border-b border-[#D6D1C4] p-3 text-[#3D3D3D] focus:outline-none focus:border-[#C67C4E] transition-colors font-serif placeholder:text-[#BDB9AE]"
                                    placeholder="Recording Title"
                                    :disabled="isSaving"
                                />
                            </label>

                            <div class="grid grid-cols-2 gap-4">
                                <label class="block">
                                    <span
                                        class="text-xs uppercase tracking-wider text-[#8A8A8A] font-serif block mb-2"
                                    >
                                        Type
                                    </span>
                                    <input
                                        v-model="typeModel"
                                        type="text"
                                        maxlength="50"
                                        class="w-full bg-[#F7F5F0] border-b border-[#D6D1C4] p-3 text-[#3D3D3D] focus:outline-none focus:border-[#C67C4E] transition-colors font-serif placeholder:text-[#BDB9AE]"
                                        placeholder="e.g. Live, Studio"
                                        :disabled="isSaving"
                                    />
                                </label>
                                <label class="block">
                                    <span
                                        class="text-xs uppercase tracking-wider text-[#8A8A8A] font-serif block mb-2"
                                    >
                                        Label
                                    </span>
                                    <input
                                        v-model="labelModel"
                                        type="text"
                                        maxlength="50"
                                        class="w-full bg-[#F7F5F0] border-b border-[#D6D1C4] p-3 text-[#3D3D3D] focus:outline-none focus:border-[#C67C4E] transition-colors font-serif placeholder:text-[#BDB9AE]"
                                        placeholder="Optional label"
                                        :disabled="isSaving"
                                    />
                                </label>
                            </div>

                            <label class="flex-1 flex flex-col min-h-[100px]">
                                <span
                                    class="text-xs uppercase tracking-wider text-[#8A8A8A] font-serif block mb-2"
                                >
                                    Comment
                                </span>
                                <textarea
                                    v-model="commentModel"
                                    class="w-full flex-1 bg-[#F7F5F0] border-b border-[#D6D1C4] p-3 text-[#3D3D3D] focus:outline-none focus:border-[#C67C4E] transition-colors font-serif placeholder:text-[#BDB9AE] resize-none"
                                    placeholder="Add a comment..."
                                    :disabled="isSaving"
                                ></textarea>
                            </label>

                            <label
                                v-if="showDefaultToggle"
                                class="flex items-center gap-3 cursor-pointer group py-1"
                            >
                                <div class="relative flex items-center">
                                    <input
                                        v-model="isDefaultModel"
                                        type="checkbox"
                                        class="peer sr-only"
                                        :disabled="isSaving"
                                    />
                                    <div
                                        class="w-5 h-5 border border-[#D6D1C4] bg-[#F7F5F0] peer-checked:bg-[#C67C4E] peer-checked:border-[#C67C4E] transition-colors"
                                    ></div>
                                    <svg
                                        class="absolute inset-0 w-5 h-5 text-white opacity-0 peer-checked:opacity-100 transition-opacity pointer-events-none"
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
                                <span
                                    class="text-sm text-[#5E564D] group-hover:text-[#2C2420] transition-colors"
                                >
                                    默认版本 (Default Version)
                                </span>
                            </label>

                            <p v-if="error" class="text-sm text-[#B95D5D]">
                                {{ error }}
                            </p>

                            <div class="flex gap-3 pt-4 border-t border-[#EAE6DE] mt-auto">
                                <button
                                    type="button"
                                    class="flex-1 px-4 py-3 border border-[#D6D1C4] text-[#8A8A8A] hover:bg-[#F7F5F0] hover:text-[#5A5A5A] transition-colors text-sm uppercase tracking-wide"
                                    :disabled="isSaving"
                                    @click="closeModal"
                                >
                                    取消
                                </button>
                                <button
                                    type="button"
                                    class="flex-1 px-4 py-3 bg-[#2B221B] text-[#F7F5F0] hover:bg-[#C67C4E] transition-colors text-sm uppercase tracking-wide shadow-md disabled:opacity-60 disabled:cursor-not-allowed"
                                    :disabled="isSaving"
                                    @click="submit"
                                >
                                    {{ isSaving ? '保存中...' : '保存更改' }}
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </Transition>
    </Teleport>
</template>
