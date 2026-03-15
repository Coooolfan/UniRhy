<script setup lang="ts">
import { Pause, Play } from 'lucide-vue-next'
import { featuredAlbum as defaultFeaturedAlbum } from './data'
import { computed, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '@/ApiInstance'
import { resolveArtistName, resolvePlayableAudio } from '@/composables/recordingMedia'
import { useAudioStore } from '@/stores/audio'

type Album = {
    workId?: number
    recordingId?: number
    title: string
    artist: string
    cover: string
    audioSrc?: string
    mediaFileId?: number
}

type FeaturedStatus = 'loading' | 'ready' | 'empty'

const router = useRouter()
const album = ref<Album | null>(null)
const featuredStatus = ref<FeaturedStatus>('loading')
const audioStore = useAudioStore()

const isFeaturedPlaying = computed(() => {
    return (
        featuredStatus.value === 'ready' &&
        audioStore.isPlaying &&
        audioStore.currentTrack?.id === album.value?.recordingId
    )
})

const hasPlayableFeatured = computed(() => {
    return featuredStatus.value === 'ready' && !!album.value?.recordingId && !!album.value.audioSrc
})

const featuredActionLabel = computed(() => {
    if (featuredStatus.value === 'ready') {
        return isFeaturedPlaying.value ? '暂停播放' : '立即播放'
    }
    if (featuredStatus.value === 'empty') {
        return '前往设置'
    }
    return '加载中'
})

const isFeaturedActionDisabled = computed(() => featuredStatus.value === 'loading')

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
    const featuredAlbum = album.value
    const recordingId = featuredAlbum.recordingId
    const audioSrc = featuredAlbum.audioSrc
    const mediaFileId = featuredAlbum.mediaFileId

    if (recordingId === undefined || audioSrc === undefined || mediaFileId === undefined) {
        return
    }

    audioStore.play({
        id: recordingId,
        title: featuredAlbum.title,
        artist: featuredAlbum.artist,
        cover: featuredAlbum.cover,
        src: audioSrc,
        mediaFileId,
        workId: featuredAlbum.workId,
    })
}

onMounted(async () => {
    featuredStatus.value = 'loading'
    album.value = null
    try {
        const work = await api.workController.randomWork({
            offset: new Date().getTimezoneOffset() * 60000,
        })
        if (!work || work.recordings.length === 0) {
            featuredStatus.value = 'empty'
            return
        }

        const defaultRecording = work.recordings?.find((recording) => recording.defaultInWork)
        const featuredRecording = defaultRecording ?? work.recordings?.[0]
        const featuredAudio = resolvePlayableAudio(featuredRecording?.assets || [])

        if (!featuredRecording || !featuredAudio) {
            featuredStatus.value = 'empty'
            return
        }

        album.value = {
            workId: work.id,
            recordingId: featuredRecording.id,
            title: work.title,
            artist: resolveArtistName(featuredRecording.artists),
            cover: featuredRecording.cover?.id
                ? `/api/media/${featuredRecording.cover.id}`
                : defaultFeaturedAlbum.cover,
            audioSrc: featuredAudio.src,
            mediaFileId: featuredAudio.mediaFileId,
        }
        featuredStatus.value = 'ready'
    } catch (e) {
        featuredStatus.value = 'empty'
        console.error('Failed to fetch daily pick:', e)
    }
})
</script>

<template>
    <div class="relative mb-12 px-1 sm:px-2">
        <h2 class="mb-5 text-lg font-serif text-[#2C2C2C] sm:mb-6 sm:text-xl">今日推荐</h2>

        <div class="relative min-h-[34rem] w-full md:h-80 md:min-h-0">
            <!-- Bottom Stack Layers -->
            <div
                class="absolute top-3 left-3 -right-1.5 -bottom-1.5 rounded-sm border border-[#E6E1D8] bg-[#F0EBE3] shadow-sm rotate-1 sm:top-4 sm:left-4 sm:-right-2.5 sm:-bottom-2.5"
            ></div>
            <div
                class="absolute top-1.5 left-1.5 -right-0.5 -bottom-0.5 rounded-sm border border-[#E6E1D8] bg-[#F5F2EB] shadow-md -rotate-1 sm:top-2 sm:left-2 sm:-right-1.25 sm:-bottom-1.25"
            ></div>

            <!-- Top Card -->
            <div
                class="absolute inset-0 flex flex-col overflow-hidden rounded-sm border border-white bg-[#FCFBF9] shadow-[0_10px_30px_-10px_rgba(168,160,149,0.4)] md:flex-row"
            >
                <!-- Album Cover -->
                <div
                    class="relative flex h-48 w-full shrink-0 items-center justify-center overflow-hidden bg-[#D6D2C9] md:h-full md:w-auto md:aspect-square md:border-r md:border-[#EBE7E0]"
                    :class="{ 'group cursor-pointer': featuredStatus === 'ready' }"
                    @click="handleFeaturedAction"
                >
                    <template v-if="featuredStatus === 'ready' && album">
                        <img
                            :src="album.cover"
                            alt="Album Cover"
                            class="absolute inset-0 w-full h-full object-cover opacity-90"
                        />

                        <!-- Play Overlay -->
                        <div
                            class="absolute inset-0 flex items-center justify-center bg-black/40 opacity-100 transition-opacity duration-500 ease-out md:opacity-0 md:group-hover:opacity-100"
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
                    class="relative flex flex-1 flex-col justify-center overflow-hidden bg-[url('https://www.transparenttextures.com/patterns/cream-paper.png')] p-6 sm:p-8 md:p-12"
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
                                class="mb-3 text-[11px] font-medium uppercase tracking-widest text-[#9C968B] md:mb-4"
                            >
                                Editor's Choice
                            </div>
                            <h3
                                class="mb-3 font-serif text-3xl leading-tight tracking-tight text-[#2C2C2C] sm:text-4xl md:mb-4 md:text-5xl"
                            >
                                {{ album.title }}
                            </h3>
                            <p
                                class="mb-6 flex items-center text-base font-serif italic text-[#8A857D] sm:text-lg md:mb-10"
                            >
                                <span class="w-8 h-px bg-[#C27E46] mr-3 inline-block"></span>
                                {{ album.artist }}
                            </p>
                        </template>
                        <template v-else>
                            <div
                                class="mb-3 text-[11px] font-medium uppercase tracking-widest text-[#9C968B] md:mb-4"
                            >
                                Daily Pick
                            </div>
                            <h3
                                class="mb-3 font-serif text-3xl leading-tight tracking-tight text-[#2C2C2C] sm:text-4xl md:mb-4"
                            >
                                旋律不可调
                            </h3>
                            <p
                                class="mb-6 text-base font-serif italic text-[#8A857D] sm:text-lg md:mb-10"
                            >
                                资料库中未能发现可用旋律
                            </p>
                        </template>
                        <div class="flex items-center">
                            <button
                                class="w-full rounded-sm border border-[#C27E46] px-6 py-3 text-sm font-medium tracking-wide text-[#C27E46] uppercase transition-all duration-500 hover:bg-[#C27E46] hover:text-white disabled:cursor-not-allowed disabled:opacity-50 disabled:hover:bg-transparent disabled:hover:text-[#C27E46] sm:w-auto sm:px-8"
                                :disabled="
                                    isFeaturedActionDisabled ||
                                    (featuredStatus === 'ready' && !hasPlayableFeatured)
                                "
                                @click="handleFeaturedAction"
                            >
                                {{ featuredActionLabel }}
                            </button>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>
