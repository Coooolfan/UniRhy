<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Play, Pause, Pencil, Music } from 'lucide-vue-next'
import { api, normalizeApiError } from '@/ApiInstance'
import { useAudioStore } from '@/stores/audio'
import DashboardTopBar from '@/components/dashboard/DashboardTopBar.vue'
import MediaListPanel from '@/components/MediaListPanel.vue'
import MediaListItem from '@/components/MediaListItem.vue'

const route = useRoute()
const audioStore = useAudioStore()
const currentTrackId = ref<number | null>(null)
const isLoading = ref(true)

// Edit Modal State
const isEditModalOpen = ref(false)
const isEditing = ref(false)
const editName = ref('')
const editComment = ref('')
const editError = ref('')

type PlaylistData = {
    title: string
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

const playlistData = ref<PlaylistData>({
    title: '',
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

const fetchPlaylist = async (id: number) => {
    try {
        isLoading.value = true

        const data = await api.playlistController.getPlaylist({ id })

        const firstCover =
            data.recordings && data.recordings.length > 0 ? data.recordings[0]?.cover : undefined

        playlistData.value = {
            title: data.name,
            description: data.comment || '',
            // 歌单本身可能没有封面，使用第一首歌的封面或者默认封面
            cover: firstCover ? resolveCover(firstCover.id) : '',
        }

        // 映射曲目
        tracks.value = (data.recordings || []).map((recording) => ({
            id: recording.id,
            title: recording.title || recording.comment || 'Untitled Track',
            artist: recording.artists.map((artist) => artist.name).join(', ') || 'Unknown Artist',
            label: recording.label || '',
            cover: resolveCover(recording.cover?.id),
            audioSrc: resolveAudio(recording.assets || []),
        }))

        if (tracks.value.length > 0) {
            const firstPlayableTrack = tracks.value.find((track) => track.audioSrc)
            currentTrackId.value = firstPlayableTrack?.id ?? tracks.value[0]?.id ?? null
        }
    } catch (error) {
        console.error('Failed to fetch playlist details:', error)
    } finally {
        isLoading.value = false
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
        cover: targetTrack.cover || playlistData.value.cover,
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

// Edit Modal Functions
const openEditModal = () => {
    if (isEditing.value) return
    editName.value = playlistData.value.title
    editComment.value = playlistData.value.description
    editError.value = ''
    isEditModalOpen.value = true
}

const closeEditModal = () => {
    if (isEditing.value) return
    isEditModalOpen.value = false
    editName.value = ''
    editComment.value = ''
    editError.value = ''
}

const submitEdit = async () => {
    const name = editName.value.trim()
    const id = Number(route.params.id)
    if (!name) {
        editError.value = '请输入歌单名称'
        return
    }
    if (isNaN(id)) return

    if (isEditing.value) return
    isEditing.value = true
    editError.value = ''

    try {
        await api.playlistController.updatePlaylist({
            id,
            body: {
                name,
                comment: editComment.value.trim(),
            },
        })
        isEditModalOpen.value = false
        // Refresh playlist data
        await fetchPlaylist(id)
    } catch (error) {
        const normalized = normalizeApiError(error)
        editError.value = normalized.message ?? '更新歌单失败'
    } finally {
        isEditing.value = false
    }
}

onMounted(() => {
    const id = Number(route.params.id)
    if (!isNaN(id)) {
        fetchPlaylist(id)
    }
})

watch(
    () => route.params.id,
    (newId) => {
        const id = Number(newId)
        if (!isNaN(id)) {
            fetchPlaylist(id)
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
            <!-- 歌单头部卡片 -->
            <div class="mt-8 flex flex-col md:flex-row gap-12 items-end mb-16 group">
                <!-- 歌单封面 - 简化版，无 CD 动画 -->
                <div
                    class="relative z-0 group shrink-0 w-64 h-64 md:w-80 md:h-80 select-none shadow-xl rounded-sm overflow-hidden bg-[#2C2420]"
                >
                    <img
                        v-if="playlistData.cover"
                        :src="playlistData.cover"
                        alt="Playlist Cover"
                        class="w-full h-full object-cover"
                    />
                    <div
                        v-else
                        class="w-full h-full flex items-center justify-center bg-[#2C2420] text-[#8C857B]"
                    >
                        <span class="text-xs">No Cover</span>
                    </div>
                </div>

                <!-- 歌单信息 -->
                <div class="flex flex-col gap-4 pb-2 w-full relative z-10">
                    <div
                        class="flex items-center gap-3 text-sm tracking-wider uppercase text-[#8C857B]"
                    >
                        <span>Playlist</span>
                        <button
                            class="p-1 text-[#8C857B] hover:text-[#C17D46] transition-all opacity-0 group-hover:opacity-100 cursor-pointer"
                            @click="openEditModal"
                            title="编辑歌单"
                        >
                            <Pencil :size="14" />
                        </button>
                    </div>

                    <h1 class="text-5xl md:text-7xl font-serif text-[#2C2420] leading-tight">
                        {{ playlistData.title }}
                    </h1>

                    <p class="text-sm text-[#8C857B] max-w-2xl line-clamp-3">
                        {{ playlistData.description }}
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
                <template #empty> 前往 Work 或者 Album 详情页添加歌曲到您的歌单 </template>

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

        <Teleport to="body">
            <Transition
                enter-active-class="transition duration-200 ease-out"
                enter-from-class="opacity-0"
                enter-to-class="opacity-100"
                leave-active-class="transition duration-150 ease-in"
                leave-from-class="opacity-100"
                leave-to-class="opacity-0"
            >
                <div
                    v-if="isEditModalOpen"
                    class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-[#2B221B]/60"
                    @click.self="closeEditModal"
                >
                    <div
                        class="bg-[#fffcf5] p-8 w-full max-w-md shadow-[0_8px_30px_rgba(0,0,0,0.12)] border border-[#EAE6DE] relative transform transition-all"
                    >
                        <div
                            class="absolute top-0 right-0 w-16 h-16 bg-linear-to-bl from-[#EAE6DE]/30 to-transparent pointer-events-none"
                        ></div>

                        <div class="mb-8 text-center">
                            <div
                                class="inline-flex items-center justify-center w-12 h-12 rounded-full bg-[#FAF9F6] mb-4 border border-[#EAE6DE]"
                            >
                                <Music :size="24" class="text-[#C67C4E]" />
                            </div>
                            <h3 class="font-serif text-2xl text-[#2B221B]">编辑歌单</h3>
                            <p class="text-xs text-[#8A8A8A] mt-2 font-serif italic">
                                Edit Playlist
                            </p>
                        </div>

                        <div class="space-y-6">
                            <label class="block">
                                <span
                                    class="text-xs uppercase tracking-wider text-[#8A8A8A] font-serif block mb-2"
                                    >Name</span
                                >
                                <input
                                    v-model="editName"
                                    type="text"
                                    maxlength="100"
                                    class="w-full bg-[#F7F5F0] border-b border-[#D6D1C4] p-3 text-[#3D3D3D] focus:outline-none focus:border-[#C67C4E] transition-colors font-serif placeholder:text-[#BDB9AE]"
                                    placeholder="e.g. My Favorites"
                                    :disabled="isEditing"
                                />
                            </label>

                            <label class="block">
                                <span
                                    class="text-xs uppercase tracking-wider text-[#8A8A8A] font-serif block mb-2"
                                >
                                    Description
                                </span>
                                <textarea
                                    v-model="editComment"
                                    rows="3"
                                    maxlength="500"
                                    class="w-full resize-none bg-[#F7F5F0] border-b border-[#D6D1C4] p-3 text-[#3D3D3D] focus:outline-none focus:border-[#C67C4E] transition-colors font-serif placeholder:text-[#BDB9AE]"
                                    placeholder="Optional short note for this playlist"
                                    :disabled="isEditing"
                                />
                            </label>

                            <p v-if="editError" class="text-sm text-[#B95D5D]">
                                {{ editError }}
                            </p>

                            <div class="flex gap-3 mt-8 pt-6 border-t border-[#EAE6DE]">
                                <button
                                    type="button"
                                    class="flex-1 px-4 py-2.5 border border-[#D6D1C4] text-[#8A8A8A] hover:bg-[#F7F5F0] hover:text-[#5A5A5A] transition-colors text-sm uppercase tracking-wide"
                                    :disabled="isEditing"
                                    @click="closeEditModal"
                                >
                                    取消
                                </button>
                                <button
                                    type="button"
                                    class="flex-1 px-4 py-2.5 bg-[#2B221B] text-[#F7F5F0] hover:bg-[#C67C4E] transition-colors text-sm uppercase tracking-wide shadow-md disabled:opacity-60 disabled:cursor-not-allowed"
                                    :disabled="isEditing"
                                    @click="submitEdit"
                                >
                                    <span v-if="isEditing">Updating...</span>
                                    <span v-else>保存更改</span>
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            </Transition>
        </Teleport>
    </div>
</template>
