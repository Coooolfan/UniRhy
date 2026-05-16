<script setup lang="ts">
import { storeToRefs } from 'pinia'
import ModalStackItem from '@/components/modals/ModalStackItem.vue'
import { useModalStore } from '@/stores/modal'

const modalStore = useModalStore()
const { renderedStack, stack } = storeToRefs(modalStore)
</script>

<template>
    <ModalStackItem
        v-for="(entry, index) in renderedStack"
        :key="entry.id"
        :entry="entry"
        :index="index"
        :is-topmost="!entry.isClosing && entry.id === stack[stack.length - 1]?.id"
    />
</template>
