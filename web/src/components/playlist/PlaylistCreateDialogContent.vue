<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { resolveErrorMessage } from '@/i18n/errors'
import { useModalContext } from '@/components/modals/modalContext'

const { t } = useI18n()

const props = defineProps<{
    onSubmit: (payload: { name: string; comment: string }) => Promise<void> | void
}>()

const modal = useModalContext<undefined>()

const name = ref('')
const comment = ref('')
const error = ref('')
const isSubmitting = ref(false)

const closeModal = () => {
    if (isSubmitting.value) {
        return
    }

    modal.close()
}

const submit = async () => {
    const trimmedName = name.value.trim()
    if (!trimmedName) {
        error.value = t('playlistCreate.nameRequired')
        return
    }

    if (isSubmitting.value) {
        return
    }

    isSubmitting.value = true
    error.value = ''

    try {
        await props.onSubmit({
            name: trimmedName,
            comment: comment.value.trim(),
        })
        modal.resolve(undefined)
    } catch (submitError) {
        error.value = resolveErrorMessage(submitError, 'errors.fallback.playlistCreate')
    } finally {
        isSubmitting.value = false
    }
}
</script>

<template>
    <div class="space-y-6">
        <label class="block">
            <span class="mb-2 block font-serif text-xs uppercase tracking-wider text-[#8A8A8A]">
                {{ t('playlistCreate.name') }}
            </span>
            <input
                v-model="name"
                type="text"
                maxlength="100"
                class="w-full border-b border-[#D6D1C4] bg-[#F7F5F0] p-3 font-serif text-[#3D3D3D] transition-colors placeholder:text-[#BDB9AE] focus:border-[#C67C4E] focus:outline-none"
                :placeholder="t('playlistCreate.namePlaceholder')"
                :disabled="isSubmitting"
            />
        </label>

        <label class="block">
            <span class="mb-2 block font-serif text-xs uppercase tracking-wider text-[#8A8A8A]">
                {{ t('playlistCreate.description') }}
            </span>
            <textarea
                v-model="comment"
                rows="3"
                maxlength="500"
                class="w-full resize-none border-b border-[#D6D1C4] bg-[#F7F5F0] p-3 font-serif text-[#3D3D3D] transition-colors placeholder:text-[#BDB9AE] focus:border-[#C67C4E] focus:outline-none"
                :placeholder="t('playlistCreate.descriptionPlaceholder')"
                :disabled="isSubmitting"
            />
        </label>

        <p v-if="error" class="text-sm text-[#B95D5D]">
            {{ error }}
        </p>

        <div class="mt-8 flex gap-3 border-t border-[#EAE6DE] pt-6">
            <button
                type="button"
                class="flex-1 border border-[#D6D1C4] px-4 py-2.5 text-sm uppercase tracking-wide text-[#8A8A8A] transition-colors hover:bg-[#F7F5F0] hover:text-[#5A5A5A]"
                :disabled="isSubmitting"
                @click="closeModal"
            >
                {{ t('common.cancel') }}
            </button>
            <button
                type="button"
                class="flex-1 bg-[#2B221B] px-4 py-2.5 text-sm uppercase tracking-wide text-[#F7F5F0] shadow-md transition-colors hover:bg-[#C67C4E] disabled:cursor-not-allowed disabled:opacity-60"
                :disabled="isSubmitting"
                @click="submit"
            >
                <span v-if="isSubmitting">{{ t('common.saving') }}</span>
                <span v-else>{{ t('playlistCreate.create') }}</span>
            </button>
        </div>
    </div>
</template>
