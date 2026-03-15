<script setup lang="ts">
import { Pencil, Play, Pause } from 'lucide-vue-next'
import StackedCovers from '@/components/StackedCovers.vue'

export type WorkHeroData = {
    title: string
    artist: string
    cover: string
}

export type WorkHeroRecording = {
    id: number
    cover: string
}

defineProps<{
    workData: WorkHeroData
    recordings: WorkHeroRecording[]
    hasPlayableRecording: boolean
    isCurrentPlaying: boolean
}>()

const emit = defineEmits<{
    (event: 'play'): void
    (event: 'edit-work'): void
}>()
</script>

<template>
    <div
        class="group mb-12 mt-6 flex flex-col items-center gap-8 md:mb-16 md:mt-8 md:flex-row md:items-end md:gap-12 lg:gap-24"
    >
        <div class="mt-2 h-56 w-56 shrink-0 sm:h-64 sm:w-64 md:ml-8 md:mt-4 md:h-80 md:w-80">
            <StackedCovers :items="recordings" :default-cover="workData.cover" />
        </div>

        <div class="relative z-10 flex w-full flex-col gap-4 pb-2 text-center md:text-left">
            <div
                class="flex flex-wrap items-center justify-center gap-3 text-sm tracking-wider uppercase text-[#8C857B] md:justify-start"
            >
                <span>Musical Work</span>
                <button
                    class="cursor-pointer p-1 text-[#8C857B] opacity-100 transition-all hover:text-[#C17D46] md:opacity-0 md:group-hover:opacity-100"
                    title="编辑作品"
                    @click="emit('edit-work')"
                >
                    <Pencil :size="14" />
                </button>
            </div>

            <h1 class="font-serif text-4xl leading-tight text-[#2C2420] sm:text-5xl md:text-7xl">
                {{ workData.title }}
            </h1>

            <div class="mb-2 font-serif text-lg italic text-[#5E564D] sm:text-xl">
                Originally by {{ workData.artist }}
            </div>

            <div class="mt-4 flex items-center justify-center gap-4 md:justify-start">
                <button
                    class="flex w-full items-center justify-center gap-2 rounded-sm border border-[#C17D46] px-6 py-3 text-sm font-medium tracking-widest text-[#C17D46] uppercase transition-all duration-300 hover:bg-[#C17D46] hover:text-white disabled:cursor-not-allowed disabled:opacity-50 disabled:hover:bg-transparent disabled:hover:text-[#C17D46] sm:w-auto sm:px-8"
                    :disabled="!hasPlayableRecording"
                    @click="emit('play')"
                >
                    <Pause v-if="isCurrentPlaying" :size="16" />
                    <Play v-else :size="16" fill="currentColor" />
                    {{ isCurrentPlaying ? '暂停播放' : '立即播放' }}
                </button>
            </div>
        </div>
    </div>
</template>
