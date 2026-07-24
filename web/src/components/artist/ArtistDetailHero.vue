<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { Pause, Pencil, Play, UserRound } from 'lucide-vue-next'

const { t } = useI18n()

export type ArtistHeroData = {
    name: string
    aliases: string
    comment: string
    avatar: string
}

defineProps<{
    artistData: ArtistHeroData
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
            class="relative h-44 w-44 shrink-0 select-none overflow-hidden rounded-full bg-[#EFEAE2] shadow-xl sm:h-56 sm:w-56 md:h-64 md:w-64"
        >
            <img
                v-if="artistData.avatar"
                :src="artistData.avatar"
                :alt="artistData.name"
                class="h-full w-full object-cover"
            />
            <div v-else class="flex h-full w-full items-center justify-center text-[#B0AAA0]">
                <UserRound class="h-1/2 w-1/2" />
            </div>
        </div>

        <div
            class="relative z-10 flex w-full flex-col gap-2 pb-0 text-center sm:gap-4 sm:pb-2 md:text-left"
        >
            <div
                class="flex flex-wrap items-center justify-center gap-3 text-[11px] tracking-wider uppercase text-[#8C857B] sm:text-sm md:justify-start"
            >
                <span>{{ t('artistDetail.artistLabel') }}</span>
                <button
                    v-if="canEdit"
                    class="cursor-pointer p-1 text-[#8C857B] opacity-100 transition-all hover:text-[#C17D46] md:opacity-0 md:group-hover:opacity-100"
                    :title="t('artistDetail.editArtist')"
                    @click="emit('edit')"
                >
                    <Pencil :size="14" />
                </button>
            </div>

            <h1 class="font-serif text-3xl leading-tight text-[#2C2420] sm:text-5xl md:text-6xl">
                {{ artistData.name }}
            </h1>

            <div
                v-if="artistData.aliases"
                class="font-serif text-[15px] italic text-[#5E564D] sm:mb-2 sm:text-xl"
            >
                {{ artistData.aliases }}
            </div>

            <p
                v-if="artistData.comment"
                class="mx-auto line-clamp-2 max-w-2xl text-xs text-[#8C857B] sm:line-clamp-3 sm:text-sm md:mx-0"
            >
                {{ artistData.comment }}
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
