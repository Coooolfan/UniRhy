<script setup lang="ts">
import { Play, Pause } from 'lucide-vue-next'

export type AlbumHeroData = {
    title: string
    artist: string
    year: string
    type: string
    description: string
    cover: string
}

defineProps<{
    albumData: AlbumHeroData
    isCdVisible: boolean
    hasPlayableRecording: boolean
    isCurrentPlaying: boolean
}>()

const emit = defineEmits<{
    (event: 'play'): void
}>()
</script>

<template>
    <div class="mt-8 flex flex-col md:flex-row gap-12 items-end mb-16">
        <div
            class="relative z-0 group shrink-0 w-64 h-64 md:w-80 md:h-80 select-none perspective-1000"
        >
            <div
                class="absolute top-2 right-2 bottom-2 left-2 bg-linear-to-tr from-gray-200 to-gray-100 rounded-full shadow-lg flex items-center justify-center transition-transform duration-2000 ease-out z-0"
                :class="isCdVisible ? 'translate-x-16 md:translate-x-24' : 'translate-x-0'"
            >
                <div class="w-1/3 h-1/3 border border-gray-300 rounded-full opacity-50"></div>
                <div
                    class="absolute w-8 h-8 bg-[#EBE7E0] rounded-full border border-gray-300"
                ></div>
            </div>

            <div
                class="relative w-full h-full shadow-xl rounded-sm overflow-hidden bg-[#2C2420] z-10"
            >
                <img
                    v-if="albumData.cover"
                    :src="albumData.cover"
                    alt="Album Cover"
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

        <div class="flex flex-col gap-4 pb-2 w-full relative z-10">
            <div class="flex items-center gap-3 text-sm tracking-wider uppercase text-[#8C857B]">
                <span>{{ albumData.type }}</span>
                <span class="w-8 h-px bg-[#C17D46]"></span>
                <span>{{ albumData.year }}</span>
            </div>

            <h1 class="text-5xl md:text-7xl font-serif text-[#2C2420] leading-tight">
                {{ albumData.title }}
            </h1>

            <div class="text-xl text-[#5E564D] font-serif italic mb-2">
                By {{ albumData.artist }}
            </div>

            <p class="text-sm text-[#8C857B] max-w-2xl line-clamp-3">
                {{ albumData.description }}
            </p>

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
