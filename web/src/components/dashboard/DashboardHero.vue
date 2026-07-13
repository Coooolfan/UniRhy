<script setup lang="ts">
import { Pause, Play } from 'lucide-vue-next'
import { featuredAlbum as defaultFeaturedAlbum } from './data'
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { api } from '@/ApiInstance'
import { resolveArtistName, resolveCover, type RecordingAsset } from '@/composables/recordingMedia'
import { useAudioStore } from '@/stores/audio'
import { useUserStore } from '@/stores/user'
import {
    hasPlayableRecordingCandidate,
    resolvePlaybackTrackFromCandidate,
    type RecordingPlaybackCandidate,
} from '@/services/recordingPlaybackResolver'

type Album = {
    title: string
    recording: RecordingPlaybackCandidate
}

type FeaturedStatus = 'loading' | 'ready' | 'empty'

const { t } = useI18n()
const router = useRouter()
const album = ref<Album | null>(null)
const featuredStatus = ref<FeaturedStatus>('loading')
const audioStore = useAudioStore()
const userStore = useUserStore()
const featuredWork = ref<Awaited<ReturnType<typeof api.workController.randomWork>> | null>(null)

const isFeaturedPlaying = computed(() => {
    return (
        featuredStatus.value === 'ready' &&
        audioStore.isPlaying &&
        audioStore.currentTrack?.id === album.value?.recording.id
    )
})

const hasPlayableFeatured = computed(() => {
    return (
        featuredStatus.value === 'ready' &&
        !!album.value &&
        hasPlayableRecordingCandidate(album.value.recording)
    )
})

const featuredActionLabel = computed(() => {
    if (featuredStatus.value === 'ready') {
        return isFeaturedPlaying.value
            ? t('dashboardHero.pausePlayback')
            : t('dashboardHero.playNow')
    }
    if (featuredStatus.value === 'empty' && userStore.isAdmin) {
        return t('dashboardHero.goToSettings')
    }
    return t('dashboardHero.loading')
})

const compactFeaturedActionLabel = computed(() => {
    if (featuredStatus.value === 'ready') {
        return isFeaturedPlaying.value ? t('dashboardHero.pause') : t('dashboardHero.play')
    }
    return featuredActionLabel.value
})

const isFeaturedActionDisabled = computed(() => featuredStatus.value === 'loading')
const showFeaturedAction = computed(
    () =>
        featuredStatus.value === 'ready' || userStore.isAdmin || featuredStatus.value === 'loading',
)

const navigateToSettings = () => {
    router.push({ name: 'settings' })
}

const handleFeaturedAction = () => {
    if (featuredStatus.value === 'empty') {
        navigateToSettings()
        return
    }
    if (featuredStatus.value !== 'ready' || !album.value) {
        return
    }
    void resolvePlaybackTrackFromCandidate(album.value.recording).then((track) => {
        if (!track) {
            return
        }
        audioStore.play(track)
    })
}

const applyFeaturedWork = () => {
    const work = featuredWork.value
    if (!work || work.recordings.length === 0) {
        featuredStatus.value = 'empty'
        album.value = null
        return
    }

    const defaultRecording = work.recordings.find((recording) => recording.defaultInWork)
    const featuredRecording = defaultRecording ?? work.recordings[0]
    if (!featuredRecording) {
        featuredStatus.value = 'empty'
        album.value = null
        return
    }

    const recording: RecordingPlaybackCandidate = {
        id: featuredRecording.id,
        title: work.title,
        artist: resolveArtistName(featuredRecording.artists),
        cover: featuredRecording.cover?.url
            ? resolveCover(featuredRecording.cover)
            : defaultFeaturedAlbum.cover,
        assets: (featuredRecording.assets || []) as readonly RecordingAsset[],
        defaultInWork: featuredRecording.defaultInWork,
        workId: work.id,
    }

    album.value = {
        title: work.title,
        recording,
    }
    featuredStatus.value = hasPlayableRecordingCandidate(recording) ? 'ready' : 'empty'
}

onMounted(async () => {
    featuredStatus.value = 'loading'
    album.value = null
    featuredWork.value = null
    try {
        const work = await api.workController.randomWork({
            offset: new Date().getTimezoneOffset() * 60000,
        })
        if (!work || work.recordings.length === 0) {
            featuredStatus.value = 'empty'
            return
        }

        featuredWork.value = work
        applyFeaturedWork()
    } catch (e) {
        featuredStatus.value = 'empty'
        console.error('Failed to fetch daily pick:', e)
    }
})

watch(
    () => userStore.preferredAssetFormat,
    () => {
        if (!featuredWork.value) {
            return
        }
        applyFeaturedWork()
    },
)
</script>

<template>
    <div class="relative mb-8 px-1 sm:px-2 md:mb-12">
        <h2 class="mb-5 text-lg font-serif text-[#2C2C2C] sm:mb-6 sm:text-xl">
            {{ t('dashboardHero.todayRecommend') }}
        </h2>

        <div class="relative h-[10.5rem] w-full md:h-80">
            <!-- Bottom Stack Layers -->
            <div
                class="absolute top-3 left-3 -right-1.5 -bottom-1.5 rounded-sm border border-[#E6E1D8] bg-[#F0EBE3] shadow-sm rotate-1 sm:top-4 sm:left-4 sm:-right-2.5 sm:-bottom-2.5"
            ></div>
            <div
                class="absolute top-1.5 left-1.5 -right-0.5 -bottom-0.5 rounded-sm border border-[#E6E1D8] bg-[#F5F2EB] shadow-md -rotate-1 sm:top-2 sm:left-2 sm:-right-1.25 sm:-bottom-1.25"
            ></div>

            <!-- Top Card -->
            <div
                class="absolute inset-0 flex flex-row items-center overflow-hidden rounded-sm border border-white bg-[#FCFBF9] shadow-[0_10px_30px_-10px_rgba(168,160,149,0.4)] md:items-stretch"
            >
                <!-- Album Cover -->
                <div
                    class="relative flex h-full w-auto aspect-square shrink-0 items-center justify-center overflow-hidden bg-[#D6D2C9] md:border-r md:border-[#EBE7E0]"
                    :class="{ 'group cursor-pointer': featuredStatus === 'ready' }"
                    @click="handleFeaturedAction"
                >
                    <template v-if="featuredStatus === 'ready' && album">
                        <img
                            :src="album.recording.cover"
                            alt="Album Cover"
                            class="absolute inset-0 w-full h-full object-cover opacity-90"
                        />

                        <!-- Play Overlay -->
                        <div
                            class="absolute inset-0 hidden items-center justify-center bg-black/40 opacity-0 transition-opacity duration-500 ease-out md:flex md:group-hover:opacity-100"
                        >
                            <div
                                class="flex h-12 w-12 items-center justify-center rounded-full bg-[#F5F2EB] shadow-lg transition-all duration-300 md:h-14 md:w-14 md:translate-y-4 md:group-hover:translate-y-0"
                            >
                                <Pause
                                    v-if="isFeaturedPlaying"
                                    :size="24"
                                    fill="#C27E46"
                                    class="text-[#C27E46]"
                                />
                                <Play
                                    v-else
                                    :size="24"
                                    fill="#C27E46"
                                    class="text-[#C27E46] ml-1"
                                />
                            </div>
                        </div>
                    </template>
                    <template v-else>
                        <img
                            :src="defaultFeaturedAlbum.cover"
                            alt="Album Cover Placeholder"
                            class="absolute inset-0 w-full h-full object-cover opacity-90"
                        />
                    </template>
                </div>

                <!-- Content -->
                <div
                    class="relative flex h-full min-w-0 flex-1 flex-col justify-center overflow-hidden bg-[url('https://www.transparenttextures.com/patterns/cream-paper.png')] p-4 md:p-12"
                >
                    <!-- Background Decoration -->
                    <div
                        class="pointer-events-none absolute -right-32 top-1/2 hidden h-96 w-96 -translate-y-1/2 select-none opacity-[0.06] md:block"
                    >
                        <svg viewBox="0 0 200 200" xmlns="http://www.w3.org/2000/svg">
                            <circle
                                cx="100"
                                cy="100"
                                r="75"
                                fill="none"
                                stroke="#2C2C2C"
                                stroke-width="15"
                            />
                            <circle
                                cx="100"
                                cy="100"
                                r="15"
                                fill="none"
                                stroke="#2C2C2C"
                                stroke-width="15"
                            />
                        </svg>
                    </div>

                    <div class="relative z-10">
                        <template v-if="featuredStatus === 'ready' && album">
                            <div
                                class="mb-3 hidden text-[11px] font-medium uppercase tracking-widest text-[#9C968B] md:mb-4 md:block"
                            >
                                {{ t('dashboardHero.todayRecommend') }}
                            </div>
                            <h3
                                class="mb-2 line-clamp-2 font-serif text-2xl leading-tight tracking-tight text-[#2C2C2C] md:mb-4 md:line-clamp-none md:text-5xl"
                            >
                                {{ album.title }}
                            </h3>
                            <p
                                class="mb-3 flex min-w-0 items-center text-sm font-serif italic text-[#8A857D] md:mb-10 md:text-lg"
                            >
                                <span
                                    class="mr-2 inline-block h-px w-5 shrink-0 bg-[#C27E46] md:mr-3 md:w-8"
                                ></span>
                                <span class="truncate">{{ album.recording.artist }}</span>
                            </p>
                        </template>
                        <template v-else>
                            <div
                                class="mb-3 hidden text-[11px] font-medium uppercase tracking-widest text-[#9C968B] md:mb-4 md:block"
                            >
                                {{ t('dashboardHero.todayRecommend') }}
                            </div>
                            <h3
                                class="mb-2 line-clamp-2 font-serif text-2xl leading-tight tracking-tight text-[#2C2C2C] md:mb-4 md:line-clamp-none md:text-3xl"
                            >
                                {{ t('dashboardHero.melodyUnavailable') }}
                            </h3>
                            <p
                                class="mb-3 line-clamp-2 text-sm font-serif italic text-[#8A857D] md:mb-10 md:line-clamp-none md:text-lg"
                            >
                                {{ t('dashboardHero.noMelodyFound') }}
                            </p>
                        </template>
                        <div
                            v-if="showFeaturedAction"
                            class="flex translate-y-2 items-center md:translate-y-0"
                        >
                            <button
                                class="inline-flex w-auto items-center gap-1.5 rounded-sm border border-[#C27E46] px-4 py-2 text-xs font-medium tracking-wide text-[#C27E46] uppercase transition-all duration-500 hover:bg-[#C27E46] hover:text-white disabled:cursor-not-allowed disabled:opacity-50 disabled:hover:bg-transparent disabled:hover:text-[#C27E46] md:px-8 md:py-3 md:text-sm"
                                :disabled="
                                    isFeaturedActionDisabled ||
                                    (featuredStatus === 'ready' && !hasPlayableFeatured)
                                "
                                @click="handleFeaturedAction"
                            >
                                <Pause
                                    v-if="featuredStatus === 'ready' && isFeaturedPlaying"
                                    :size="14"
                                    class="md:hidden"
                                />
                                <Play
                                    v-else-if="featuredStatus === 'ready'"
                                    :size="14"
                                    fill="currentColor"
                                    class="md:hidden"
                                />
                                <span class="md:hidden">{{ compactFeaturedActionLabel }}</span>
                                <span class="hidden md:inline">{{ featuredActionLabel }}</span>
                            </button>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>
