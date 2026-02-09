<script setup lang="ts">
import { Heart, Pause, Play } from 'lucide-vue-next'
import { featuredAlbum as defaultFeaturedAlbum } from './data'
import { computed, ref, onMounted } from 'vue'
import { api } from '@/ApiInstance'
import { useAudioStore } from '@/stores/audio'

type Album = {
    workId?: number
    recordingId?: number
    title: string
    artist: string
    year: string
    cover: string
    audioSrc?: string
}

type WorkArtist = {
    id: number
    name?: string
}

const album = ref<Album>({ ...defaultFeaturedAlbum })
const audioStore = useAudioStore()

const resolveArtistName = (artists?: ReadonlyArray<WorkArtist>) => {
    const names = artists?.map((artist) => artist.name).filter(Boolean) ?? []
    if (names.length > 0) {
        return names.join(' / ')
    }
    return 'Unknown Artist'
}

type Asset = {
    mediaFile: {
        id: number
        mimeType: string
    }
}

const resolveAudio = (assets: readonly Asset[]) => {
    const audioAsset = assets.find((asset) => asset.mediaFile.mimeType.startsWith('audio/'))
    if (audioAsset) {
        return `/api/media/${audioAsset.mediaFile.id}`
    }
    return undefined
}

const isFeaturedPlaying = computed(() => {
    return audioStore.isPlaying && audioStore.currentTrack?.id === album.value.recordingId
})

const hasPlayableFeatured = computed(() => {
    return !!album.value.recordingId && !!album.value.audioSrc
})

const handlePlayFeatured = () => {
    if (!album.value.recordingId || !album.value.audioSrc) {
        return
    }

    audioStore.play({
        id: album.value.recordingId,
        title: album.value.title,
        artist: album.value.artist,
        cover: album.value.cover,
        src: album.value.audioSrc,
        workId: album.value.workId,
    })
}

onMounted(async () => {
    try {
        const work = await api.workController.randomWork({
            offset: new Date().getTimezoneOffset() * 60000,
        })
        const defaultRecording = work.recordings?.find((recording) => recording.defaultInWork)
        const featuredRecording = defaultRecording ?? work.recordings?.[0]

        if (work) {
            album.value = {
                workId: work.id,
                recordingId: featuredRecording?.id,
                title: work.title,
                artist: resolveArtistName(
                    featuredRecording?.artists as ReadonlyArray<WorkArtist> | undefined,
                ),
                year: '2024', // Fallback as Work doesn't have year
                cover: featuredRecording?.cover?.id
                    ? `/api/media/${featuredRecording.cover.id}`
                    : defaultFeaturedAlbum.cover,
                audioSrc: resolveAudio(featuredRecording?.assets || []),
            }
        }
    } catch (e) {
        console.error('Failed to fetch daily pick:', e)
    }
})
</script>

<template>
    <div class="mb-14 relative px-2">
        <h2 class="text-xl font-serif mb-6 text-[#2C2C2C]">每日精选</h2>

        <div class="relative h-80 w-full">
            <!-- Bottom Stack Layers -->
            <div
                class="absolute top-4 left-4 -right-2.5 -bottom-2.5 bg-[#F0EBE3] shadow-sm transform rotate-1 rounded-sm border border-[#E6E1D8]"
            ></div>
            <div
                class="absolute top-2 left-2 -right-1.25 -bottom-1.25 bg-[#F5F2EB] shadow-md transform -rotate-1 rounded-sm border border-[#E6E1D8]"
            ></div>

            <!-- Top Card -->
            <div
                class="absolute inset-0 bg-[#FCFBF9] shadow-[0_10px_30px_-10px_rgba(168,160,149,0.4)] rounded-sm flex overflow-hidden border border-white"
            >
                <!-- Album Cover -->
                <div
                    class="h-full aspect-square bg-[#D6D2C9] relative flex items-center justify-center group cursor-pointer overflow-hidden border-r border-[#EBE7E0] shrink-0"
                    @click="handlePlayFeatured"
                >
                    <img
                        :src="album.cover"
                        alt="Album Cover"
                        class="absolute inset-0 w-full h-full object-cover opacity-90 transition-transform duration-700 group-hover:scale-105"
                    />

                    <!-- Play Overlay -->
                    <div
                        class="absolute inset-0 bg-black/30 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center"
                    >
                        <div
                            class="w-14 h-14 bg-[#F5F2EB] rounded-full flex items-center justify-center shadow-lg transform translate-y-4 group-hover:translate-y-0 transition-all duration-300"
                        >
                            <Pause
                                v-if="isFeaturedPlaying"
                                :size="24"
                                fill="#C27E46"
                                class="text-[#C27E46]"
                            />
                            <Play v-else :size="24" fill="#C27E46" class="text-[#C27E46] ml-1" />
                        </div>
                    </div>
                </div>

                <!-- Content -->
                <div
                    class="flex-1 p-12 flex flex-col justify-center bg-[url('https://www.transparenttextures.com/patterns/cream-paper.png')] relative overflow-hidden"
                >
                    <!-- Background Decoration -->
                    <div
                        class="absolute -right-32 top-1/2 -translate-y-1/2 w-96 h-96 opacity-[0.06] pointer-events-none select-none"
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
                        <div
                            class="text-xs uppercase tracking-widest text-[#9C968B] mb-4 font-medium"
                        >
                            Editor's Choice
                        </div>
                        <h3
                            class="text-5xl font-serif text-[#2C2C2C] mb-4 tracking-tight leading-tight"
                        >
                            {{ album.title }}
                        </h3>
                        <p class="text-[#8A857D] text-lg mb-10 font-serif italic flex items-center">
                            <span class="w-8 h-px bg-[#C27E46] mr-3 inline-block"></span>
                            {{ album.artist }}
                        </p>
                        <div class="flex items-center space-x-6">
                            <button
                                class="px-8 py-3 border border-[#C27E46] text-[#C27E46] text-sm hover:bg-[#C27E46] hover:text-white disabled:opacity-50 disabled:cursor-not-allowed disabled:hover:bg-transparent disabled:hover:text-[#C27E46] transition-all duration-500 rounded-sm font-medium tracking-wide uppercase"
                                :disabled="!hasPlayableFeatured"
                                @click="handlePlayFeatured"
                            >
                                {{ isFeaturedPlaying ? '暂停播放' : '立即播放' }}
                            </button>
                            <button
                                class="text-[#9C968B] hover:text-[#C27E46] transition-colors p-2"
                            >
                                <Heart :size="24" />
                            </button>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>
