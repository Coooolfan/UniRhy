<script setup lang="ts">
import { Image as ImageIcon, ImagePlus } from 'lucide-vue-next'
import { onBeforeUnmount, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { ArtworkEditValue } from '@/composables/artwork'

const MAX_IMAGE_SIZE = 10 * 1024 * 1024
const props = withDefaults(
    defineProps<{
        currentUrl?: string
        label: string
        disabled?: boolean
        round?: boolean
    }>(),
    {
        currentUrl: '',
        disabled: false,
        round: false,
    },
)

const model = defineModel<ArtworkEditValue>({ required: true })
const { t } = useI18n()
const fileInput = ref<HTMLInputElement | null>(null)
const previewUrl = ref(props.currentUrl)
const error = ref('')
let objectUrl = ''

const releaseObjectUrl = () => {
    if (objectUrl) {
        URL.revokeObjectURL(objectUrl)
        objectUrl = ''
    }
}

watch(
    [() => model.value.file, () => model.value.remove, () => props.currentUrl],
    ([file, remove, currentUrl]) => {
        releaseObjectUrl()
        if (file) {
            objectUrl = URL.createObjectURL(file)
            previewUrl.value = objectUrl
        } else {
            previewUrl.value = remove ? '' : currentUrl
        }
    },
    { immediate: true },
)

onBeforeUnmount(releaseObjectUrl)

const openFilePicker = () => {
    if (!props.disabled) {
        fileInput.value?.click()
    }
}

const selectFile = (event: Event) => {
    const input = event.target as HTMLInputElement
    const file = input.files?.[0]
    input.value = ''
    if (!file) {
        return
    }

    if (file.size > MAX_IMAGE_SIZE) {
        error.value = t('artwork.tooLarge')
        return
    }

    error.value = ''
    model.value = { file, remove: false }
}
</script>

<template>
    <div class="space-y-2" data-test="artwork-field">
        <div class="font-serif text-xs uppercase tracking-wider text-[#8A8A8A]">
            {{ label }}
        </div>
        <div class="flex min-w-0 items-center">
            <button
                type="button"
                class="group relative flex h-24 w-24 shrink-0 items-center justify-center overflow-hidden border border-[#D6D1C4] bg-[#EAE6DE] text-[#8C857B] transition-colors hover:border-[#C67C4E] focus-visible:border-[#C67C4E] focus-visible:outline-none disabled:cursor-not-allowed disabled:opacity-60"
                :class="round ? 'rounded-full' : 'rounded-sm'"
                :disabled="disabled"
                :aria-label="previewUrl ? t('artwork.replace') : t('artwork.select')"
                :title="previewUrl ? t('artwork.replace') : t('artwork.select')"
                data-test="artwork-picker"
                @click="openFilePicker"
            >
                <img
                    v-if="previewUrl"
                    :src="previewUrl"
                    :alt="label"
                    class="h-full w-full object-cover"
                />
                <ImageIcon v-else :size="24" />
                <span
                    class="absolute inset-0 flex items-center justify-center gap-1.5 bg-black/55 px-2 text-xs font-medium text-white opacity-0 transition-opacity group-hover:opacity-100 group-focus-visible:opacity-100"
                >
                    <ImagePlus :size="15" />
                    {{ previewUrl ? t('artwork.replace') : t('artwork.select') }}
                </span>
            </button>

            <input
                ref="fileInput"
                class="sr-only"
                type="file"
                accept="image/*"
                :disabled="disabled"
                data-test="artwork-input"
                @change="selectFile"
            />
        </div>
        <p v-if="error" class="text-xs text-[#B95D5D]" data-test="artwork-error">
            {{ error }}
        </p>
    </div>
</template>
