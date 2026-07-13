<script setup lang="ts">
import { ref } from 'vue'
import { resolveErrorMessage } from '@/i18n/errors'
import { useModalContext } from '@/components/modals/modalContext'

const props = defineProps<{
    initialTitle: string
    onSubmit: (title: string) => Promise<void> | void
}>()

const modal = useModalContext<undefined>()

const title = ref(props.initialTitle)
const error = ref('')
const isSaving = ref(false)

const closeModal = () => {
    if (isSaving.value) {
        return
    }

    modal.close()
}

const submit = async () => {
    const nextTitle = title.value.trim()

    if (!nextTitle) {
        error.value = '作品标题不能为空。'
        return
    }

    if (isSaving.value) {
        return
    }

    isSaving.value = true
    error.value = ''

    try {
        await props.onSubmit(nextTitle)
        modal.resolve(undefined)
    } catch (submitError) {
        error.value = resolveErrorMessage(submitError, 'errors.fallback.workUpdate')
    } finally {
        isSaving.value = false
    }
}
</script>

<template>
    <div class="space-y-6">
        <label class="block">
            <span class="mb-2 block font-serif text-xs uppercase tracking-wider text-[#8A8A8A]">
                Title
            </span>
            <input
                v-model="title"
                type="text"
                maxlength="255"
                class="w-full border-b border-[#D6D1C4] bg-[#F7F5F0] p-3 font-serif text-[#3D3D3D] transition-colors placeholder:text-[#BDB9AE] focus:border-[#C67C4E] focus:outline-none"
                placeholder="e.g. New Work Title"
                :disabled="isSaving"
            />
        </label>

        <p v-if="error" class="text-sm text-[#B95D5D]">
            {{ error }}
        </p>

        <div class="flex gap-3 border-t border-[#EAE6DE] pt-6">
            <button
                type="button"
                class="flex-1 border border-[#D6D1C4] px-4 py-2.5 text-sm uppercase tracking-wide text-[#8A8A8A] transition-colors hover:bg-[#F7F5F0] hover:text-[#5A5A5A]"
                :disabled="isSaving"
                @click="closeModal"
            >
                取消
            </button>
            <button
                type="button"
                class="flex-1 bg-[#2B221B] px-4 py-2.5 text-sm uppercase tracking-wide text-[#F7F5F0] shadow-md transition-colors hover:bg-[#C67C4E] disabled:cursor-not-allowed disabled:opacity-60"
                :disabled="isSaving"
                @click="submit"
            >
                {{ isSaving ? '更新中...' : '保存更改' }}
            </button>
        </div>
    </div>
</template>
