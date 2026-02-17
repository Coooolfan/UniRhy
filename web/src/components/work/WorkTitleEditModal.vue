<script setup lang="ts">
import { computed } from 'vue'
import { Music } from 'lucide-vue-next'

type Props = {
    open: boolean
    title: string
    error: string
    isSaving: boolean
}

const props = defineProps<Props>()

const emit = defineEmits<{
    (event: 'update:title', value: string): void
    (event: 'cancel'): void
    (event: 'save'): void
}>()

const titleModel = computed({
    get: () => props.title,
    set: (value: string) => emit('update:title', value),
})

const closeModal = () => {
    if (props.isSaving) {
        return
    }
    emit('cancel')
}
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
                @click.self="closeModal"
            >
                <div
                    class="bg-[#fffcf5] p-8 w-full max-w-md shadow-[0_8px_30px_rgba(0,0,0,0.12)] border border-[#EAE6DE] relative transform transition-all"
                >
                    <div
                        class="absolute top-0 right-0 w-16 h-16 bg-linear-to-bl from-[#EAE6DE]/30 to-transparent pointer-events-none"
                    ></div>

                    <div class="mb-8 text-center">
                        <div
                            class="inline-flex items-center justify-center w-12 h-12 rounded-full bg-[#FAF9F6] mb-4 border border-[#EAE6DE]"
                        >
                            <Music :size="24" class="text-[#C67C4E]" />
                        </div>
                        <h3 class="font-serif text-2xl text-[#2B221B]">编辑作品</h3>
                        <p class="text-xs text-[#8A8A8A] mt-2 font-serif italic">Edit Work</p>
                    </div>

                    <div class="space-y-6">
                        <label class="block">
                            <span
                                class="text-xs uppercase tracking-wider text-[#8A8A8A] font-serif block mb-2"
                            >
                                Title
                            </span>
                            <input
                                v-model="titleModel"
                                type="text"
                                maxlength="255"
                                class="w-full bg-[#F7F5F0] border-b border-[#D6D1C4] p-3 text-[#3D3D3D] focus:outline-none focus:border-[#C67C4E] transition-colors font-serif placeholder:text-[#BDB9AE]"
                                placeholder="e.g. New Work Title"
                                :disabled="isSaving"
                            />
                        </label>

                        <p v-if="error" class="text-sm text-[#B95D5D]">
                            {{ error }}
                        </p>

                        <div class="flex gap-3 mt-8 pt-6 border-t border-[#EAE6DE]">
                            <button
                                type="button"
                                class="flex-1 px-4 py-2.5 border border-[#D6D1C4] text-[#8A8A8A] hover:bg-[#F7F5F0] hover:text-[#5A5A5A] transition-colors text-sm uppercase tracking-wide"
                                :disabled="isSaving"
                                @click="closeModal"
                            >
                                取消
                            </button>
                            <button
                                type="button"
                                class="flex-1 px-4 py-2.5 bg-[#2B221B] text-[#F7F5F0] hover:bg-[#C67C4E] transition-colors text-sm uppercase tracking-wide shadow-md disabled:opacity-60 disabled:cursor-not-allowed"
                                :disabled="isSaving"
                                @click="emit('save')"
                            >
                                {{ isSaving ? '更新中...' : '保存更改' }}
                            </button>
                        </div>
                    </div>
                </div>
            </div>
        </Transition>
    </Teleport>
</template>
