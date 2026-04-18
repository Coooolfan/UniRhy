<script setup lang="ts">
import { ref, watch } from 'vue'
import { normalizeApiError } from '@/ApiInstance'
import { useModalContext } from '@/components/modals/modalContext'

const props = defineProps<{
    initialName: string
    initialComment: string
    onSave: (payload: { name: string; comment: string }) => Promise<void> | void
    onDelete: () => Promise<void> | void
}>()

const modal = useModalContext<undefined>()

const name = ref(props.initialName)
const comment = ref(props.initialComment)
const error = ref('')
const deleteError = ref('')
const isDeleteConfirming = ref(false)
const isSaving = ref(false)
const isDeleting = ref(false)

const isDeleteAction = () => name.value.trim().length === 0

watch(name, (nextName) => {
    if (nextName.trim().length > 0) {
        isDeleteConfirming.value = false
        deleteError.value = ''
    }
})

const closeModal = () => {
    if (isSaving.value || isDeleting.value) {
        return
    }

    modal.close()
}

const confirmDelete = async () => {
    if (isDeleting.value) {
        return
    }

    isDeleting.value = true
    deleteError.value = ''

    try {
        await props.onDelete()
        modal.resolve(undefined)
    } catch (submitError) {
        deleteError.value = normalizeApiError(submitError).message ?? '删除歌单失败'
    } finally {
        isDeleting.value = false
    }
}

const submit = async () => {
    if (isDeleteAction()) {
        error.value = ''
        if (!isDeleteConfirming.value) {
            isDeleteConfirming.value = true
            deleteError.value = ''
            return
        }

        await confirmDelete()
        return
    }

    if (isSaving.value || isDeleting.value) {
        return
    }

    isDeleteConfirming.value = false
    isSaving.value = true
    error.value = ''

    try {
        await props.onSave({
            name: name.value.trim(),
            comment: comment.value.trim(),
        })
        modal.resolve(undefined)
    } catch (submitError) {
        error.value = normalizeApiError(submitError).message ?? '更新歌单失败'
    } finally {
        isSaving.value = false
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
                placeholder="例如：我的收藏"
                :disabled="isSaving || isDeleting"
            />
            <p
                v-if="isDeleteAction() && isDeleteConfirming"
                class="mt-2 font-serif text-sm italic text-[#B95D5D]"
            >
                再次点击“确认删除”后将永久删除歌单，此操作不可恢复。
            </p>
        </label>

        <label v-if="!isDeleteAction()" class="block">
            <span class="mb-2 block font-serif text-xs uppercase tracking-wider text-[#8A8A8A]">
                歌单描述
            </span>
            <textarea
                v-model="comment"
                rows="3"
                maxlength="500"
                class="w-full resize-none border-b border-[#D6D1C4] bg-[#F7F5F0] p-3 font-serif text-[#3D3D3D] transition-colors placeholder:text-[#BDB9AE] focus:border-[#C67C4E] focus:outline-none"
                placeholder="可选的歌单描述"
                :disabled="isSaving || isDeleting"
            />
        </label>

        <p v-if="error" class="text-sm text-[#B95D5D]">{{ error }}</p>
        <p v-if="deleteError" class="text-sm text-[#B95D5D]">{{ deleteError }}</p>

        <div class="mt-8 flex gap-3 border-t border-[#EAE6DE] pt-6">
            <button
                type="button"
                class="flex-1 border border-[#D6D1C4] px-4 py-2.5 text-sm uppercase tracking-wide text-[#8A8A8A] transition-colors hover:bg-[#F7F5F0] hover:text-[#5A5A5A]"
                :disabled="isSaving || isDeleting"
                @click="closeModal"
            >
                取消
            </button>
            <button
                type="button"
                class="flex-1 px-4 py-2.5 text-sm uppercase tracking-wide text-[#F7F5F0] shadow-md transition-colors disabled:cursor-not-allowed disabled:opacity-60"
                :class="
                    isDeleteAction()
                        ? isDeleteConfirming
                            ? 'bg-[#A24E4E] hover:bg-[#8E4040]'
                            : 'bg-[#B95D5D] hover:bg-[#A24E4E]'
                        : 'bg-[#2B221B] hover:bg-[#C67C4E]'
                "
                :disabled="isSaving || isDeleting"
                @click="submit"
            >
                <span v-if="isSaving">Updating...</span>
                <span v-else-if="isDeleting">删除中...</span>
                <span v-else-if="isDeleteAction() && isDeleteConfirming">确认删除</span>
                <span v-else-if="isDeleteAction()">删除歌单</span>
                <span v-else>保存更改</span>
            </button>
        </div>
    </div>
</template>
