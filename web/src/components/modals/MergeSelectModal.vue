<script setup lang="ts">
import { computed } from 'vue'

export type MergeSelectOption = {
    id: number
    title: string
    subtitle: string
}

const props = withDefaults(
    defineProps<{
        open: boolean
        title: string
        description: string
        options: MergeSelectOption[]
        targetId: number | null
        error?: string
        note?: string
        submitting?: boolean
        confirmDisabled?: boolean
        confirmText?: string
        submittingText?: string
        modalTestId?: string
        optionRadioTestId?: string
        confirmTestId?: string
    }>(),
    {
        error: '',
        note: '',
        submitting: false,
        confirmDisabled: false,
        confirmText: '确认合并',
        submittingText: '合并中...',
        modalTestId: undefined,
        optionRadioTestId: undefined,
        confirmTestId: undefined,
    },
)

const emit = defineEmits<{
    (e: 'close'): void
    (e: 'confirm'): void
    (e: 'update:targetId', value: number): void
}>()

const selectedTargetId = computed({
    get: () => props.targetId,
    set: (value: number | null) => {
        if (value !== null) {
            emit('update:targetId', value)
        }
    },
})
</script>

<template>
    <Teleport to="body">
        <Transition
            enter-active-class="transition duration-200 ease-out"
            enter-from-class="opacity-0"
            enter-to-class="opacity-100"
            leave-active-class="transition duration-150 ease-in"
            leave-from-class="opacity-100"
            leave-to-class="opacity-0"
        >
            <div
                v-if="open"
                :data-testid="modalTestId"
                class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-[#2B221B]/60"
                @click.self="emit('close')"
            >
                <div
                    class="bg-[#fffcf5] w-full max-w-lg max-h-[85vh] flex flex-col shadow-[0_8px_30px_rgba(0,0,0,0.12)] border border-[#EAE6DE]"
                >
                    <div class="px-8 pt-8 pb-6 border-b border-[#EAE6DE]">
                        <h3 class="font-serif text-2xl text-[#2B221B]">{{ title }}</h3>
                        <p class="text-sm text-[#8C857B] mt-2">{{ description }}</p>
                    </div>

                    <div class="px-8 py-6 overflow-y-auto">
                        <div class="space-y-3">
                            <label
                                v-for="option in options"
                                :key="option.id"
                                class="flex items-start gap-3 p-3 border border-[#EAE6DE] cursor-pointer hover:bg-[#F7F5F0] transition-colors"
                            >
                                <input
                                    v-model="selectedTargetId"
                                    type="radio"
                                    :value="option.id"
                                    :data-testid="optionRadioTestId"
                                    class="mt-1 accent-[#C27E46]"
                                />
                                <span class="min-w-0">
                                    <span class="block text-[#2B221B] font-serif truncate">
                                        {{ option.title }}
                                    </span>
                                    <span class="block text-xs text-[#8C857B] truncate">
                                        {{ option.subtitle }}
                                    </span>
                                </span>
                            </label>
                        </div>

                        <p v-if="note" class="mt-4 text-xs text-[#8C857B] leading-relaxed">
                            {{ note }}
                        </p>

                        <p v-if="error" class="text-sm text-[#B95D5D] mt-4">{{ error }}</p>
                        <p v-else-if="options.length < 2" class="text-sm text-[#8C857B] mt-4">
                            需要至少选中 2 项才能执行合并。
                        </p>
                    </div>

                    <div class="p-8 pt-6 border-t border-[#EAE6DE] grid grid-cols-2 gap-3">
                        <button
                            type="button"
                            class="px-4 py-2.5 border border-[#D6D1C4] text-[#8A8A8A] hover:bg-[#F7F5F0] hover:text-[#5A5A5A] transition-colors text-sm tracking-wide"
                            :disabled="submitting"
                            @click="emit('close')"
                        >
                            取消
                        </button>
                        <button
                            type="button"
                            :data-testid="confirmTestId"
                            class="px-4 py-2.5 bg-[#C27E46] text-white text-sm tracking-wide transition-colors hover:bg-[#B06D39] disabled:opacity-50 disabled:cursor-not-allowed"
                            :disabled="confirmDisabled"
                            @click="emit('confirm')"
                        >
                            {{ submitting ? submittingText : confirmText }}
                        </button>
                    </div>
                </div>
            </div>
        </Transition>
    </Teleport>
</template>
