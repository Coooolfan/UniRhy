<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, useSlots, watch } from 'vue'
import { X } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
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
        open?: boolean
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
        open: true,
    },
)

const emit = defineEmits<{
    (event: 'close'): void
    (event: 'afterLeave'): void
}>()
const { t } = useI18n()
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
let isRedirectingFocus = false

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
        ? 'border-[#E3C8C8] bg-[#FBF8F4] text-[#2B221B]'
        : 'border-[#E7E2D6] bg-[#FAF9F6] text-[#2B221B]',
)

const backdropClass = computed(() =>
    props.tone === 'danger' ? 'bg-[#241414]/55' : 'bg-[#2B221B]/45',
)

const headerClass = computed(() =>
    props.tone === 'danger' ? 'border-[#EDD9D9]' : 'border-[#EAE6DE]',
)

const titleMarkClass = computed(() => (props.tone === 'danger' ? 'bg-[#B95D5D]' : 'bg-[#2B221B]'))

const closeButtonClass = computed(() =>
    props.tone === 'danger'
        ? 'text-[#9E5A5A] hover:text-[#7B3434] focus-visible:ring-[#9E5A5A]'
        : 'text-[#8A8A8A] hover:text-[#C27E46] focus-visible:ring-[#C27E46]',
)

const footerClass = computed(() =>
    props.tone === 'danger'
        ? 'border-[#EDD9D9] bg-[#F9F1EC]/70'
        : 'border-[#EAE6DE] bg-[#F5F2EA]/70',
)

const bodyClass = computed(() =>
    props.bodyPadding
        ? 'modal-body min-h-0 flex-1 overflow-y-auto px-7 py-6'
        : 'flex min-h-0 flex-1 overflow-hidden',
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

const focusElement = (element: HTMLElement) => {
    isRedirectingFocus = true
    try {
        element.focus({ preventScroll: true })
    } finally {
        isRedirectingFocus = false
    }
}

const focusModalContainer = () => {
    if (modalContainerRef.value) {
        focusElement(modalContainerRef.value)
    }
}

const focusFirstFocusableElement = () => {
    const focusableElements = getFocusableElements()
    const firstElement = focusableElements[0]

    if (firstElement) {
        focusElement(firstElement)
        return
    }

    focusModalContainer()
}

const focusLastFocusableElement = () => {
    const focusableElements = getFocusableElements()
    const [lastElement] = focusableElements.slice(-1)

    if (lastElement) {
        focusElement(lastElement)
        return
    }

    focusModalContainer()
}

const syncFocusToModal = async () => {
    if (!props.isTopmost) {
        return
    }

    await nextTick()
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
    if (!props.isTopmost || isRedirectingFocus) {
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
    () => props.open,
    (isOpen) => {
        if (!isOpen) {
            return
        }

        void syncFocusToModal()
    },
    { immediate: true, flush: 'post' },
)

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
        <Transition
            appear
            name="app-modal"
            @after-enter="void syncFocusToModal()"
            @after-leave="emit('afterLeave')"
        >
            <div
                v-if="open"
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
                        class="app-modal-panel pointer-events-auto relative max-w-full transition-[width,height,transform,translate,scale] duration-300 ease-out"
                        :class="isTopmost ? '' : 'translate-y-3 scale-[0.96]'"
                        :style="panelFrameStyle"
                    >
                        <div
                            aria-hidden="true"
                            class="absolute inset-0 translate-y-2 rotate-[0.75deg] border border-[#E3DED2] bg-[#F4F1E9]"
                        ></div>
                        <div
                            aria-hidden="true"
                            class="absolute inset-0 -rotate-1 bg-[#EDE9DE] shadow-md"
                        ></div>

                        <div
                            ref="modalContainerRef"
                            data-testid="app-modal-container"
                            tabindex="-1"
                            role="dialog"
                            aria-modal="true"
                            :aria-label="title || undefined"
                            class="relative flex max-h-[min(85vh,720px)] flex-col overflow-hidden border shadow-[0_24px_56px_-16px_rgba(43,34,27,0.28)]"
                            :class="shellClass"
                        >
                            <div
                                v-if="shouldRenderHeader"
                                data-testid="app-modal-header"
                                class="flex items-center gap-4 border-b px-7 py-5"
                                :class="headerClass"
                            >
                                <slot v-if="hasCustomHeader" name="header" />

                                <template v-else>
                                    <span
                                        aria-hidden="true"
                                        class="h-7 w-[3px] shrink-0"
                                        :class="titleMarkClass"
                                    ></span>
                                    <h2
                                        class="min-w-0 truncate font-serif text-xl tracking-[0.15em]"
                                    >
                                        {{ title }}
                                    </h2>
                                </template>

                                <button
                                    v-if="closable"
                                    type="button"
                                    data-testid="app-modal-close"
                                    class="ml-auto inline-flex h-8 w-8 shrink-0 items-center justify-center outline-none transition-colors focus-visible:ring-1 focus-visible:ring-offset-2"
                                    :class="closeButtonClass"
                                    :aria-label="t('common.close')"
                                    @click="requestClose"
                                >
                                    <X class="h-4 w-4" />
                                </button>
                            </div>

                            <div :class="bodyClass">
                                <slot />
                            </div>

                            <div
                                v-if="$slots.footer"
                                class="border-t px-7 py-5"
                                :class="footerClass"
                            >
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
.app-modal-enter-active {
    transition: opacity 240ms ease;
}

.app-modal-leave-active {
    transition: opacity 160ms ease-in;
}

.app-modal-enter-from,
.app-modal-leave-to {
    opacity: 0;
}

.app-modal-enter-active .app-modal-panel {
    transition:
        transform 340ms cubic-bezier(0.22, 1, 0.36, 1),
        opacity 240ms ease,
        width 300ms ease-out,
        height 300ms ease-out;
}

.app-modal-leave-active .app-modal-panel {
    transition:
        transform 180ms ease-in,
        opacity 160ms ease-in,
        width 300ms ease-out,
        height 300ms ease-out;
}

.app-modal-enter-from .app-modal-panel {
    opacity: 0;
    transform: translateY(18px) scale(0.965);
}

.app-modal-leave-to .app-modal-panel {
    opacity: 0;
    transform: translateY(10px) scale(0.98);
}

@media (prefers-reduced-motion: reduce) {
    .app-modal-enter-active,
    .app-modal-leave-active,
    .app-modal-enter-active .app-modal-panel,
    .app-modal-leave-active .app-modal-panel {
        transition: none;
    }
}

.modal-body {
    scrollbar-color: #d6d1c4 transparent;
    scrollbar-gutter: stable;
}

.modal-body::-webkit-scrollbar {
    width: 6px;
}

.modal-body::-webkit-scrollbar-track {
    background: transparent;
}

.modal-body::-webkit-scrollbar-thumb {
    background-color: #d6d1c4;
    border-radius: 3px;
}

.modal-body::-webkit-scrollbar-thumb:hover {
    background-color: #c0bab0;
}
</style>
