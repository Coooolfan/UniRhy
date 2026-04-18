<script setup lang="ts">
import { ref } from 'vue'
import { normalizeApiError } from '@/ApiInstance'
import { useModalContext } from '@/components/modals/modalContext'

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
        error.value = '请输入歌单名称'
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
        error.value = normalizeApiError(submitError).message ?? '创建歌单失败'
    } finally {
        isSubmitting.value = false
    }
}
</script>

<template>
    <div class="space-y-6">
        <label class="block">
            <span class="mb-2 block font-serif text-xs uppercase tracking-wider text-[#8A8A8A]">
                歌单名
            </span>
            <input
                v-model="name"
                type="text"
                maxlength="100"
                class="w-full border-b border-[#D6D1C4] bg-[#F7F5F0] p-3 font-serif text-[#3D3D3D] transition-colors placeholder:text-[#BDB9AE] focus:border-[#C67C4E] focus:outline-none"
                placeholder="例如：通勤日常"
                :disabled="isSubmitting"
            />
        </label>

        <label class="block">
            <span class="mb-2 block font-serif text-xs uppercase tracking-wider text-[#8A8A8A]">
                歌单描述
            </span>
            <textarea
                v-model="comment"
                rows="3"
                maxlength="500"
                class="w-full resize-none border-b border-[#D6D1C4] bg-[#F7F5F0] p-3 font-serif text-[#3D3D3D] transition-colors placeholder:text-[#BDB9AE] focus:border-[#C67C4E] focus:outline-none"
                placeholder="可选的歌单描述"
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
                取消
            </button>
            <button
                type="button"
                class="flex-1 bg-[#2B221B] px-4 py-2.5 text-sm uppercase tracking-wide text-[#F7F5F0] shadow-md transition-colors hover:bg-[#C67C4E] disabled:cursor-not-allowed disabled:opacity-60"
                :disabled="isSubmitting"
                @click="submit"
            >
                <span v-if="isSubmitting">Creating...</span>
                <span v-else>创建歌单</span>
            </button>
        </div>
    </div>
</template>
