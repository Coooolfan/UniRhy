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
    <div class="mt-8 flex flex-col md:flex-row gap-12 md:gap-32 items-end mb-16 group">
        <div class="ml-8 mt-4 w-64 h-64 md:w-80 md:h-80 shrink-0">
            <StackedCovers :items="recordings" :default-cover="workData.cover" />
        </div>

        <div class="flex flex-col gap-4 pb-2 w-full relative z-10">
            <div class="flex items-center gap-3 text-sm tracking-wider uppercase text-[#8C857B]">
                <span>Musical Work</span>
                <button
                    class="p-1 text-[#8C857B] hover:text-[#C17D46] transition-all opacity-0 group-hover:opacity-100 cursor-pointer"
                    title="编辑作品"
                    @click="emit('edit-work')"
                >
                    <Pencil :size="14" />
                </button>
            </div>

            <h1 class="text-5xl md:text-7xl font-serif text-[#2C2420] leading-tight">
                {{ workData.title }}
            </h1>

            <div class="text-xl text-[#5E564D] font-serif italic mb-2">
                Originally by {{ workData.artist }}
            </div>

            <div class="flex items-center gap-4 mt-4">
                <button
                    class="px-8 py-3 border border-[#C17D46] text-[#C17D46] hover:bg-[#C17D46] hover:text-white disabled:opacity-50 disabled:cursor-not-allowed disabled:hover:bg-transparent disabled:hover:text-[#C17D46] transition-all duration-300 flex items-center gap-2 text-sm tracking-widest uppercase font-medium rounded-sm cursor-pointer"
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
