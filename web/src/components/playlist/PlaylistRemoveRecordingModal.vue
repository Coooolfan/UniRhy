<script setup lang="ts">
import { ref } from 'vue'
import { normalizeApiError } from '@/ApiInstance'
import { useModalContext } from '@/components/modals/modalContext'

const props = defineProps<{
    recordingTitle: string
    onConfirm: () => Promise<void> | void
}>()

const modal = useModalContext<undefined>()

const isRemoving = ref(false)
const error = ref('')

const closeModal = () => {
    if (isRemoving.value) {
        return
    }

    modal.close()
}

const confirm = async () => {
    if (isRemoving.value) {
        return
    }

    isRemoving.value = true
    error.value = ''

    try {
        await props.onConfirm()
        modal.resolve(undefined)
    } catch (submitError) {
        error.value = normalizeApiError(submitError).message ?? '移除曲目失败'
    } finally {
        isRemoving.value = false
    }
}
</script>

<template>
    <div class="space-y-6">
        <p class="text-sm text-[#8C857B]">确认从当前歌单中移除「{{ recordingTitle }}」？</p>

        <p v-if="error" class="text-sm text-[#B95D5D]">
            {{ error }}
        </p>

        <div class="flex gap-3 border-t border-[#EAE6DE] pt-4">
            <button
                type="button"
                class="flex-1 border border-[#D6D1C4] px-4 py-2.5 text-sm uppercase tracking-wide text-[#8A8A8A] transition-colors hover:bg-[#F7F5F0] hover:text-[#5A5A5A] disabled:cursor-not-allowed disabled:opacity-60"
                :disabled="isRemoving"
                @click="closeModal"
            >
                取消
            </button>
            <button
                type="button"
                class="flex-1 bg-[#2B221B] px-4 py-2.5 text-sm uppercase tracking-wide text-[#F7F5F0] shadow-md transition-colors hover:bg-[#B95D5D] disabled:cursor-not-allowed disabled:opacity-60"
                :disabled="isRemoving"
                @click="confirm"
            >
                <span v-if="isRemoving">移除中...</span>
                <span v-else>确认移除</span>
            </button>
        </div>
    </div>
</template>
