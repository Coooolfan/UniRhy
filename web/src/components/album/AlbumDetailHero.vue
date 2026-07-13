<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { Pause, Pencil, Play } from 'lucide-vue-next'

const { t } = useI18n()

export type AlbumHeroData = {
    title: string
    artist: string
    year: string
    description: string
    cover: string
}

defineProps<{
    albumData: AlbumHeroData
    isCdVisible: boolean
    hasPlayableRecording: boolean
    isCurrentPlaying: boolean
    canEdit?: boolean
}>()

const emit = defineEmits<{
    (event: 'play'): void
    (event: 'edit'): void
}>()
</script>

<template>
    <div
        class="group mb-7 mt-5 flex flex-col items-center gap-4 sm:mb-12 sm:mt-6 sm:gap-8 md:mb-16 md:mt-8 md:flex-row md:items-end md:gap-12"
    >
        <div
            class="group perspective-1000 relative z-0 h-44 w-44 shrink-0 select-none sm:h-64 sm:w-64 md:h-80 md:w-80"
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

        <div
            class="relative z-10 flex w-full flex-col gap-2 pb-0 text-center sm:gap-4 sm:pb-2 md:text-left"
        >
            <div
                class="flex flex-wrap items-center justify-center gap-3 text-[11px] tracking-wider uppercase text-[#8C857B] sm:text-sm md:justify-start"
            >
                <span v-if="albumData.year">{{ albumData.year }}</span>
                <button
                    v-if="canEdit"
                    class="cursor-pointer p-1 text-[#8C857B] opacity-100 transition-all hover:text-[#C17D46] md:opacity-0 md:group-hover:opacity-100"
                    :title="t('albumHero.editAlbum')"
                    @click="emit('edit')"
                >
                    <Pencil :size="14" />
                </button>
            </div>

            <h1 class="font-serif text-3xl leading-tight text-[#2C2420] sm:text-5xl md:text-7xl">
                {{ albumData.title }}
            </h1>

            <div class="font-serif text-[15px] italic text-[#5E564D] sm:mb-2 sm:text-xl">
                By {{ albumData.artist }}
            </div>

            <p
                class="mx-auto line-clamp-2 max-w-2xl text-xs text-[#8C857B] sm:line-clamp-3 sm:text-sm md:mx-0"
            >
                {{ albumData.description }}
            </p>

            <div class="mt-1 flex items-center justify-center gap-4 sm:mt-4 md:justify-start">
                <button
                    class="flex w-auto items-center justify-center gap-2 rounded-sm border border-[#C17D46] px-4 py-2 text-xs font-medium tracking-widest text-[#C17D46] uppercase transition-all duration-300 hover:bg-[#C17D46] hover:text-white disabled:cursor-not-allowed disabled:opacity-50 disabled:hover:bg-transparent disabled:hover:text-[#C17D46] sm:px-8 sm:py-3 sm:text-sm"
                    :disabled="!hasPlayableRecording"
                    @click="emit('play')"
                >
                    <Pause v-if="isCurrentPlaying" :size="16" />
                    <Play v-else :size="16" fill="currentColor" />
                    {{ isCurrentPlaying ? t('albumHero.pausePlayback') : t('albumHero.playNow') }}
                </button>
            </div>
        </div>
    </div>
</template>
