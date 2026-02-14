<script setup lang="ts">
import { computed } from 'vue'

interface CoverItem {
    id: number | string
    cover?: string
}

const props = defineProps<{
    items: CoverItem[]
    defaultCover?: string
    isSelected?: boolean
}>()

const displayCovers = computed(() => {
    if (props.items.length === 0) {
        return props.defaultCover ? [{ id: -1, cover: props.defaultCover }] : []
    }
    const count = Math.min(props.items.length, 3)
    return props.items.slice(0, count).map((item) => ({
        id: item.id,
        cover: item.cover || props.defaultCover,
    }))
})

const getStackStyle = (index: number, total: number) => {
    // Stacked State (Default)
    let base = ''
    if (total === 1) {
        base = 'rotate-0 translate-x-0'
    } else {
        if (index === 0) base = 'rotate-0 translate-x-0'
        else if (index === 1) base = 'rotate-3 translate-x-[4%]'
        else base = 'rotate-6 translate-x-[8%]'
    }

    // Hover State (Spread)
    let hover = ''
    if (total === 1) {
        hover = 'group-hover:scale-105'
    } else if (total === 2) {
        if (index === 0) hover = 'group-hover:-translate-x-[15%] group-hover:-rotate-6'
        if (index === 1) hover = 'group-hover:translate-x-[15%] group-hover:rotate-6'
    } else {
        if (index === 0) hover = 'group-hover:-translate-x-[25%] group-hover:-rotate-12'
        if (index === 1)
            hover = 'group-hover:translate-x-0 group-hover:rotate-0 group-hover:-translate-y-[5%]'
        if (index === 2) hover = 'group-hover:translate-x-[25%] group-hover:rotate-12'
    }

    return `${base} ${hover}`
}
</script>

<template>
    <div class="relative z-0 group shrink-0 w-full h-full select-none perspective-1000">
        <div
            v-for="(item, index) in displayCovers"
            :key="item.id"
            class="absolute inset-0 shadow-2xl bg-white transition-all duration-500 ease-out"
            :class="getStackStyle(index, displayCovers.length)"
            :style="{ zIndex: (displayCovers.length - index) * 10 }"
        >
            <!-- Bookmark for Top Item -->
            <div
                v-if="index === 0 && isSelected"
                class="absolute -top-1.5 left-4 z-30 w-8 drop-shadow-lg"
            >
                <!-- this svg code by human -->
                <svg
                    viewBox="0 0 32 64"
                    fill="none"
                    xmlns="http://www.w3.org/2000/svg"
                    class="w-full h-full drop-shadow-xl"
                >
                    <path d="M0 0H20V36L12 30L0 36V0Z" fill="#d7b472" />
                    <path d="M20 0V6H26Z" fill="#979185" />
                </svg>
            </div>

            <!-- Content Wrapper (Clipped) -->
            <div class="absolute inset-0 overflow-hidden w-full h-full">
                <img
                    v-if="item.cover"
                    :src="item.cover"
                    alt="Cover"
                    class="w-full h-full object-cover"
                />
                <div
                    v-else
                    class="w-full h-full flex items-center justify-center bg-gray-100 text-gray-400"
                >
                    <span class="text-xs">No Cover</span>
                </div>
            </div>
        </div>
    </div>
</template>
