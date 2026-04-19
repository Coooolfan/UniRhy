<script setup lang="ts">
import { reactive, ref } from 'vue'
import { normalizeApiError } from '@/ApiInstance'
import { useModalContext } from '@/components/modals/modalContext'

export type AlbumEditForm = {
    title: string
    kind: string
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
    kind: props.initialForm.kind,
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
        error.value = '专辑名不能为空'
        return
    }

    isSaving.value = true
    error.value = ''

    try {
        await props.onSubmit({
            title: form.title.trim(),
            kind: form.kind.trim(),
            releaseDate: form.releaseDate,
            comment: form.comment,
        })
        modal.resolve(undefined)
    } catch (submitError) {
        error.value = normalizeApiError(submitError).message ?? '更新专辑失败'
    } finally {
        isSaving.value = false
    }
}
</script>

<template>
    <div class="flex flex-col space-y-5">
        <label class="block">
            <span class="mb-2 block font-serif text-xs uppercase tracking-wider text-[#8A8A8A]">
                专辑名
            </span>
            <input
                v-model="form.title"
                type="text"
                maxlength="255"
                class="w-full border-b border-[#D6D1C4] bg-[#F7F5F0] p-3 font-serif text-[#3D3D3D] transition-colors placeholder:text-[#BDB9AE] focus:border-[#C67C4E] focus:outline-none"
                placeholder="专辑名"
                :disabled="isSaving"
            />
        </label>

        <div class="grid grid-cols-2 gap-4">
            <label class="block">
                <span class="mb-2 block font-serif text-xs uppercase tracking-wider text-[#8A8A8A]">
                    专辑类型
                </span>
                <input
                    v-model="form.kind"
                    type="text"
                    maxlength="50"
                    class="w-full border-b border-[#D6D1C4] bg-[#F7F5F0] p-3 font-serif text-[#3D3D3D] transition-colors placeholder:text-[#BDB9AE] focus:border-[#C67C4E] focus:outline-none"
                    placeholder="专辑类型"
                    :disabled="isSaving"
                />
            </label>
            <label class="block">
                <span class="mb-2 block font-serif text-xs uppercase tracking-wider text-[#8A8A8A]">
                    发布日期
                </span>
                <input
                    v-model="form.releaseDate"
                    type="date"
                    class="w-full border-b border-[#D6D1C4] bg-[#F7F5F0] p-3 font-serif text-[#3D3D3D] transition-colors placeholder:text-[#BDB9AE] focus:border-[#C67C4E] focus:outline-none"
                    :disabled="isSaving"
                />
            </label>
        </div>

        <label class="flex min-h-[120px] flex-col">
            <span class="mb-2 block font-serif text-xs uppercase tracking-wider text-[#8A8A8A]">
                描述
            </span>
            <textarea
                v-model="form.comment"
                rows="4"
                class="flex-1 resize-none border-b border-[#D6D1C4] bg-[#F7F5F0] p-3 font-serif text-[#3D3D3D] transition-colors placeholder:text-[#BDB9AE] focus:border-[#C67C4E] focus:outline-none"
                placeholder="添加描述..."
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
</template>
