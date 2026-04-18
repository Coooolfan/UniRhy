<script setup lang="ts">
import { ref } from 'vue'
import { normalizeApiError } from '@/ApiInstance'
import { useModalContext } from '@/components/modals/modalContext'

export type MergeSelectOption = {
    id: number
    title: string
    subtitle: string
}

const props = withDefaults(
    defineProps<{
        description: string
        options: MergeSelectOption[]
        note?: string
        confirmText?: string
        submittingText?: string
        modalTestId?: string
        optionRadioTestId?: string
        confirmTestId?: string
        missingTargetMessage?: string
        onConfirm: (targetId: number) => Promise<void> | void
    }>(),
    {
        note: '',
        confirmText: '确认合并',
        submittingText: '合并中...',
        modalTestId: undefined,
        optionRadioTestId: undefined,
        confirmTestId: undefined,
        missingTargetMessage: '请选择一个目标项。',
    },
)

const modal = useModalContext<undefined>()

const selectedTargetId = ref<number | null>(props.options[0]?.id ?? null)
const error = ref('')
const submitting = ref(false)

const closeModal = () => {
    if (submitting.value) {
        return
    }

    modal.close()
}

const submit = async () => {
    if (selectedTargetId.value === null) {
        error.value = props.missingTargetMessage
        return
    }

    submitting.value = true
    error.value = ''

    try {
        await props.onConfirm(selectedTargetId.value)
        modal.resolve(undefined)
    } catch (submitError) {
        error.value = normalizeApiError(submitError).message ?? '合并失败'
    } finally {
        submitting.value = false
    }
}
</script>

<template>
    <div :data-testid="modalTestId" class="space-y-6">
        <p class="text-sm text-[#8C857B]">
            {{ description }}
        </p>

        <div class="max-h-[50vh] space-y-3 overflow-y-auto">
            <label
                v-for="option in options"
                :key="option.id"
                class="flex cursor-pointer items-start gap-3 border border-[#EAE6DE] p-3 transition-colors hover:bg-[#F7F5F0]"
            >
                <input
                    v-model="selectedTargetId"
                    type="radio"
                    :value="option.id"
                    :data-testid="optionRadioTestId"
                    class="mt-1 accent-[#C27E46]"
                    :disabled="submitting"
                />
                <span class="min-w-0">
                    <span class="block truncate font-serif text-[#2B221B]">
                        {{ option.title }}
                    </span>
                    <span class="block truncate text-xs text-[#8C857B]">
                        {{ option.subtitle }}
                    </span>
                </span>
            </label>
        </div>

        <p v-if="note" class="text-xs leading-relaxed text-[#8C857B]">
            {{ note }}
        </p>

        <p v-if="error" class="text-sm text-[#B95D5D]">
            {{ error }}
        </p>
        <p v-else-if="options.length < 2" class="text-sm text-[#8C857B]">
            需要至少选中 2 项才能执行合并。
        </p>

        <div class="grid grid-cols-2 gap-3 border-t border-[#EAE6DE] pt-6">
            <button
                type="button"
                class="border border-[#D6D1C4] px-4 py-2.5 text-sm tracking-wide text-[#8A8A8A] transition-colors hover:bg-[#F7F5F0] hover:text-[#5A5A5A]"
                :disabled="submitting"
                @click="closeModal"
            >
                取消
            </button>
            <button
                type="button"
                :data-testid="confirmTestId"
                class="bg-[#C27E46] px-4 py-2.5 text-sm tracking-wide text-white transition-colors hover:bg-[#B06D39] disabled:cursor-not-allowed disabled:opacity-50"
                :disabled="options.length < 2 || selectedTargetId === null || submitting"
                @click="submit"
            >
                {{ submitting ? submittingText : confirmText }}
            </button>
        </div>
    </div>
</template>
