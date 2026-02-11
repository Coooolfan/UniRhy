<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Play, Pause } from 'lucide-vue-next'
import { api } from '@/ApiInstance'
import { useAudioStore } from '@/stores/audio'
import DashboardTopBar from '@/components/dashboard/DashboardTopBar.vue'
import MediaListPanel from '@/components/MediaListPanel.vue'
import MediaListItem from '@/components/MediaListItem.vue'

const route = useRoute()
const audioStore = useAudioStore()
const currentTrackId = ref<number | null>(null)
const isLoading = ref(true)
const isCdVisible = ref(false)

type AlbumData = {
    title: string
    artist: string
    year: string
    type: string
    description: string
    cover: string
}

type Track = {
    id: number
    title: string
    artist: string
    label: string
    cover: string
    audioSrc?: string
}

const albumData = ref<AlbumData>({
    title: '',
    artist: '',
    year: '',
    type: 'Album',
    description: '',
    cover: '',
})

const tracks = ref<Track[]>([])

const resolveCover = (coverId?: number) => {
    if (coverId !== undefined) {
        return `/api/media/${coverId}`
    }
    return ''
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

const fetchAlbum = async (id: number) => {
    try {
        isLoading.value = true
        // 获取新专辑时重置 CD 动画
        isCdVisible.value = false

        const data = await api.albumController.getAlbum({ id })

        // 映射专辑信息
        const releaseYear = data.releaseDate
            ? new Date(data.releaseDate).getFullYear().toString()
            : ''
        const artistName = data.recordings?.[0]?.artists?.[0]?.name ?? 'Unknown Artist'

        albumData.value = {
            title: data.title,
            artist: artistName,
            year: releaseYear,
            type: data.kind || 'Album',
            description: data.comment || '',
            cover: resolveCover(data.cover?.id),
        }

        // 映射曲目
        tracks.value = (data.recordings || []).map((recording) => ({
            id: recording.id,
            title: recording.title || recording.comment || 'Untitled Track',
            artist: recording.artists.map((artist) => artist.name).join(', ') || artistName,
            label: recording.label || '',
            cover: resolveCover(recording.cover?.id),
            audioSrc: resolveAudio(recording.assets || []),
        }))

        if (tracks.value.length > 0) {
            const firstPlayableTrack = tracks.value.find((track) => track.audioSrc)
            currentTrackId.value = firstPlayableTrack?.id ?? tracks.value[0]?.id ?? null
        }
    } catch (error) {
        console.error('Failed to fetch album details:', error)
    } finally {
        isLoading.value = false
        // 加载完成后触发动画
        setTimeout(() => {
            isCdVisible.value = true
        }, 100)
    }
}

const hasPlayableTrack = computed(() => tracks.value.some((track) => !!track.audioSrc))

const isCurrentTrackPlaying = computed(() => {
    return audioStore.isPlaying && audioStore.currentTrack?.id === currentTrackId.value
})

const handlePlay = (track?: Track) => {
    const targetTrackId = track?.id ?? currentTrackId.value
    if (!targetTrackId) return

    const targetTrack = tracks.value.find((item) => item.id === targetTrackId)
    if (!targetTrack || !targetTrack.audioSrc) {
        console.warn('No audio source for track', targetTrackId)
        return
    }

    currentTrackId.value = targetTrack.id
    audioStore.play({
        id: targetTrack.id,
        title: targetTrack.title,
        artist: targetTrack.artist,
        cover: targetTrack.cover || albumData.value.cover,
        src: targetTrack.audioSrc,
    })
}

const onTrackClick = (track: Track) => {
    currentTrackId.value = track.id
}

const onTrackDoubleClick = (track: Track) => {
    currentTrackId.value = track.id
    handlePlay(track)
}

const onTrackKeydown = (event: KeyboardEvent, track: Track) => {
    if (event.key === 'Enter' || event.key === ' ') {
        event.preventDefault()
        onTrackDoubleClick(track)
    }
}

onMounted(() => {
    const id = Number(route.params.id)
    if (!isNaN(id)) {
        fetchAlbum(id)
    }
})

watch(
    () => route.params.id,
    (newId) => {
        const id = Number(newId)
        if (!isNaN(id)) {
            fetchAlbum(id)
        }
    },
)
</script>

<template>
    <div class="flex-1 flex flex-col h-full relative">
        <!-- 顶部搜索栏 -->
        <DashboardTopBar />

        <div v-if="isLoading" class="flex-1 flex items-center justify-center text-[#8C857B]">
            Loading...
        </div>

        <div v-else class="px-8 pb-32 max-w-5xl mx-auto w-full">
            <!-- 专辑头部卡片 -->
            <div class="mt-8 flex flex-col md:flex-row gap-12 items-end mb-16">
                <!-- 带 CD 效果的封面艺术 -->
                <div
                    class="relative z-0 group shrink-0 w-64 h-64 md:w-80 md:h-80 select-none perspective-1000"
                >
                    <!-- 滑出的 CD 光盘 -->
                    <div
                        class="absolute top-2 right-2 bottom-2 left-2 bg-linear-to-tr from-gray-200 to-gray-100 rounded-full shadow-lg flex items-center justify-center transition-transform duration-2000 ease-out z-0"
                        :class="isCdVisible ? 'translate-x-16 md:translate-x-24' : 'translate-x-0'"
                    >
                        <div
                            class="w-1/3 h-1/3 border border-gray-300 rounded-full opacity-50"
                        ></div>
                        <div
                            class="absolute w-8 h-8 bg-[#EBE7E0] rounded-full border border-gray-300"
                        ></div>
                    </div>

                    <!-- 专辑封面 -->
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

                <!-- 专辑信息 -->
                <div class="flex flex-col gap-4 pb-2 w-full relative z-10">
                    <div
                        class="flex items-center gap-3 text-sm tracking-wider uppercase text-[#8C857B]"
                    >
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
                            @click="handlePlay()"
                            :disabled="!hasPlayableTrack"
                            class="px-8 py-3 border border-[#C17D46] text-[#C17D46] hover:bg-[#C17D46] hover:text-white disabled:opacity-50 disabled:cursor-not-allowed disabled:hover:bg-transparent disabled:hover:text-[#C17D46] transition-all duration-300 flex items-center gap-2 text-sm tracking-widest uppercase font-medium rounded-sm cursor-pointer"
                        >
                            <Pause v-if="isCurrentTrackPlaying" :size="16" />
                            <Play v-else :size="16" fill="currentColor" />
                            {{ isCurrentTrackPlaying ? '暂停播放' : '立即播放' }}
                        </button>
                    </div>
                </div>
            </div>

            <MediaListPanel
                title="Tracklist"
                :summary="`${tracks.length} Songs`"
                :items="tracks"
                :active-id="currentTrackId"
                :playing-id="audioStore.isPlaying ? (audioStore.currentTrack?.id ?? null) : null"
                :playing-requires-active="true"
                @item-click="onTrackClick"
                @item-double-click="onTrackDoubleClick"
                @item-keydown="onTrackKeydown"
            >
                <template #item="{ item, isActive }">
                    <MediaListItem
                        :title="item.title"
                        :label="item.label"
                        :is-active="isActive"
                        :is-playing="
                            audioStore.isPlaying && audioStore.currentTrack?.id === item.id
                        "
                        @play="handlePlay(item)"
                    />
                </template>
            </MediaListPanel>
        </div>
    </div>
</template>
