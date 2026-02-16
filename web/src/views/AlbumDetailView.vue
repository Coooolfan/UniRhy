<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Play, Pause, Disc, FileAudio, Users, Image as ImageIcon } from 'lucide-vue-next'
import { api, normalizeApiError } from '@/ApiInstance'
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

const isEditRecordingModalOpen = ref(false)
const isEditingRecording = ref(false)
const editingRecording = ref<Recording | null>(null)
const editRecordingForm = ref({
    title: '',
    label: '',
    comment: '',
    type: '',
})
const editRecordingError = ref('')

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
    type: string
    comment: string
    assets: readonly Asset[]
    rawArtists: readonly { readonly id: number; readonly name: string }[]
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
        objectKey: string
        ossProvider?: { id: number }
        fsProvider?: { id: number }
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
            type: recording.kind,
            comment: recording.comment,
            assets: recording.assets || [],
            rawArtists: recording.artists || [],
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

const openEditRecordingModal = (rec: Recording) => {
    if (isEditingRecording.value) return

    editingRecording.value = rec
    editRecordingForm.value = {
        title: rec.title,
        label: rec.label,
        comment: rec.comment,
        type: rec.type,
    }
    editRecordingError.value = ''
    isEditRecordingModalOpen.value = true
}

const closeEditRecordingModal = () => {
    if (isEditingRecording.value) return

    isEditRecordingModalOpen.value = false
    editingRecording.value = null
    editRecordingForm.value = {
        title: '',
        label: '',
        comment: '',
        type: '',
    }
    editRecordingError.value = ''
}

const submitRecordingEdit = async () => {
    if (!editingRecording.value || isEditingRecording.value) return

    const { title, label, comment, type } = editRecordingForm.value

    if (!title.trim()) {
        editRecordingError.value = '标题不能为空'
        return
    }

    isEditingRecording.value = true
    editRecordingError.value = ''

    try {
        await api.recordingController.updateRecording({
            id: editingRecording.value.id,
            body: {
                title: title.trim(),
                label: label?.trim(),
                comment: comment?.trim() || '',
                kind: type.trim(),
            },
        })

        // Update local state
        const index = recordings.value.findIndex((r) => r.id === editingRecording.value.id)
        if (index !== -1) {
            recordings.value[index] = {
                ...recordings.value[index],
                title: title.trim(),
                label: label?.trim() || '',
                comment: comment?.trim() || '',
                type: type.trim(),
            }
        }

        isEditingRecording.value = false
        closeEditRecordingModal()
    } catch (error) {
        const normalized = normalizeApiError(error)
        editRecordingError.value = normalized.message ?? '更新录音失败'
    } finally {
        isEditingRecording.value = false
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
                        :show-edit-button="true"
                        :is-active="isActive"
                        :is-playing="
                            audioStore.isPlaying && audioStore.currentTrack?.id === item.id
                        "
                        @play="handlePlay(item)"
                        @add="openAddToPlaylistModal(item)"
                        @edit="openEditRecordingModal(item)"
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
                    v-if="isEditRecordingModalOpen"
                    class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-[#2B221B]/60"
                    @click.self="closeEditRecordingModal"
                >
                    <div
                        class="bg-[#fffcf5] p-8 w-full max-w-4xl shadow-[0_8px_30px_rgba(0,0,0,0.12)] border border-[#EAE6DE] relative transform transition-all max-h-[90vh] overflow-y-auto"
                    >
                        <div
                            class="absolute top-0 right-0 w-16 h-16 bg-linear-to-bl from-[#EAE6DE]/30 to-transparent pointer-events-none"
                        ></div>

                        <div class="mb-8 text-center">
                            <div
                                class="inline-flex items-center justify-center w-12 h-12 rounded-full bg-[#FAF9F6] mb-4 border border-[#EAE6DE]"
                            >
                                <Disc :size="24" class="text-[#C67C4E]" />
                            </div>
                            <h3 class="font-serif text-2xl text-[#2B221B]">关于录音</h3>
                            <p class="text-xs text-[#8A8A8A] mt-2 font-serif italic">
                                About Recording
                            </p>
                        </div>

                        <div class="grid grid-cols-1 md:grid-cols-2 gap-8">
                            <!-- Left Column: Info (Cover, Artists, Assets) -->
                            <div class="space-y-6">
                                <div
                                    v-if="editingRecording"
                                    class="bg-[#F7F5F0] border border-[#D6D1C4] p-5 rounded-sm h-full flex flex-col"
                                >
                                    <!-- Cover & Basic Info -->
                                    <div class="flex gap-5 mb-6">
                                        <div
                                            class="w-24 h-24 shrink-0 bg-[#EAE6DE] rounded-sm overflow-hidden shadow-sm"
                                        >
                                            <img
                                                v-if="editingRecording.cover"
                                                :src="editingRecording.cover"
                                                class="w-full h-full object-cover"
                                            />
                                            <div
                                                v-else
                                                class="w-full h-full flex items-center justify-center text-[#8C857B]"
                                            >
                                                <ImageIcon :size="24" />
                                            </div>
                                        </div>
                                        <div class="flex-1 min-w-0 space-y-3 py-1">
                                            <!-- Artists -->
                                            <div>
                                                <div
                                                    class="flex items-center gap-1.5 text-[#8C857B] text-xs uppercase tracking-wider mb-1"
                                                >
                                                    <Users :size="12" />
                                                    <span>Artists</span>
                                                </div>
                                                <div class="text-[#2C2420] font-medium leading-snug">
                                                    {{
                                                        editingRecording.rawArtists
                                                            .map((a) => a.name)
                                                            .join(', ') || 'Unknown Artist'
                                                    }}
                                                </div>
                                            </div>
                                            <!-- Assets Summary -->
                                            <div>
                                                <div
                                                    class="flex items-center gap-1.5 text-[#8C857B] text-xs uppercase tracking-wider mb-1"
                                                >
                                                    <FileAudio :size="12" />
                                                    <span>Assets</span>
                                                </div>
                                                <div class="text-[#2C2420] truncate">
                                                    {{ editingRecording.assets.length }} file(s) attached
                                                </div>
                                            </div>
                                        </div>
                                    </div>

                                    <!-- Assets Details -->
                                    <div
                                        v-if="editingRecording.assets.length > 0"
                                        class="border-t border-[#D6D1C4] pt-4 flex-1"
                                    >
                                        <div class="text-xs text-[#8C857B] mb-3 uppercase tracking-wider">Attached Files</div>
                                        <div class="flex flex-col gap-2">
                                            <div
                                                v-for="asset in editingRecording.assets"
                                                :key="asset.mediaFile.id"
                                                class="flex items-start gap-2 text-xs bg-[#EAE6DE]/50 px-2 py-2 rounded-sm text-[#5E564D] border border-[#D6D1C4]/50 w-full hover:bg-[#EAE6DE] transition-colors"
                                            >
                                                <FileAudio :size="14" class="shrink-0 mt-0.5 text-[#C17D46]" />
                                                <div class="min-w-0 flex-1">
                                                    <div
                                                        class="font-medium break-all text-[#2C2420]"
                                                        :title="asset.mediaFile.objectKey"
                                                    >
                                                        {{ asset.mediaFile.objectKey }}
                                                    </div>
                                                    <div
                                                        class="flex flex-wrap items-center gap-2 text-[10px] text-[#8C857B] font-mono mt-1"
                                                    >
                                                        <span class="bg-[#D6D1C4]/30 px-1 rounded-xs">{{ asset.mediaFile.mimeType }}</span>
                                                        <span class="w-px h-2 bg-[#D6D1C4]"></span>
                                                        <span>
                                                            {{
                                                                asset.mediaFile.ossProvider
                                                                    ? `OSS#${asset.mediaFile.ossProvider.id}`
                                                                    : asset.mediaFile.fsProvider
                                                                    ? `FS#${asset.mediaFile.fsProvider.id}`
                                                                    : 'UNKNOWN'
                                                            }}
                                                        </span>
                                                        <span class="w-px h-2 bg-[#D6D1C4]"></span>
                                                        <span>ID: {{ asset.mediaFile.id }}</span>
                                                    </div>
                                                </div>
                                            </div>
                                        </div>
                                    </div>
                                    <div v-else class="flex-1 flex items-center justify-center text-[#8C857B] text-xs italic opacity-70">
                                        No audio assets attached
                                    </div>
                                </div>
                            </div>

                            <!-- Right Column: Edit Form -->
                            <div class="space-y-5 flex flex-col h-full">
                                <!-- Title -->
                                <label class="block">
                                    <span
                                        class="text-xs uppercase tracking-wider text-[#8A8A8A] font-serif block mb-2"
                                        >Title</span
                                    >
                                    <input
                                        v-model="editRecordingForm.title"
                                        type="text"
                                        maxlength="255"
                                        class="w-full bg-[#F7F5F0] border-b border-[#D6D1C4] p-3 text-[#3D3D3D] focus:outline-none focus:border-[#C67C4E] transition-colors font-serif placeholder:text-[#BDB9AE]"
                                        placeholder="Recording Title"
                                        :disabled="isEditingRecording"
                                    />
                                </label>

                                <!-- Type & Label -->
                                <div class="grid grid-cols-2 gap-4">
                                    <label class="block">
                                        <span
                                            class="text-xs uppercase tracking-wider text-[#8A8A8A] font-serif block mb-2"
                                            >Type</span
                                        >
                                        <input
                                            v-model="editRecordingForm.type"
                                            type="text"
                                            maxlength="50"
                                            class="w-full bg-[#F7F5F0] border-b border-[#D6D1C4] p-3 text-[#3D3D3D] focus:outline-none focus:border-[#C67C4E] transition-colors font-serif placeholder:text-[#BDB9AE]"
                                            placeholder="e.g. Live, Studio"
                                            :disabled="isEditingRecording"
                                        />
                                    </label>
                                    <label class="block">
                                        <span
                                            class="text-xs uppercase tracking-wider text-[#8A8A8A] font-serif block mb-2"
                                            >Label</span
                                        >
                                        <input
                                            v-model="editRecordingForm.label"
                                            type="text"
                                            maxlength="50"
                                            class="w-full bg-[#F7F5F0] border-b border-[#D6D1C4] p-3 text-[#3D3D3D] focus:outline-none focus:border-[#C67C4E] transition-colors font-serif placeholder:text-[#BDB9AE]"
                                            placeholder="Optional label"
                                            :disabled="isEditingRecording"
                                        />
                                    </label>
                                </div>

                                <!-- Comment -->
                                <label class="flex-1 flex flex-col min-h-[100px]">
                                    <span
                                        class="text-xs uppercase tracking-wider text-[#8A8A8A] font-serif block mb-2"
                                        >Comment</span
                                    >
                                    <textarea
                                        v-model="editRecordingForm.comment"
                                        class="w-full flex-1 bg-[#F7F5F0] border-b border-[#D6D1C4] p-3 text-[#3D3D3D] focus:outline-none focus:border-[#C67C4E] transition-colors font-serif placeholder:text-[#BDB9AE] resize-none"
                                        placeholder="Add a comment..."
                                        :disabled="isEditingRecording"
                                    ></textarea>
                                </label>

                                <p v-if="editRecordingError" class="text-sm text-[#B95D5D]">
                                    {{ editRecordingError }}
                                </p>

                                <div class="flex gap-3 pt-4 border-t border-[#EAE6DE] mt-auto">
                                    <button
                                        type="button"
                                        class="flex-1 px-4 py-3 border border-[#D6D1C4] text-[#8A8A8A] hover:bg-[#F7F5F0] hover:text-[#5A5A5A] transition-colors text-sm uppercase tracking-wide"
                                        :disabled="isEditingRecording"
                                        @click="closeEditRecordingModal"
                                    >
                                        取消
                                    </button>
                                    <button
                                        type="button"
                                        class="flex-1 px-4 py-3 bg-[#2B221B] text-[#F7F5F0] hover:bg-[#C67C4E] transition-colors text-sm uppercase tracking-wide shadow-md disabled:opacity-60 disabled:cursor-not-allowed"
                                        :disabled="isEditingRecording"
                                        @click="submitRecordingEdit"
                                    >
                                        {{ isEditingRecording ? '保存中...' : '保存更改' }}
                                    </button>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </Transition>
        </Teleport>
    </div>
</template>
