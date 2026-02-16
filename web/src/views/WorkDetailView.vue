<script setup lang="ts">
import { onMounted, ref, watch, computed } from 'vue'
import { useRoute } from 'vue-router'
import { Play, Pause, Pencil, Music, Disc, FileAudio, Users, Image as ImageIcon } from 'lucide-vue-next'
import { api, normalizeApiError } from '@/ApiInstance'
import { useAudioStore } from '@/stores/audio'
import DashboardTopBar from '@/components/dashboard/DashboardTopBar.vue'
import MediaListPanel from '@/components/MediaListPanel.vue'
import StackedCovers from '@/components/StackedCovers.vue'
import MediaListItem from '@/components/MediaListItem.vue'
import AddRecordingToPlaylistModal from '@/components/playlist/AddRecordingToPlaylistModal.vue'

const route = useRoute()
const audioStore = useAudioStore()
const currentRecordingId = ref<number | null>(null)
const isLoading = ref(true)
const isAddToPlaylistModalOpen = ref(false)
const selectedRecordingForPlaylist = ref<Recording | null>(null)
const isEditModalOpen = ref(false)
const isEditing = ref(false)
const editTitle = ref('')
const editError = ref('')

const isEditRecordingModalOpen = ref(false)
const isEditingRecording = ref(false)
const editingRecording = ref<Recording | null>(null)
const editRecordingForm = ref({
    title: '',
    label: '',
    comment: '',
    type: '',
    isDefault: false,
})
const editRecordingError = ref('')

type WorkData = {
    title: string
    artist: string
    cover: string
}

type Recording = {
    id: number
    title: string
    artist: string
    type: string
    label: string
    comment: string
    cover: string
    isDefault: boolean
    audioSrc?: string
    assets: readonly Asset[]
    rawArtists: readonly { readonly id: number; readonly name: string }[]
}

const workData = ref<WorkData>({
    title: '',
    artist: '',
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
    const audioAsset = assets.find((a) => a.mediaFile.mimeType.startsWith('audio/'))
    if (audioAsset) {
        return `/api/media/${audioAsset.mediaFile.id}`
    }
    return undefined
}

const fetchWork = async (id: number) => {
    try {
        isLoading.value = true

        const data = await api.workController.getWorkById({ id })

        const defaultRecording =
            data.recordings?.find((r) => r.defaultInWork) ?? data.recordings?.[0]
        const artistName = defaultRecording?.artists?.[0]?.name ?? 'Unknown Artist'

        workData.value = {
            title: data.title,
            artist: artistName,
            cover: resolveCover(defaultRecording?.cover?.id),
        }

        recordings.value = (data.recordings || [])
            .map((recording) => ({
                id: recording.id,
                title: recording.title || recording.comment || 'Untitled Recording',
                artist: recording.artists.map((a) => a.name).join(', '),
                type: recording.kind,
                label: recording.label || '',
                comment: recording.comment,
                cover: resolveCover(recording.cover?.id),
                isDefault: recording.defaultInWork,
                audioSrc: resolveAudio(recording.assets || []),
                assets: recording.assets || [],
                rawArtists: recording.artists || [],
            }))
            .sort((a, b) => {
                if (a.isDefault === b.isDefault) return 0
                return a.isDefault ? -1 : 1
            })

        if (recordings.value.length > 0) {
            // Select default recording or the first one
            const defaultRec = recordings.value.find((r) => r.isDefault)
            currentRecordingId.value = defaultRec ? defaultRec.id : recordings.value[0]!.id
        }
    } catch (error) {
        console.error('Failed to fetch work details:', error)
    } finally {
        isLoading.value = false
    }
}

// Computed to check if the currently selected recording is playing
const isCurrentPlaying = computed(() => {
    return audioStore.isPlaying && audioStore.currentTrack?.id === currentRecordingId.value
})

const handlePlay = (rec?: Recording) => {
    const targetId = rec?.id ?? currentRecordingId.value
    if (!targetId) return

    const targetRec = recordings.value.find((r) => r.id === targetId)
    if (!targetRec || !targetRec.audioSrc) {
        console.warn('No audio source for recording', targetId)
        return
    }

    audioStore.play({
        id: targetRec.id,
        title: targetRec.title,
        artist: targetRec.artist,
        cover: targetRec.cover || workData.value.cover,
        src: targetRec.audioSrc,
        workId: Number(route.params.id),
    })
}

const onRecordingKeydown = (event: KeyboardEvent, rec: Recording) => {
    if (event.key === 'Enter' || event.key === ' ') {
        event.preventDefault()
        onRecordingDoubleClick(rec)
    }
}

// Handle clicking on a recording in the list
const onRecordingClick = (rec: Recording) => {
    currentRecordingId.value = rec.id
}

const onRecordingDoubleClick = (rec: Recording) => {
    currentRecordingId.value = rec.id
    handlePlay(rec)
}

const openEditModal = () => {
    if (isEditing.value) {
        return
    }

    editTitle.value = workData.value.title
    editError.value = ''
    isEditModalOpen.value = true
}

const closeEditModal = () => {
    if (isEditing.value) {
        return
    }

    isEditModalOpen.value = false
    editTitle.value = ''
    editError.value = ''
}

const openEditRecordingModal = (rec: Recording) => {
    if (isEditingRecording.value) return

    editingRecording.value = rec
    editRecordingForm.value = {
        title: rec.title,
        label: rec.label,
        comment: rec.comment,
        type: rec.type,
        isDefault: rec.isDefault,
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
        isDefault: false,
    }
    editRecordingError.value = ''
}

const submitRecordingEdit = async () => {
    if (!editingRecording.value || isEditingRecording.value) return

    const { title, label, comment, type, isDefault } = editRecordingForm.value

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
                defaultInWork: isDefault,
            },
        })

        // Update local state
        const index = recordings.value.findIndex((r) => r.id === editingRecording.value!.id)
        if (index !== -1) {
            const current = recordings.value[index]
            if (current) {
                recordings.value[index] = {
                    ...current,
                    title: title.trim(),
                    label: label?.trim() || '',
                    comment: comment?.trim() || '',
                    type: type.trim(),
                    isDefault: isDefault,
                }
            }
        }

        // If this became default, unset others
        if (isDefault) {
            recordings.value.forEach((r, i) => {
                if (i !== index && r.isDefault) {
                    recordings.value[i] = { ...r, isDefault: false }
                }
            })
        }

        // Re-sort recordings to keep default at top
        recordings.value.sort((a, b) => {
            if (a.isDefault === b.isDefault) return 0
            return a.isDefault ? -1 : 1
        })

        isEditingRecording.value = false
        closeEditRecordingModal()
    } catch (error) {
        const normalized = normalizeApiError(error)
        editRecordingError.value = normalized.message ?? '更新录音失败'
    } finally {
        isEditingRecording.value = false
    }
}

const submitEdit = async () => {
    const id = Number(route.params.id)
    const title = editTitle.value.trim()

    if (isNaN(id)) {
        return
    }
    if (!title) {
        editError.value = '作品标题不能为空。'
        return
    }
    if (isEditing.value) {
        return
    }

    isEditing.value = true
    editError.value = ''
    try {
        const updated = await api.workController.updateWork({
            id,
            body: { title },
        })
        workData.value = {
            ...workData.value,
            title: updated.title || title,
        }
        isEditModalOpen.value = false
    } catch (error) {
        const normalized = normalizeApiError(error)
        editError.value = normalized.message ?? '更新作品失败'
    } finally {
        isEditing.value = false
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
        fetchWork(id)
    }
})

watch(
    () => route.params.id,
    (newId) => {
        const id = Number(newId)
        if (!isNaN(id)) {
            fetchWork(id)
        }
    },
)
</script>

<template>
    <div class="flex-1 flex flex-col h-full relative">
        <DashboardTopBar />

        <div v-if="isLoading" class="flex-1 flex items-center justify-center text-[#8C857B]">
            Loading...
        </div>

        <div v-else class="px-8 pb-32 max-w-5xl mx-auto w-full">
            <!-- 头部卡片 -->
            <div class="mt-8 flex flex-col md:flex-row gap-12 md:gap-32 items-end mb-16 group">
                <!-- 封面艺术 -->
                <div class="ml-8 mt-4 w-64 h-64 md:w-80 md:h-80 shrink-0">
                    <StackedCovers :items="recordings" :default-cover="workData.cover" />
                </div>

                <!-- 信息 -->
                <div class="flex flex-col gap-4 pb-2 w-full relative z-10">
                    <div
                        class="flex items-center gap-3 text-sm tracking-wider uppercase text-[#8C857B]"
                    >
                        <span>Musical Work</span>
                        <button
                            class="p-1 text-[#8C857B] hover:text-[#C17D46] transition-all opacity-0 group-hover:opacity-100 cursor-pointer"
                            @click="openEditModal"
                            title="编辑作品"
                        >
                            <Pencil :size="14" />
                        </button>
                    </div>

                    <h1 class="text-5xl md:text-7xl font-serif text-[#2C2420] leading-tight">
                        {{ workData.title }}
                    </h1>

                    <div class="text-xl text-[#5E564D] font-serif italic mb-2">
                        Originally by {{ workData.artist }}
                    </div>

                    <div class="flex items-center gap-4 mt-4">
                        <button
                            @click="handlePlay()"
                            class="px-8 py-3 border border-[#C17D46] text-[#C17D46] hover:bg-[#C17D46] hover:text-white transition-all duration-300 flex items-center gap-2 text-sm tracking-widest uppercase font-medium rounded-sm cursor-pointer"
                        >
                            <Pause v-if="isCurrentPlaying" :size="16" />
                            <Play v-else :size="16" fill="currentColor" />
                            {{ isCurrentPlaying ? '暂停播放' : '立即播放' }}
                        </button>
                    </div>
                </div>
            </div>

            <MediaListPanel
                title="Recordings"
                summary="Versions & Interpretations"
                :items="recordings"
                :active-id="currentRecordingId"
                :playing-id="audioStore.isPlaying ? (audioStore.currentTrack?.id ?? null) : null"
                @item-click="onRecordingClick"
                @item-double-click="onRecordingDoubleClick"
                @item-keydown="onRecordingKeydown"
            >
                <template #item="{ item, isActive }">
                    <MediaListItem
                        :title="item.title"
                        :label="item.label"
                        :cover="item.cover"
                        :show-add-button="true"
                        :show-edit-button="true"
                        :is-default="item.isDefault"
                        :subtitle="`${item.artist}${item.type ? ' · ' + item.type : ''}`"
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
                            <h3 class="font-serif text-2xl text-[#2B221B]">编辑作品</h3>
                            <p class="text-xs text-[#8A8A8A] mt-2 font-serif italic">Edit Work</p>
                        </div>

                        <div class="space-y-6">
                            <label class="block">
                                <span
                                    class="text-xs uppercase tracking-wider text-[#8A8A8A] font-serif block mb-2"
                                >
                                    Title
                                </span>
                                <input
                                    v-model="editTitle"
                                    type="text"
                                    maxlength="255"
                                    class="w-full bg-[#F7F5F0] border-b border-[#D6D1C4] p-3 text-[#3D3D3D] focus:outline-none focus:border-[#C67C4E] transition-colors font-serif placeholder:text-[#BDB9AE]"
                                    placeholder="e.g. New Work Title"
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
                                    {{ isEditing ? '更新中...' : '保存更改' }}
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            </Transition>
        </Teleport>

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

                                <!-- Default Checkbox -->
                                <label class="flex items-center gap-3 cursor-pointer group py-1">
                                    <div class="relative flex items-center">
                                        <input
                                            v-model="editRecordingForm.isDefault"
                                            type="checkbox"
                                            class="peer sr-only"
                                            :disabled="isEditingRecording"
                                        />
                                        <div
                                            class="w-5 h-5 border border-[#D6D1C4] bg-[#F7F5F0] peer-checked:bg-[#C67C4E] peer-checked:border-[#C67C4E] transition-colors"
                                        ></div>
                                        <svg
                                            class="absolute inset-0 w-5 h-5 text-white opacity-0 peer-checked:opacity-100 transition-opacity pointer-events-none"
                                            viewBox="0 0 24 24"
                                            fill="none"
                                            stroke="currentColor"
                                            stroke-width="3"
                                            stroke-linecap="round"
                                            stroke-linejoin="round"
                                        >
                                            <polyline points="20 6 9 17 4 12"></polyline>
                                        </svg>
                                    </div>
                                    <span
                                        class="text-sm text-[#5E564D] group-hover:text-[#2C2420] transition-colors"
                                        >默认版本 (Default Version)</span
                                    >
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
