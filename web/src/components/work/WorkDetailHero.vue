<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { Pencil, Play, Pause } from 'lucide-vue-next'
import StackedCovers from '@/components/StackedCovers.vue'

const { t } = useI18n()

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
    canEdit?: boolean
}>()

const emit = defineEmits<{
    (event: 'play'): void
    (event: 'edit-work'): void
}>()
</script>

<template>
    <div
        class="group mb-7 mt-5 flex flex-col items-center gap-4 sm:mb-12 sm:mt-6 sm:gap-8 md:mb-16 md:mt-8 md:flex-row md:items-end md:gap-12 lg:gap-24"
    >
        <div class="h-44 w-44 shrink-0 sm:mt-2 sm:h-64 sm:w-64 md:ml-8 md:mt-4 md:h-80 md:w-80">
            <StackedCovers :items="recordings" :default-cover="workData.cover" />
        </div>

        <div
            class="relative z-10 flex w-full flex-col gap-2 pb-0 text-center sm:gap-4 sm:pb-2 md:text-left"
        >
            <div
                class="flex flex-wrap items-center justify-center gap-3 text-[11px] tracking-wider uppercase text-[#8C857B] sm:text-sm md:justify-start"
            >
                <span>Musical Work</span>
                <button
                    v-if="canEdit"
                    class="cursor-pointer p-1 text-[#8C857B] opacity-100 transition-all hover:text-[#C17D46] md:opacity-0 md:group-hover:opacity-100"
                    :title="t('workHero.editWork')"
                    @click="emit('edit-work')"
                >
                    <Pencil :size="14" />
                </button>
            </div>

            <h1 class="font-serif text-3xl leading-tight text-[#2C2420] sm:text-5xl md:text-7xl">
                {{ workData.title }}
            </h1>

            <div class="font-serif text-[15px] italic text-[#5E564D] sm:mb-2 sm:text-xl">
                {{ workData.artist }}
            </div>

            <div class="mt-1 flex items-center justify-center gap-4 sm:mt-4 md:justify-start">
                <button
                    class="flex w-auto items-center justify-center gap-2 rounded-sm border border-[#C17D46] px-4 py-2 text-xs font-medium tracking-widest text-[#C17D46] uppercase transition-all duration-300 hover:bg-[#C17D46] hover:text-white disabled:cursor-not-allowed disabled:opacity-50 disabled:hover:bg-transparent disabled:hover:text-[#C17D46] sm:px-8 sm:py-3 sm:text-sm"
                    :disabled="!hasPlayableRecording"
                    @click="emit('play')"
                >
                    <Pause v-if="isCurrentPlaying" :size="16" />
                    <Play v-else :size="16" fill="currentColor" />
                    {{ isCurrentPlaying ? t('workHero.pausePlayback') : t('workHero.playNow') }}
                </button>
            </div>
        </div>
    </div>
</template>
