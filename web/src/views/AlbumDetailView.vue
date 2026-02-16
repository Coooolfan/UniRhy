<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Play, Pause } from 'lucide-vue-next'
import { api } from '@/ApiInstance'
import { useAudioStore } from '@/stores/audio'
import DashboardTopBar from '@/components/dashboard/DashboardTopBar.vue'
import MediaListPanel from '@/components/MediaListPanel.vue'
import MediaListItem from '@/components/MediaListItem.vue'
import AddRecordingToPlaylistModal from '@/components/playlist/AddRecordingToPlaylistModal.vue'

const route = useRoute()
const audioStore = useAudioStore()
const currentRecordingId = ref<number | null>(null)
const isLoading = ref(true)
const isCdVisible = ref(false)
const isAddToPlaylistModalOpen = ref(false)
const selectedRecordingForPlaylist = ref<Recording | null>(null)

type AlbumData = {
    title: string
    artist: string
    year: string
    type: string
    description: string
    cover: string
}

type Recording = {
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

const recordings = ref<Recording[]>([])

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

        // 映射录音
        recordings.value = (data.recordings || []).map((recording) => ({
            id: recording.id,
            title: recording.title || recording.comment || 'Untitled Recording',
            artist: recording.artists.map((artist) => artist.name).join(', ') || artistName,
            label: recording.label || '',
            cover: resolveCover(recording.cover?.id),
            audioSrc: resolveAudio(recording.assets || []),
        }))

        if (recordings.value.length > 0) {
            const firstPlayableRecording = recordings.value.find((recording) => recording.audioSrc)
            currentRecordingId.value = firstPlayableRecording?.id ?? recordings.value[0]?.id ?? null
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

const hasPlayableRecording = computed(() =>
    recordings.value.some((recording) => !!recording.audioSrc),
)

const isCurrentRecordingPlaying = computed(() => {
    return audioStore.isPlaying && audioStore.currentTrack?.id === currentRecordingId.value
})

const handlePlay = (recording?: Recording) => {
    const targetRecordingId = recording?.id ?? currentRecordingId.value
    if (!targetRecordingId) return

    const targetRecording = recordings.value.find((item) => item.id === targetRecordingId)
    if (!targetRecording || !targetRecording.audioSrc) {
        console.warn('No audio source for recording', targetRecordingId)
        return
    }

    currentRecordingId.value = targetRecording.id
    audioStore.play({
        id: targetRecording.id,
        title: targetRecording.title,
        artist: targetRecording.artist,
        cover: targetRecording.cover || albumData.value.cover,
        src: targetRecording.audioSrc,
    })
}

const onRecordingClick = (recording: Recording) => {
    currentRecordingId.value = recording.id
}

const onRecordingDoubleClick = (recording: Recording) => {
    currentRecordingId.value = recording.id
    handlePlay(recording)
}

const onRecordingKeydown = (event: KeyboardEvent, recording: Recording) => {
    if (event.key === 'Enter' || event.key === ' ') {
        event.preventDefault()
        onRecordingDoubleClick(recording)
    }
}

const openAddToPlaylistModal = (recording: Recording) => {
    selectedRecordingForPlaylist.value = recording
    isAddToPlaylistModalOpen.value = true
}

const closeAddToPlaylistModal = () => {
    isAddToPlaylistModalOpen.value = false
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
                            :disabled="!hasPlayableRecording"
                            class="px-8 py-3 border border-[#C17D46] text-[#C17D46] hover:bg-[#C17D46] hover:text-white disabled:opacity-50 disabled:cursor-not-allowed disabled:hover:bg-transparent disabled:hover:text-[#C17D46] transition-all duration-300 flex items-center gap-2 text-sm tracking-widest uppercase font-medium rounded-sm cursor-pointer"
                        >
                            <Pause v-if="isCurrentRecordingPlaying" :size="16" />
                            <Play v-else :size="16" fill="currentColor" />
                            {{ isCurrentRecordingPlaying ? '暂停播放' : '立即播放' }}
                        </button>
                    </div>
                </div>
            </div>

            <MediaListPanel
                title="Recordings"
                :summary="`${recordings.length} Recordings`"
                :items="recordings"
                :active-id="currentRecordingId"
                :playing-id="audioStore.isPlaying ? (audioStore.currentTrack?.id ?? null) : null"
                :playing-requires-active="true"
                @item-click="onRecordingClick"
                @item-double-click="onRecordingDoubleClick"
                @item-keydown="onRecordingKeydown"
            >
                <template #item="{ item, isActive }">
                    <MediaListItem
                        :title="item.title"
                        :label="item.label"
                        :show-add-button="true"
                        :is-active="isActive"
                        :is-playing="
                            audioStore.isPlaying && audioStore.currentTrack?.id === item.id
                        "
                        @play="handlePlay(item)"
                        @add="openAddToPlaylistModal(item)"
                    />
                </template>
            </MediaListPanel>
        </div>

        <AddRecordingToPlaylistModal
            :open="isAddToPlaylistModalOpen"
            :recording-id="selectedRecordingForPlaylist?.id ?? null"
            :recording-title="selectedRecordingForPlaylist?.title"
            @close="closeAddToPlaylistModal"
        />
    </div>
</template>
