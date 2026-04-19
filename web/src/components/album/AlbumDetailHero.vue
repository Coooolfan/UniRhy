<script setup lang="ts">
import { Pause, Pencil, Play } from 'lucide-vue-next'

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
    (event: 'edit'): void
}>()
</script>

<template>
    <div
        class="group mb-12 mt-6 flex flex-col items-center gap-8 md:mb-16 md:mt-8 md:flex-row md:items-end md:gap-12"
    >
        <div
            class="relative z-0 group h-56 w-56 shrink-0 select-none perspective-1000 sm:h-64 sm:w-64 md:h-80 md:w-80"
        >
            <div
                class="absolute top-2 right-2 bottom-2 left-2 bg-linear-to-tr from-gray-200 to-gray-100 rounded-full shadow-lg flex items-center justify-center transition-transform duration-2000 ease-out z-0"
                :class="
                    isCdVisible
                        ? 'translate-x-12 sm:translate-x-16 md:translate-x-24'
                        : 'translate-x-0'
                "
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

        <div class="relative z-10 flex w-full flex-col gap-4 pb-2 text-center md:text-left">
            <div
                class="flex flex-wrap items-center justify-center gap-3 text-sm tracking-wider uppercase text-[#8C857B] md:justify-start"
            >
                <span>{{ albumData.type }}</span>
                <span class="w-8 h-px bg-[#C17D46]"></span>
                <span>{{ albumData.year }}</span>
                <button
                    class="cursor-pointer p-1 text-[#8C857B] opacity-100 transition-all hover:text-[#C17D46] md:opacity-0 md:group-hover:opacity-100"
                    title="编辑专辑"
                    @click="emit('edit')"
                >
                    <Pencil :size="14" />
                </button>
            </div>

            <h1 class="font-serif text-4xl leading-tight text-[#2C2420] sm:text-5xl md:text-7xl">
                {{ albumData.title }}
            </h1>

            <div class="mb-2 font-serif text-lg italic text-[#5E564D] sm:text-xl">
                By {{ albumData.artist }}
            </div>

            <p class="mx-auto max-w-2xl text-sm text-[#8C857B] line-clamp-3 md:mx-0">
                {{ albumData.description }}
            </p>

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
