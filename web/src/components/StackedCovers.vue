<script setup lang="ts">
import { computed } from 'vue'

interface CoverItem {
    id: number | string
    cover?: string
}

const props = defineProps<{
    items: CoverItem[]
    defaultCover?: string
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
        if (index === 0) base = '-rotate-3 translate-x-0'
        else if (index === 1) base = 'rotate-6 translate-x-[5%]'
        else base = 'rotate-12 translate-x-[10%]'
    }

    // Hover State (Spread)
    let hover = ''
    if (total === 1) {
        hover = 'group-hover:scale-105'
    } else if (total === 2) {
        if (index === 0) hover = 'group-hover:-translate-x-[15%] group-hover:-rotate-12'
        if (index === 1) hover = 'group-hover:translate-x-[15%] group-hover:rotate-12'
    } else {
        if (index === 0) hover = 'group-hover:-translate-x-[30%] group-hover:-rotate-12'
        if (index === 1)
            hover = 'group-hover:translate-x-0 group-hover:rotate-0 group-hover:-translate-y-[5%]'
        if (index === 2) hover = 'group-hover:translate-x-[30%] group-hover:rotate-12'
    }

    return `${base} ${hover}`
}
</script>

<template>
    <div class="relative z-0 group shrink-0 w-full h-full select-none perspective-1000">
        <div
            v-for="(item, index) in displayCovers"
            :key="item.id"
            class="absolute inset-0 shadow-xl rounded-sm overflow-hidden bg-[#2C2420] transition-all duration-500 ease-out border border-[#EFEAE2]"
            :class="getStackStyle(index, displayCovers.length)"
            :style="{ zIndex: (displayCovers.length - index) * 10 }"
        >
            <img
                v-if="item.cover"
                :src="item.cover"
                alt="Cover"
                class="w-full h-full object-cover"
            />
            <div
                v-else
                class="w-full h-full flex items-center justify-center bg-[#2C2420] text-[#8C857B]"
            >
                <span class="text-xs">No Cover</span>
            </div>
        </div>
    </div>
</template>
