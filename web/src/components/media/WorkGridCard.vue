<script setup lang="ts">
import { Pause, Play } from 'lucide-vue-next'
import StackedCovers from '@/components/StackedCovers.vue'

type CoverItem = {
    id: number | string
    cover?: string
}

const props = withDefaults(
    defineProps<{
        title: string
        subtitle: string
        details: string
        cover?: string
        stackedImages?: CoverItem[]
        isSelected?: boolean
        selectable?: boolean
        showPlayButton?: boolean
        playLoading?: boolean
        isPlaying?: boolean
    }>(),
    {
        cover: '',
        stackedImages: () => [],
        isSelected: false,
        selectable: false,
        showPlayButton: true,
        playLoading: false,
        isPlaying: false,
    },
)

const emit = defineEmits<{
    (e: 'open'): void
    (e: 'play'): void
    (e: 'toggle-select'): void
}>()
</script>

<template>
    <div class="group cursor-pointer" @click="emit('open')">
        <div class="relative aspect-square mb-5 transition-all duration-300 ease-out">
            <div class="relative w-full h-full">
                <StackedCovers
                    :items="stackedImages"
                    :default-cover="cover"
                    :is-selected="isSelected"
                />

                <button
                    v-if="selectable"
                    type="button"
                    aria-label="选择作品"
                    class="absolute top-0 left-0 w-1/2 h-1/2 z-30 cursor-pointer"
                    @click.stop="emit('toggle-select')"
                ></button>

                <button
                    v-if="showPlayButton"
                    class="absolute bottom-4 right-4 w-10 h-10 bg-white/90 rounded-full shadow-lg flex items-center justify-center text-[#2C2420] opacity-0 group-hover:opacity-100 transition-all duration-300 hover:scale-110 z-20"
                    @click.stop="emit('play')"
                >
                    <Play v-if="playLoading" :size="16" class="animate-pulse" fill="currentColor" />
                    <Pause v-else-if="isPlaying" :size="16" fill="currentColor" />
                    <Play v-else :size="16" fill="currentColor" class="ml-0.5" />
                </button>
            </div>
        </div>

        <div class="text-center md:text-left">
            <h3
                class="font-serif text-lg leading-tight mb-1 truncate text-[#1A1A1A] group-hover:text-[#C27E46] transition-colors"
            >
                {{ title }}
            </h3>
            <p class="text-xs text-[#8C857B] uppercase tracking-wider truncate">
                {{ subtitle }}
            </p>
            <p class="text-[10px] text-[#B0AAA0] mt-1">{{ details }}</p>
        </div>
    </div>
</template>
