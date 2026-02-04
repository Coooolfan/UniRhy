<script setup lang="ts">
import { Trash2 } from 'lucide-vue-next'

type Props = {
    open: boolean
    isSaving: boolean
}

defineProps<Props>()
const emit = defineEmits<{
    (event: 'cancel'): void
    (event: 'confirm'): void
}>()
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
                class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-[#2B221B]/60"
                @click.self="emit('cancel')"
            >
                <div
                    class="bg-[#fffcf5] p-8 w-full max-w-sm shadow-[0_8px_30px_rgba(0,0,0,0.12)] border border-[#EAE6DE] relative transform transition-all text-center"
                >
                    <div class="mb-6">
                        <div
                            class="inline-flex items-center justify-center w-12 h-12 rounded-full bg-[#FAF9F6] mb-4 border border-[#EAE6DE] text-[#B95D5D]"
                        >
                            <Trash2 :size="24" />
                        </div>
                        <h3 class="font-serif text-xl text-[#2B221B] mb-2">确认删除?</h3>
                        <p class="text-sm text-[#8A8A8A] font-serif">此操作无法撤销。</p>
                    </div>

                    <div class="flex gap-3 pt-2">
                        <button
                            class="flex-1 px-4 py-2.5 border border-[#D6D1C4] text-[#8A8A8A] hover:bg-[#F7F5F0] hover:text-[#5A5A5A] transition-colors text-sm uppercase tracking-wide"
                            @click="emit('cancel')"
                        >
                            取消
                        </button>
                        <button
                            class="flex-1 px-4 py-2.5 bg-[#B95D5D] text-[#F7F5F0] hover:bg-[#9E4C4C] transition-colors text-sm uppercase tracking-wide shadow-md disabled:opacity-60"
                            :disabled="isSaving"
                            @click="emit('confirm')"
                        >
                            <span v-if="isSaving">Deleting...</span>
                            <span v-else>删除</span>
                        </button>
                    </div>
                </div>
            </div>
        </Transition>
    </Teleport>
</template>
