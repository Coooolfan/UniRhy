<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Play, Pause, Pencil, Music, Trash2 } from 'lucide-vue-next'
import { api, normalizeApiError } from '@/ApiInstance'
import { useAudioStore } from '@/stores/audio'
import { usePlaylistStore } from '@/stores/playlist'
import DashboardTopBar from '@/components/dashboard/DashboardTopBar.vue'
import MediaListPanel from '@/components/MediaListPanel.vue'
import MediaListItem from '@/components/MediaListItem.vue'

const route = useRoute()
const router = useRouter()
const audioStore = useAudioStore()
const playlistStore = usePlaylistStore()
const currentRecordingId = ref<number | null>(null)
const isLoading = ref(true)

// Edit Modal State
const isEditModalOpen = ref(false)
const isEditing = ref(false)
const editName = ref('')
const editComment = ref('')
const editError = ref('')
const isDeleteConfirming = ref(false)
const isDeletingPlaylist = ref(false)
const deletePlaylistError = ref('')
const recordingPendingRemoval = ref<Recording | null>(null)
const isRemovingRecording = ref(false)
const removeRecordingError = ref('')

type PlaylistData = {
    title: string
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

const playlistData = ref<PlaylistData>({
    title: '',
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

const fetchPlaylist = async (id: number) => {
    try {
        isLoading.value = true
        removeRecordingError.value = ''

        const data = await api.playlistController.getPlaylist({ id })

        const firstCover =
            data.recordings && data.recordings.length > 0 ? data.recordings[0]?.cover : undefined

        playlistData.value = {
            title: data.name,
            description: data.comment || '',
            // 歌单本身可能没有封面，使用第一条录音的封面或者默认封面
            cover: firstCover ? resolveCover(firstCover.id) : '',
        }

        // 映射录音
        recordings.value = (data.recordings || []).map((recording) => ({
            id: recording.id,
            title: recording.title || recording.comment || 'Untitled Recording',
            artist: recording.artists.map((artist) => artist.name).join(', ') || 'Unknown Artist',
            label: recording.label || '',
            cover: resolveCover(recording.cover?.id),
            audioSrc: resolveAudio(recording.assets || []),
        }))

        if (recordings.value.length > 0) {
            const firstPlayableRecording = recordings.value.find((recording) => recording.audioSrc)
            currentRecordingId.value = firstPlayableRecording?.id ?? recordings.value[0]?.id ?? null
        }
    } catch (error) {
        console.error('Failed to fetch playlist details:', error)
    } finally {
        isLoading.value = false
    }
}

const hasPlayableRecording = computed(() =>
    recordings.value.some((recording) => !!recording.audioSrc),
)
const isDeleteAction = computed(() => editName.value.trim().length === 0)

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
        cover: targetRecording.cover || playlistData.value.cover,
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

// Edit Modal Functions
const openEditModal = () => {
    if (isEditing.value || isDeletingPlaylist.value) return
    editName.value = playlistData.value.title
    editComment.value = playlistData.value.description
    editError.value = ''
    deletePlaylistError.value = ''
    isDeleteConfirming.value = false
    isEditModalOpen.value = true
}

const closeEditModal = () => {
    if (isEditing.value || isDeletingPlaylist.value) return
    isEditModalOpen.value = false
    isDeleteConfirming.value = false
    editName.value = ''
    editComment.value = ''
    editError.value = ''
    deletePlaylistError.value = ''
}

const submitEdit = async () => {
    const name = editName.value.trim()
    const id = Number(route.params.id)
    if (isNaN(id)) return

    if (isDeleteAction.value) {
        editError.value = ''
        if (!isDeleteConfirming.value) {
            isDeleteConfirming.value = true
            deletePlaylistError.value = ''
            return
        }
        await confirmDeletePlaylist()
        return
    }

    if (isEditing.value || isDeletingPlaylist.value) return
    isDeleteConfirming.value = false
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
        playlistStore.upsertPlaylist({ id, name })
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

const confirmDeletePlaylist = async () => {
    const id = Number(route.params.id)
    if (isNaN(id) || isDeletingPlaylist.value) return

    isDeletingPlaylist.value = true
    deletePlaylistError.value = ''

    try {
        await api.playlistController.deletePlaylist({ id })
        playlistStore.removePlaylist(id)

        if (
            audioStore.currentTrack &&
            recordings.value.some((recording) => recording.id === audioStore.currentTrack?.id)
        ) {
            audioStore.stop()
        }

        isEditModalOpen.value = false
        await router.push({ name: 'dashboard-home' })
    } catch (error) {
        const normalized = normalizeApiError(error)
        deletePlaylistError.value = normalized.message ?? '删除歌单失败'
    } finally {
        isDeletingPlaylist.value = false
    }
}

const openRemoveRecordingModal = (recording: Recording) => {
    if (isRemovingRecording.value) return
    recordingPendingRemoval.value = recording
    removeRecordingError.value = ''
}

const closeRemoveRecordingModal = () => {
    if (isRemovingRecording.value) return
    recordingPendingRemoval.value = null
    removeRecordingError.value = ''
}

const confirmRemoveRecording = async () => {
    const recording = recordingPendingRemoval.value
    if (!recording || isRemovingRecording.value) return

    const playlistId = Number(route.params.id)
    if (isNaN(playlistId)) return

    isRemovingRecording.value = true
    removeRecordingError.value = ''

    try {
        await api.playlistController.removeRecordingFromPlaylist({
            id: playlistId,
            recordingId: recording.id,
        })

        const nextRecordings = recordings.value.filter((item) => item.id !== recording.id)
        recordings.value = nextRecordings

        if (currentRecordingId.value === recording.id) {
            const firstPlayableRecording = nextRecordings.find((item) => item.audioSrc)
            currentRecordingId.value = firstPlayableRecording?.id ?? nextRecordings[0]?.id ?? null
        }

        if (audioStore.currentTrack?.id === recording.id) {
            audioStore.stop()
        }

        playlistData.value = {
            ...playlistData.value,
            cover: nextRecordings[0]?.cover || '',
        }
        recordingPendingRemoval.value = null
    } catch (error) {
        const normalized = normalizeApiError(error)
        removeRecordingError.value = normalized.message ?? '移除录音失败'
    } finally {
        isRemovingRecording.value = false
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

watch(
    () => editName.value,
    (name) => {
        if (name.trim().length > 0) {
            isDeleteConfirming.value = false
            deletePlaylistError.value = ''
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
                <template #empty> 前往 Work 或者 Album 详情页添加录音到您的歌单 </template>

                <template #item="{ item, isActive }">
                    <MediaListItem
                        :title="item.title"
                        :label="item.label"
                        :show-remove-button="true"
                        :is-removing="
                            isRemovingRecording && recordingPendingRemoval?.id === item.id
                        "
                        :is-active="isActive"
                        :is-playing="
                            audioStore.isPlaying && audioStore.currentTrack?.id === item.id
                        "
                        @play="handlePlay(item)"
                        @remove="openRemoveRecordingModal(item)"
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
                                class="inline-flex items-center justify-center w-12 h-12 rounded-full bg-[#FAF9F6] mb-4 border"
                                :class="isDeleteAction ? 'border-[#F0D6D6]' : 'border-[#EAE6DE]'"
                            >
                                <Trash2 v-if="isDeleteAction" :size="22" class="text-[#B95D5D]" />
                                <Music v-else :size="24" class="text-[#C67C4E]" />
                            </div>
                            <h3 class="font-serif text-2xl text-[#2B221B]">
                                {{ isDeleteAction ? '删除歌单' : '编辑歌单' }}
                            </h3>
                            <p class="text-xs text-[#8A8A8A] mt-2 font-serif italic">
                                {{
                                    isDeleteAction
                                        ? isDeleteConfirming
                                            ? 'Confirm Deletion'
                                            : 'Delete Playlist'
                                        : 'Edit Playlist'
                                }}
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
                                    :disabled="isEditing || isDeletingPlaylist"
                                />
                                <p
                                    v-if="isDeleteAction && isDeleteConfirming"
                                    class="mt-2 text-sm text-[#B95D5D] font-serif italic"
                                >
                                    再次点击“确认删除”后将永久删除歌单，此操作不可恢复。
                                </p>
                            </label>

                            <label v-if="!isDeleteAction" class="block">
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
                                    :disabled="isEditing || isDeletingPlaylist"
                                />
                            </label>

                            <p v-if="editError" class="text-sm text-[#B95D5D]">
                                {{ editError }}
                            </p>
                            <p v-if="deletePlaylistError" class="text-sm text-[#B95D5D]">
                                {{ deletePlaylistError }}
                            </p>

                            <div class="flex gap-3 mt-8 pt-6 border-t border-[#EAE6DE]">
                                <button
                                    type="button"
                                    class="flex-1 px-4 py-2.5 border border-[#D6D1C4] text-[#8A8A8A] hover:bg-[#F7F5F0] hover:text-[#5A5A5A] transition-colors text-sm uppercase tracking-wide"
                                    :disabled="isEditing || isDeletingPlaylist"
                                    @click="closeEditModal"
                                >
                                    取消
                                </button>
                                <button
                                    type="button"
                                    class="flex-1 px-4 py-2.5 text-[#F7F5F0] transition-colors text-sm uppercase tracking-wide shadow-md disabled:opacity-60 disabled:cursor-not-allowed"
                                    :class="
                                        isDeleteAction
                                            ? isDeleteConfirming
                                                ? 'bg-[#A24E4E] hover:bg-[#8E4040]'
                                                : 'bg-[#B95D5D] hover:bg-[#A24E4E]'
                                            : 'bg-[#2B221B] hover:bg-[#C67C4E]'
                                    "
                                    :disabled="isEditing || isDeletingPlaylist"
                                    @click="submitEdit"
                                >
                                    <span v-if="isEditing">Updating...</span>
                                    <span v-else-if="isDeletingPlaylist">删除中...</span>
                                    <span v-else-if="isDeleteAction && isDeleteConfirming"
                                        >确认删除</span
                                    >
                                    <span v-else-if="isDeleteAction">删除歌单</span>
                                    <span v-else>保存更改</span>
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            </Transition>

            <Transition
                enter-active-class="transition duration-200 ease-out"
                enter-from-class="opacity-0"
                enter-to-class="opacity-100"
                leave-active-class="transition duration-150 ease-in"
                leave-from-class="opacity-100"
                leave-to-class="opacity-0"
            >
                <div
                    v-if="recordingPendingRemoval"
                    class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-[#2B221B]/60"
                    @click.self="closeRemoveRecordingModal"
                >
                    <div
                        class="bg-[#fffcf5] p-8 w-full max-w-md shadow-[0_8px_30px_rgba(0,0,0,0.12)] border border-[#EAE6DE] relative transform transition-all"
                    >
                        <div
                            class="absolute top-0 right-0 w-16 h-16 bg-linear-to-bl from-[#EAE6DE]/30 to-transparent pointer-events-none"
                        ></div>

                        <div class="mb-6 text-center">
                            <div
                                class="inline-flex items-center justify-center w-12 h-12 rounded-full bg-[#FAF9F6] mb-4 border border-[#EAE6DE]"
                            >
                                <Trash2 :size="22" class="text-[#B95D5D]" />
                            </div>
                            <h3 class="font-serif text-2xl text-[#2B221B]">移除录音</h3>
                            <p class="text-sm text-[#8C857B] mt-3">
                                确认从当前歌单中移除「{{ recordingPendingRemoval.title }}」？
                            </p>
                        </div>

                        <p v-if="removeRecordingError" class="text-sm text-[#B95D5D] mb-4">
                            {{ removeRecordingError }}
                        </p>

                        <div class="flex gap-3 pt-4 border-t border-[#EAE6DE]">
                            <button
                                type="button"
                                class="flex-1 px-4 py-2.5 border border-[#D6D1C4] text-[#8A8A8A] hover:bg-[#F7F5F0] hover:text-[#5A5A5A] transition-colors text-sm uppercase tracking-wide disabled:opacity-60 disabled:cursor-not-allowed"
                                :disabled="isRemovingRecording"
                                @click="closeRemoveRecordingModal"
                            >
                                取消
                            </button>
                            <button
                                type="button"
                                class="flex-1 px-4 py-2.5 bg-[#2B221B] text-[#F7F5F0] hover:bg-[#B95D5D] transition-colors text-sm uppercase tracking-wide shadow-md disabled:opacity-60 disabled:cursor-not-allowed"
                                :disabled="isRemovingRecording"
                                @click="confirmRemoveRecording"
                            >
                                <span v-if="isRemovingRecording">移除中...</span>
                                <span v-else>确认移除</span>
                            </button>
                        </div>
                    </div>
                </div>
            </Transition>
        </Teleport>
    </div>
</template>
