<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { resolveErrorMessage } from '@/i18n/errors'
import { useModalContext } from '@/components/modals/modalContext'

const { t } = useI18n()

export type AlbumEditForm = {
    title: string
    releaseDate: string
    comment: string
}

const props = defineProps<{
    initialForm: AlbumEditForm
    onSubmit: (form: AlbumEditForm) => Promise<void> | void
}>()

const modal = useModalContext<undefined>()

const form = reactive<AlbumEditForm>({
    title: props.initialForm.title,
    releaseDate: props.initialForm.releaseDate,
    comment: props.initialForm.comment,
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
        error.value = t('albumEdit.nameEmpty')
        return
    }

    isSaving.value = true
    error.value = ''

    try {
        await props.onSubmit({
            title: form.title.trim(),
            releaseDate: form.releaseDate,
            comment: form.comment,
        })
        modal.resolve(undefined)
    } catch (submitError) {
        error.value = resolveErrorMessage(submitError, 'errors.fallback.albumUpdate')
    } finally {
        isSaving.value = false
    }
}
</script>

<template>
    <div class="flex flex-col space-y-5">
        <label class="block">
            <span class="mb-2 block font-serif text-xs uppercase tracking-wider text-[#8A8A8A]">
                {{ t('albumEdit.name') }}
            </span>
            <input
                v-model="form.title"
                type="text"
                maxlength="255"
                class="w-full border-b border-[#D6D1C4] bg-[#F7F5F0] p-3 font-serif text-[#3D3D3D] transition-colors placeholder:text-[#BDB9AE] focus:border-[#C67C4E] focus:outline-none"
                :placeholder="t('albumEdit.namePlaceholder')"
                :disabled="isSaving"
            />
        </label>

        <label class="block">
            <span class="mb-2 block font-serif text-xs uppercase tracking-wider text-[#8A8A8A]">
                {{ t('albumEdit.releaseDate') }}
            </span>
            <input
                v-model="form.releaseDate"
                type="date"
                class="w-full border-b border-[#D6D1C4] bg-[#F7F5F0] p-3 font-serif text-[#3D3D3D] transition-colors placeholder:text-[#BDB9AE] focus:border-[#C67C4E] focus:outline-none"
                :disabled="isSaving"
            />
        </label>

        <label class="flex min-h-[120px] flex-col">
            <span class="mb-2 block font-serif text-xs uppercase tracking-wider text-[#8A8A8A]">
                {{ t('albumEdit.description') }}
            </span>
            <textarea
                v-model="form.comment"
                rows="4"
                class="flex-1 resize-none border-b border-[#D6D1C4] bg-[#F7F5F0] p-3 font-serif text-[#3D3D3D] transition-colors placeholder:text-[#BDB9AE] focus:border-[#C67C4E] focus:outline-none"
                :placeholder="t('albumEdit.descriptionPlaceholder')"
                :disabled="isSaving"
            ></textarea>
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
                {{ t('common.cancel') }}
            </button>
            <button
                type="button"
                class="flex-1 bg-[#2B221B] px-4 py-3 text-sm uppercase tracking-wide text-[#F7F5F0] shadow-md transition-colors hover:bg-[#C67C4E] disabled:cursor-not-allowed disabled:opacity-60"
                :disabled="isSaving"
                @click="submit"
            >
                {{ isSaving ? t('common.saving') : t('common.saveChanges') }}
            </button>
        </div>
    </div>
</template>
