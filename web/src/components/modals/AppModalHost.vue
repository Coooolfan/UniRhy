<script setup lang="ts">
import { storeToRefs } from 'pinia'
import ModalStackItem from '@/components/modals/ModalStackItem.vue'
import { useModalStore } from '@/stores/modal'

const modalStore = useModalStore()
const { stack } = storeToRefs(modalStore)
</script>

<template>
    <ModalStackItem
        v-for="(entry, index) in stack"
        :key="entry.id"
        :entry="entry"
        :index="index"
        :is-topmost="index === stack.length - 1"
    />
</template>
