<script setup lang="ts">
import { Trash2 } from 'lucide-vue-next'

defineProps<{
    open: boolean
    recordingTitle: string
    isRemoving: boolean
    error: string
}>()

const emit = defineEmits<{
    (e: 'close'): void
    (e: 'confirm'): void
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
                @click.self="emit('close')"
            >
                <div
                    class="bg-[#fffcf5] p-8 w-full max-w-md shadow-[0_8px_30px_rgba(0,0,0,0.12)] border border-[#EAE6DE] relative transform transition-all"
                >
                    <div
                        class="absolute top-0 right-0 w-16 h-16 bg-linear-to-bl from-[#EAE6DE]/30 to-transparent pointer-events-none"
                    ></div>

                    <div class="mb-6 text-center">
                        <div
                            class="inline-flex items-center justify-center w-12 h-12 rounded-full bg-[#FAF9F6] mb-4 border border-[#EAE6DE]"
                        >
                            <Trash2 :size="22" class="text-[#B95D5D]" />
                        </div>
                        <h3 class="font-serif text-2xl text-[#2B221B]">移除录音</h3>
                        <p class="text-sm text-[#8C857B] mt-3">
                            确认从当前歌单中移除「{{ recordingTitle }}」？
                        </p>
                    </div>

                    <p v-if="error" class="text-sm text-[#B95D5D] mb-4">
                        {{ error }}
                    </p>

                    <div class="flex gap-3 pt-4 border-t border-[#EAE6DE]">
                        <button
                            type="button"
                            class="flex-1 px-4 py-2.5 border border-[#D6D1C4] text-[#8A8A8A] hover:bg-[#F7F5F0] hover:text-[#5A5A5A] transition-colors text-sm uppercase tracking-wide disabled:opacity-60 disabled:cursor-not-allowed"
                            :disabled="isRemoving"
                            @click="emit('close')"
                        >
                            取消
                        </button>
                        <button
                            type="button"
                            class="flex-1 px-4 py-2.5 bg-[#2B221B] text-[#F7F5F0] hover:bg-[#B95D5D] transition-colors text-sm uppercase tracking-wide shadow-md disabled:opacity-60 disabled:cursor-not-allowed"
                            :disabled="isRemoving"
                            @click="emit('confirm')"
                        >
                            <span v-if="isRemoving">移除中...</span>
                            <span v-else>确认移除</span>
                        </button>
                    </div>
                </div>
            </div>
        </Transition>
    </Teleport>
</template>
