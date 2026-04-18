<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, useSlots, watch } from 'vue'
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
        bodyPadding?: boolean
        isTopmost?: boolean
        zIndex?: number
        fitContent?: boolean
    }>(),
    {
        title: '',
        tone: 'default',
        size: 'md',
        closable: true,
        closeOnBackdrop: true,
        closeOnEscape: true,
        bodyPadding: true,
        isTopmost: true,
        zIndex: 500,
        fitContent: true,
    },
)

const emit = defineEmits<{
    (event: 'close'): void
}>()
const slots = useSlots()
const modalContainerRef = ref<HTMLElement | null>(null)
const focusableSelector = [
    'a[href]',
    'area[href]',
    'button:not([disabled])',
    'input:not([disabled]):not([type="hidden"])',
    'select:not([disabled])',
    'textarea:not([disabled])',
    'summary',
    'iframe',
    'audio[controls]',
    'video[controls]',
    '[contenteditable="true"]',
    '[tabindex]:not([tabindex="-1"])',
].join(',')
let previouslyFocusedElement: HTMLElement | null = null

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
            maxWidth = 1152
            break
        default:
            break
    }

    return {
        width: props.fitContent ? 'fit-content' : '100%',
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

const bodyClass = computed(() =>
    props.bodyPadding
        ? 'min-h-0 flex-1 overflow-y-auto px-8 py-8'
        : 'min-h-0 flex-1 overflow-y-auto',
)

const rootStyle = computed(() => ({
    zIndex: props.zIndex,
}))

const hasCustomHeader = computed(() => Boolean(slots.header))
const shouldRenderDefaultHeader = computed(() => !hasCustomHeader.value && Boolean(props.title))
const shouldRenderHeader = computed(() => hasCustomHeader.value || shouldRenderDefaultHeader.value)

const isFocusableElement = (element: Element): element is HTMLElement => {
    if (!(element instanceof HTMLElement)) {
        return false
    }

    if (element.hidden || element.closest('[hidden], [aria-hidden="true"]')) {
        return false
    }

    const style = window.getComputedStyle(element)
    return style.display !== 'none' && style.visibility !== 'hidden'
}

const getFocusableElements = () => {
    const container = modalContainerRef.value
    if (!container) {
        return []
    }

    return Array.from(container.querySelectorAll(focusableSelector)).filter((element) =>
        isFocusableElement(element),
    )
}

const focusModalContainer = () => {
    modalContainerRef.value?.focus({ preventScroll: true })
}

const focusFirstFocusableElement = () => {
    const focusableElements = getFocusableElements()
    const firstElement = focusableElements[0]

    if (firstElement) {
        firstElement.focus({ preventScroll: true })
        return
    }

    focusModalContainer()
}

const focusLastFocusableElement = () => {
    const focusableElements = getFocusableElements()
    const [lastElement] = focusableElements.slice(-1)

    if (lastElement) {
        lastElement.focus({ preventScroll: true })
        return
    }

    focusModalContainer()
}

const syncFocusToModal = async () => {
    if (!props.isTopmost) {
        return
    }

    await nextTick()

    const container = modalContainerRef.value
    if (!container) {
        return
    }

    const activeElement = document.activeElement
    if (activeElement instanceof HTMLElement && container.contains(activeElement)) {
        return
    }

    focusFirstFocusableElement()
}

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

const handleTab = (event: KeyboardEvent) => {
    if (event.key !== 'Tab' || !props.isTopmost) {
        return
    }

    const container = modalContainerRef.value
    if (!container) {
        return
    }

    const focusableElements = getFocusableElements()
    if (focusableElements.length === 0) {
        event.preventDefault()
        focusModalContainer()
        return
    }

    const firstElement = focusableElements[0]
    const [lastElement] = focusableElements.slice(-1)
    const activeElement = document.activeElement

    if (activeElement === container) {
        event.preventDefault()

        if (event.shiftKey) {
            focusLastFocusableElement()
            return
        }

        focusFirstFocusableElement()
        return
    }

    if (!(activeElement instanceof HTMLElement) || !container.contains(activeElement)) {
        event.preventDefault()

        if (event.shiftKey) {
            focusLastFocusableElement()
            return
        }

        focusFirstFocusableElement()
        return
    }

    if (!event.shiftKey && activeElement === lastElement) {
        event.preventDefault()
        focusFirstFocusableElement()
        return
    }

    if (event.shiftKey && activeElement === firstElement) {
        event.preventDefault()
        focusLastFocusableElement()
    }
}

const handleFocusIn = (event: FocusEvent) => {
    if (!props.isTopmost) {
        return
    }

    const container = modalContainerRef.value
    const target = event.target

    if (!container || !(target instanceof Node) || container.contains(target)) {
        return
    }

    focusFirstFocusableElement()
}

onMounted(() => {
    previouslyFocusedElement =
        document.activeElement instanceof HTMLElement ? document.activeElement : null
    lockBodyScroll()
    window.addEventListener('keydown', handleEscape)
    window.addEventListener('keydown', handleTab)
    document.addEventListener('focusin', handleFocusIn)
    void syncFocusToModal()
})

watch(
    () => props.isTopmost,
    (isTopmost) => {
        if (!isTopmost) {
            return
        }

        void syncFocusToModal()
    },
)

onUnmounted(() => {
    window.removeEventListener('keydown', handleEscape)
    window.removeEventListener('keydown', handleTab)
    document.removeEventListener('focusin', handleFocusIn)
    unlockBodyScroll()

    if (props.isTopmost && previouslyFocusedElement?.isConnected) {
        previouslyFocusedElement.focus({ preventScroll: true })
    }
})
</script>

<template>
    <Teleport to="body">
        <Transition appear name="app-modal">
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
                        class="app-modal-panel relative max-w-full pointer-events-auto transition-[width,height] duration-300 ease-out"
                        :style="panelFrameStyle"
                    >
                        <div
                            class="absolute inset-0 bg-[#F0EEE6] shadow-md transform -rotate-2"
                        ></div>

                        <div
                            ref="modalContainerRef"
                            data-testid="app-modal-container"
                            tabindex="-1"
                            role="dialog"
                            aria-modal="true"
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

                            <div :class="bodyClass">
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

<style scoped>
.app-modal-enter-active,
.app-modal-leave-active {
    transition: opacity 300ms ease;
}

.app-modal-leave-active {
    transition-duration: 200ms;
}

.app-modal-enter-from,
.app-modal-leave-to {
    opacity: 0;
}

.app-modal-enter-active .app-modal-panel,
.app-modal-leave-active .app-modal-panel {
    transition:
        transform 300ms ease,
        opacity 300ms ease,
        width 300ms ease-out,
        height 300ms ease-out;
}

.app-modal-leave-active .app-modal-panel {
    transition-duration: 200ms;
}

.app-modal-enter-from .app-modal-panel,
.app-modal-leave-to .app-modal-panel {
    opacity: 0;
    transform: translateY(1rem);
}
</style>
