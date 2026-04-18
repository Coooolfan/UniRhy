<script setup lang="ts">
import { computed, onMounted, onUnmounted, useSlots } from 'vue'
import { X } from 'lucide-vue-next'
import { lockBodyScroll, unlockBodyScroll } from '@/components/modals/bodyScrollLock'
import type { ModalSize, ModalTone } from '@/stores/modal'

const props = withDefaults(
    defineProps<{
        title?: string
        tone?: ModalTone
        size?: ModalSize
        closable?: boolean
        closeOnBackdrop?: boolean
        closeOnEscape?: boolean
        isTopmost?: boolean
        zIndex?: number
    }>(),
    {
        title: '',
        tone: 'default',
        size: 'md',
        closable: true,
        closeOnBackdrop: true,
        closeOnEscape: true,
        isTopmost: true,
        zIndex: 500,
    },
)

const emit = defineEmits<{
    (event: 'close'): void
}>()
const slots = useSlots()

const panelFrameStyle = computed(() => {
    let minWidth = 420
    let maxWidth = 560

    switch (props.size) {
        case 'sm':
            minWidth = 320
            maxWidth = 420
            break
        case 'lg':
            minWidth = 560
            maxWidth = 720
            break
        case 'xl':
            minWidth = 720
            maxWidth = 920
            break
        default:
            break
    }

    return {
        width: 'fit-content',
        minWidth: `min(${minWidth}px, calc(100vw - 2rem))`,
        maxWidth: `min(${maxWidth}px, calc(100vw - 2rem))`,
    }
})

const shellClass = computed(() =>
    props.tone === 'danger'
        ? 'border-[#E3C8C8] bg-[#FAF9F6] text-[#2B221B]'
        : 'border-[#EAE6DE] bg-[#FAF9F6] text-[#2B221B]',
)

const backdropClass = computed(() => (props.tone === 'danger' ? 'bg-black/55' : 'bg-[#2B221B]/50'))

const closeButtonClass = computed(() =>
    props.tone === 'danger'
        ? 'text-[#9E5A5A] hover:bg-[#FFF2F2] hover:text-[#7B3434]'
        : 'text-[#8C857B] hover:bg-[#F2EEE7] hover:text-[#2B221B]',
)

const titleClass = computed(() => (props.tone === 'danger' ? 'text-[#2B221B]' : 'text-[#2B221B]'))

const rootStyle = computed(() => ({
    zIndex: props.zIndex,
}))

const hasCustomHeader = computed(() => Boolean(slots.header))
const shouldRenderDefaultHeader = computed(() => !hasCustomHeader.value && Boolean(props.title))
const shouldRenderHeader = computed(() => hasCustomHeader.value || shouldRenderDefaultHeader.value)

const requestClose = () => {
    if (!props.isTopmost || !props.closable) {
        return
    }

    emit('close')
}

const handleBackdropClick = () => {
    if (!props.isTopmost || !props.closeOnBackdrop) {
        return
    }

    emit('close')
}

const handleEscape = (event: KeyboardEvent) => {
    if (event.key !== 'Escape' || !props.isTopmost || !props.closeOnEscape) {
        return
    }

    event.preventDefault()
    emit('close')
}

onMounted(() => {
    lockBodyScroll()
    window.addEventListener('keydown', handleEscape)
})

onUnmounted(() => {
    window.removeEventListener('keydown', handleEscape)
    unlockBodyScroll()
})
</script>

<template>
    <Teleport to="body">
        <Transition
            appear
            enter-active-class="transition duration-300 ease-out"
            enter-from-class="opacity-0 translate-y-4"
            enter-to-class="opacity-100 translate-y-0"
            leave-active-class="transition duration-200 ease-in"
            leave-from-class="opacity-100 translate-y-0"
            leave-to-class="opacity-0 translate-y-4"
        >
            <div
                data-testid="app-modal-root"
                class="fixed inset-0"
                :class="isTopmost ? '' : 'pointer-events-none'"
                :style="rootStyle"
            >
                <div
                    data-testid="app-modal-backdrop"
                    class="absolute inset-0 transition-opacity duration-300"
                    :class="backdropClass"
                    @click="handleBackdropClick"
                ></div>

                <div class="absolute inset-0 flex items-center justify-center p-4 sm:p-6">
                    <div
                        class="relative max-w-full pointer-events-auto transition-[width,height] duration-300 ease-out"
                        :style="panelFrameStyle"
                    >
                        <div
                            class="absolute inset-0 bg-[#F0EEE6] shadow-md transform -rotate-2"
                        ></div>

                        <div
                            class="relative flex max-h-[min(85vh,720px)] flex-col overflow-hidden border shadow-[0_20px_40px_-12px_rgba(43,34,27,0.16)]"
                            :class="shellClass"
                        >
                            <div
                                v-if="shouldRenderHeader"
                                data-testid="app-modal-header"
                                class="flex items-start justify-between gap-4 border-b border-[#EAE6DE] px-8 py-6"
                            >
                                <slot v-if="hasCustomHeader" name="header" />

                                <div v-else class="min-w-0">
                                    <h2
                                        class="font-serif text-3xl tracking-wide"
                                        :class="titleClass"
                                    >
                                        {{ title }}
                                    </h2>
                                    <div class="mt-6 h-px w-full bg-[#2B221B]"></div>
                                </div>

                                <button
                                    v-if="closable"
                                    type="button"
                                    data-testid="app-modal-close"
                                    class="shrink-0 p-2 transition-colors"
                                    :class="closeButtonClass"
                                    @click="requestClose"
                                >
                                    <X :size="18" />
                                </button>
                            </div>

                            <div class="min-h-0 flex-1 overflow-y-auto px-8 py-8">
                                <slot />
                            </div>

                            <div v-if="$slots.footer" class="border-t border-[#EAE6DE] px-8 py-6">
                                <slot name="footer" />
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </Transition>
    </Teleport>
</template>
