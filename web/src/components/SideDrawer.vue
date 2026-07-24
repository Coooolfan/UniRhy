<script setup lang="ts">
import { onBeforeUnmount, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { X } from 'lucide-vue-next'

const { t } = useI18n()

const props = defineProps<{
    open: boolean
    title?: string
    width?: string
    maxWidth?: string
    /** 扩展模式：占据更大宽度，用于展示列表 + 画布的组合内容 */
    expanded?: boolean
}>()

const emit = defineEmits<{
    'update:open': [value: boolean]
    'update:expanded': [value: boolean]
}>()

const close = () => {
    emit('update:open', false)
}

const onKeydown = (event: KeyboardEvent) => {
    if (event.key === 'Escape' && props.open) {
        if (props.expanded) {
            emit('update:expanded', false)
        } else {
            close()
        }
    }
}

watch(
    () => props.open,
    (open) => {
        if (open) {
            document.addEventListener('keydown', onKeydown)
        } else {
            document.removeEventListener('keydown', onKeydown)
        }
    },
    { immediate: true },
)

onBeforeUnmount(() => {
    document.removeEventListener('keydown', onKeydown)
})
</script>

<template>
    <Teleport to="body">
        <Transition
            enter-active-class="transition-opacity duration-200 ease-out"
            enter-from-class="opacity-0"
            enter-to-class="opacity-100"
            leave-active-class="transition-opacity duration-150 ease-in"
            leave-from-class="opacity-100"
            leave-to-class="opacity-0"
        >
            <div v-if="open" class="fixed inset-0 z-[60] bg-black/55" @click="close" />
        </Transition>
        <Transition
            enter-active-class="transition-transform duration-250 ease-out"
            enter-from-class="translate-x-full"
            enter-to-class="translate-x-0"
            leave-active-class="transition-transform duration-200 ease-in"
            leave-from-class="translate-x-0"
            leave-to-class="translate-x-full"
        >
            <aside
                v-if="open"
                class="fixed top-0 right-0 bottom-0 z-[61] flex flex-col border-l border-[#EAE6DE] bg-[#FFFCF5] shadow-[0_8px_30px_rgba(0,0,0,0.18)] transition-[width] duration-300 ease-out"
                :style="
                    expanded
                        ? { width: '90vw', maxWidth: '100vw' }
                        : { width: width ?? '32rem', maxWidth: maxWidth ?? '95vw' }
                "
            >
                <header
                    class="flex items-center justify-between border-b border-[#EAE6DE] px-4 pb-4 pt-[max(2rem,env(safe-area-inset-top))] sm:px-6"
                >
                    <h3 class="font-serif text-xl text-[#2B221B]">
                        {{ title }}
                    </h3>
                    <div class="flex items-center gap-1">
                        <slot name="header-actions" />
                        <button
                            type="button"
                            class="p-1.5 text-[#8A8A8A] transition-colors hover:text-[#B86134]"
                            :aria-label="t('common.close')"
                            @click="close"
                        >
                            <X class="h-4 w-4" />
                        </button>
                    </div>
                </header>
                <div
                    class="min-h-0 flex-1"
                    :class="expanded ? 'flex overflow-hidden' : 'overflow-y-auto'"
                >
                    <slot />
                </div>
            </aside>
        </Transition>
    </Teleport>
</template>
