<script setup lang="ts">
import { computed, provide } from 'vue'
import BaseModalShell from '@/components/modals/BaseModalShell.vue'
import { modalContextKey, type ModalContext } from '@/components/modals/modalContext'
import { useModalStore, type ModalEntry } from '@/stores/modal'

const props = defineProps<{
    entry: ModalEntry
    index: number
    isTopmost: boolean
}>()

const modalStore = useModalStore()

const zIndex = computed(() => 500 + props.index * 20)

const context: ModalContext = {
    close: () => modalStore.closeById(props.entry.id),
    resolve: (value) => modalStore.resolveById(props.entry.id, value),
    get isTopmost() {
        return props.isTopmost
    },
}

provide(modalContextKey, context)
</script>

<template>
    <BaseModalShell
        :title="entry.title"
        :tone="entry.tone"
        :size="entry.size"
        :closable="entry.closable"
        :close-on-backdrop="entry.closeOnBackdrop"
        :close-on-escape="entry.closeOnEscape"
        :body-padding="entry.bodyPadding"
        :fit-content="entry.fitContent"
        :is-topmost="isTopmost"
        :z-index="zIndex"
        @close="modalStore.closeById(entry.id)"
    >
        <component :is="entry.component" v-bind="entry.props" />
    </BaseModalShell>
</template>
