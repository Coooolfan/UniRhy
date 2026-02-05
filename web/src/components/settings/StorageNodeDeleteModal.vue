<script setup lang="ts">
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
        <!-- Backdrop: Fade only -->
        <Transition
            enter-active-class="transition duration-500 ease-out"
            enter-from-class="opacity-0"
            enter-to-class="opacity-100"
            leave-active-class="transition duration-300 ease-in"
            leave-from-class="opacity-100"
            leave-to-class="opacity-0"
        >
            <div v-if="open" class="fixed inset-0 z-50 bg-black/50" @click="emit('cancel')"></div>
        </Transition>

        <!-- Modal: Fade + Slide Up -->
        <Transition
            enter-active-class="transition duration-500 ease-out"
            enter-from-class="opacity-0 translate-y-8"
            enter-to-class="opacity-100 translate-y-0"
            leave-active-class="transition duration-300 ease-in"
            leave-from-class="opacity-100 translate-y-0"
            leave-to-class="opacity-0 translate-y-8"
        >
            <div
                v-if="open"
                class="fixed inset-0 z-50 flex items-center justify-center p-4 pointer-events-none"
            >
                <!-- Card Container -->
                <div class="relative w-full max-w-[480px] pointer-events-auto">
                    <!-- Decorative Back Card (Stacked Paper Effect) -->
                    <div class="absolute inset-0 bg-[#F0EEE6] shadow-md transform -rotate-2"></div>

                    <!-- Main Card -->
                    <div
                        class="relative bg-[#FAF9F6] p-12 shadow-[0_20px_40px_-12px_rgba(43,34,27,0.15)]"
                    >
                        <!-- Title Section -->
                        <div class="mb-10">
                            <h3 class="font-serif text-3xl text-[#2B221B] tracking-wide mb-6">
                                移除节点
                            </h3>
                            <!-- Decorative Line -->
                            <div class="h-px w-full bg-[#2B221B]"></div>
                        </div>

                        <!-- Content -->
                        <div class="mb-12">
                            <div class="font-serif text-lg text-[#2B221B] mb-3">确认操作</div>
                            <p class="font-serif text-[#5A5A5A] text-base leading-relaxed mb-6">
                                确定要永久移除此存储节点配置吗？
                            </p>

                            <!-- Warning Note -->
                            <p
                                class="font-serif text-sm text-[#B95D5D] tracking-wide flex items-center gap-2"
                            >
                                * 此操作无法撤销
                            </p>
                        </div>

                        <!-- Buttons -->
                        <div class="flex items-center gap-4">
                            <button
                                class="flex-1 py-3 border border-[#8A8A8A] text-[#5A5A5A] hover:bg-[#EAE6DE] hover:text-[#2B221B] transition-all duration-300 font-serif text-sm tracking-[0.2em] uppercase active:scale-95"
                                @click="emit('cancel')"
                            >
                                取消
                            </button>

                            <button
                                class="flex-1 py-3 border border-[#B95D5D] text-[#B95D5D] hover:bg-[#B95D5D] hover:text-[#FAF9F6] transition-all duration-300 font-serif text-sm tracking-[0.2em] uppercase active:scale-95"
                                :disabled="isSaving"
                                @click="emit('confirm')"
                            >
                                <span v-if="isSaving">PROCESSING...</span>
                                <span v-else>确认移除</span>
                            </button>
                        </div>
                    </div>
                </div>
            </div>
        </Transition>
    </Teleport>
</template>
