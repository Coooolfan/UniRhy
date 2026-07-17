<script setup lang="ts">
import { Pencil, Plus, Trash2 } from 'lucide-vue-next'
import { reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { resolveErrorMessage } from '@/i18n/errors'
import { useModalContext } from '@/components/modals/modalContext'
import { normalizeLabels } from '@/composables/recordingMedia'

const { t } = useI18n()

export type ArtistEditForm = {
    displayName: string
    alias: string[]
    comment: string
}

const props = withDefaults(
    defineProps<{
        initialForm: ArtistEditForm
        onSubmit: (form: ArtistEditForm) => Promise<void> | void
        submitText?: string
        submittingText?: string
        nameFailureMessage?: string
    }>(),
    {
        submitText: '',
        submittingText: '',
        nameFailureMessage: 'errors.fallback.artistUpdate',
    },
)

const modal = useModalContext<undefined>()

const form = reactive<ArtistEditForm>({
    displayName: props.initialForm.displayName,
    alias: [...props.initialForm.alias],
    comment: props.initialForm.comment,
})
const error = ref('')
const isSaving = ref(false)
const editingAliasIndex = ref<number | null>(null)

const closeModal = () => {
    if (isSaving.value) {
        return
    }
    modal.close()
}

const addAlias = () => {
    form.alias.push('')
    editingAliasIndex.value = form.alias.length - 1
}

const removeAlias = (index: number) => {
    form.alias.splice(index, 1)
    editingAliasIndex.value = null
}

const editAlias = (index: number) => {
    if (isSaving.value) {
        return
    }
    editingAliasIndex.value = index
}

const stopEditingAlias = () => {
    editingAliasIndex.value = null
}

const submit = async () => {
    if (isSaving.value) {
        return
    }

    if (!form.displayName.trim()) {
        error.value = t('artistEdit.nameEmpty')
        return
    }

    isSaving.value = true
    error.value = ''

    try {
        const aliases = Array.from(new Set(normalizeLabels(form.alias)))
        await props.onSubmit({
            displayName: form.displayName.trim(),
            alias: aliases,
            comment: form.comment.trim(),
        })
        modal.resolve(undefined)
    } catch (submitError) {
        error.value = resolveErrorMessage(submitError, props.nameFailureMessage)
    } finally {
        isSaving.value = false
    }
}
</script>

<template>
    <div class="flex flex-col space-y-5">
        <label class="block">
            <span class="mb-2 block font-serif text-xs uppercase tracking-wider text-[#8A8A8A]">
                {{ t('artistEdit.name') }}
            </span>
            <input
                v-model="form.displayName"
                type="text"
                maxlength="255"
                class="w-full border-b border-[#D6D1C4] bg-[#F7F5F0] p-3 font-serif text-[#3D3D3D] transition-colors placeholder:text-[#BDB9AE] focus:border-[#C67C4E] focus:outline-none"
                placeholder="Artist Name"
                :disabled="isSaving"
            />
        </label>

        <div class="block">
            <div
                class="mb-2 flex items-center justify-between gap-3 font-serif text-xs uppercase tracking-wider text-[#8A8A8A]"
            >
                <span>{{ t('artistEdit.alias') }}</span>
            </div>
            <ul class="flex h-14 min-w-0 items-start gap-2 overflow-x-auto overflow-y-hidden pb-3">
                <li
                    v-for="(_, index) in form.alias"
                    :key="index"
                    class="group flex h-10 max-w-[300px] shrink-0 items-center whitespace-nowrap rounded-sm border border-[#D6D1C4] bg-[#F7F5F0] px-2"
                >
                    <input
                        v-if="editingAliasIndex === index"
                        v-model="form.alias[index]"
                        type="text"
                        maxlength="255"
                        class="h-8 min-w-0 flex-1 truncate border-b border-[#D6D1C4] bg-transparent px-1 font-serif text-sm text-[#3D3D3D] transition-colors placeholder:text-[#BDB9AE] focus:border-[#C67C4E] focus:outline-none"
                        :placeholder="t('artistEdit.unnamedAlias')"
                        :disabled="isSaving"
                        @blur="stopEditingAlias"
                        @keydown.enter.prevent="stopEditingAlias"
                    />
                    <button
                        v-else
                        type="button"
                        class="h-8 max-w-[230px] truncate px-1 text-left font-serif text-sm text-[#3D3D3D] transition-colors hover:text-[#C67C4E]"
                        :disabled="isSaving"
                        :title="form.alias[index] || t('artistEdit.unnamedAlias')"
                    >
                        {{ form.alias[index] || t('artistEdit.unnamedAlias') }}
                    </button>
                    <div
                        v-if="editingAliasIndex !== index"
                        class="flex w-0 items-center gap-1 overflow-hidden opacity-0 transition-[width,opacity] duration-200 ease-out group-hover:w-14 group-hover:opacity-100"
                    >
                        <button
                            type="button"
                            class="inline-flex h-7 w-7 items-center justify-center rounded-sm text-[#8A8A8A] transition-colors hover:bg-[#EAE6DE] hover:text-[#C67C4E] disabled:cursor-not-allowed disabled:opacity-60"
                            :disabled="isSaving"
                            :aria-label="t('artistEdit.modifyAliasAria')"
                            :title="t('artistEdit.modifyAlias')"
                            @click.stop="editAlias(index)"
                        >
                            <Pencil :size="13" />
                        </button>
                        <button
                            type="button"
                            class="inline-flex h-7 w-7 items-center justify-center rounded-sm text-[#8A8A8A] transition-colors hover:bg-[#F1E3DF] hover:text-[#B95D5D] disabled:cursor-not-allowed disabled:opacity-60"
                            :disabled="isSaving"
                            :aria-label="t('artistEdit.deleteAliasAria')"
                            :title="t('artistEdit.deleteAlias')"
                            @click.stop="removeAlias(index)"
                        >
                            <Trash2 :size="13" />
                        </button>
                    </div>
                </li>
                <button
                    type="button"
                    class="inline-flex h-10 w-10 shrink-0 items-center justify-center rounded-sm border border-[#D6D1C4] text-[#8A8A8A] transition-colors hover:border-[#C67C4E] hover:text-[#C67C4E] disabled:cursor-not-allowed disabled:opacity-60"
                    :disabled="isSaving"
                    :aria-label="t('artistEdit.addAliasAria')"
                    @click="addAlias"
                >
                    <Plus :size="14" />
                </button>
            </ul>
        </div>

        <label class="flex min-h-[120px] flex-col">
            <span class="mb-2 block font-serif text-xs uppercase tracking-wider text-[#8A8A8A]">
                {{ t('artistEdit.description') }}
            </span>
            <textarea
                v-model="form.comment"
                rows="4"
                class="flex-1 resize-none border-b border-[#D6D1C4] bg-[#F7F5F0] p-3 font-serif text-[#3D3D3D] transition-colors placeholder:text-[#BDB9AE] focus:border-[#C67C4E] focus:outline-none"
                :placeholder="t('artistEdit.descriptionPlaceholder')"
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
                {{
                    isSaving
                        ? submittingText || t('common.saving')
                        : submitText || t('common.saveChanges')
                }}
            </button>
        </div>
    </div>
</template>
