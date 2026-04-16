<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { api, normalizeApiError } from '@/ApiInstance'
import DashboardTopBar from '@/components/dashboard/DashboardTopBar.vue'
import MediaListPanel from '@/components/MediaListPanel.vue'
import MediaListItem from '@/components/MediaListItem.vue'
import AddRecordingToPlaylistModal from '@/components/playlist/AddRecordingToPlaylistModal.vue'
import AlbumDetailHero from '@/components/album/AlbumDetailHero.vue'
import RecordingEditModal, {
    type RecordingEditForm,
    type RecordingPreview,
} from '@/components/recording/RecordingEditModal.vue'
import {
    formatDurationMs,
    normalizeRecordings,
    pickInitialRecordingId,
    resolveCover,
    type NormalizedRecordingBase,
    type RecordingAsset,
} from '@/composables/recordingMedia'
import { useRecordingPlayback } from '@/composables/useRecordingPlayback'
import {
    applyStoredItemOrder,
    buildRecordingOrderStorageKey,
    hasSameItemOrder,
    loadStoredItemOrder,
    moveItemById,
    saveStoredItemOrder,
    type ReorderPayload,
} from '@/utils/recordingOrder'

const route = useRoute()
const currentRecordingId = ref<number | null>(null)
const isLoading = ref(true)
const isCdVisible = ref(false)
const isAddToPlaylistModalOpen = ref(false)
const selectedRecordingForPlaylist = ref<Recording | null>(null)

const isEditRecordingModalOpen = ref(false)
const isEditingRecording = ref(false)
const editingRecording = ref<Recording | null>(null)
const editRecordingForm = ref<RecordingEditForm>({
    title: '',
    label: '',
    comment: '',
    type: '',
    isDefault: false,
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

type Recording = RecordingPreview & {
    id: number
    title: string
    artist: string
    label: string
    audioSrc?: string
    type: string
    comment: string
    isDefault: boolean
    durationMs: number
}

type AlbumRecordingDto = Awaited<
    ReturnType<typeof api.albumController.getAlbum>
>['recordings'][number]

const albumData = ref<AlbumData>({
    title: '',
    artist: '',
    year: '',
    type: 'Album',
    description: '',
    cover: '',
})

const recordings = ref<Recording[]>([])
const buildStorageKey = (albumId: number) => buildRecordingOrderStorageKey('album', albumId)

const {
    audioStore,
    hasPlayableRecording,
    isCurrentRecordingPlaying,
    playingId,
    handlePlay,
    onRecordingClick,
    onRecordingDoubleClick,
    onRecordingKeydown,
} = useRecordingPlayback<Recording>({
    recordings,
    currentRecordingId,
    fallbackCover: () => albumData.value.cover,
})

const fetchAlbum = async (id: number) => {
    try {
        isLoading.value = true
        isCdVisible.value = false

        const data = await api.albumController.getAlbum({ id })

        const releaseYear = data.releaseDate
            ? new Date(data.releaseDate).getFullYear().toString()
            : ''
        const artistName = data.recordings?.[0]?.artists?.[0]?.displayName ?? 'Unknown Artist'

        albumData.value = {
            title: data.title,
            artist: artistName,
            year: releaseYear,
            type: data.kind || 'Album',
            description: data.comment || '',
            cover: resolveCover(data.cover),
        }

        const normalizedRecordings = normalizeRecordings(
            (data.recordings || []) as readonly AlbumRecordingDto[],
            {
                fallbackArtist: artistName,
                transform: (recording: AlbumRecordingDto, base: NormalizedRecordingBase) => ({
                    ...base,
                    label: recording.label || '',
                    type: recording.kind,
                    comment: recording.comment,
                    durationMs: recording.durationMs,
                    rawArtists: recording.artists || [],
                    assets: (recording.assets || []) as readonly RecordingAsset[],
                    isDefault: recording.defaultInWork,
                }),
            },
        )
        const storageKey = buildStorageKey(id)
        const orderedRecordings = applyStoredItemOrder(
            normalizedRecordings,
            loadStoredItemOrder(storageKey),
        )
        recordings.value = orderedRecordings
        saveStoredItemOrder(
            storageKey,
            orderedRecordings.map((recording) => recording.id),
        )
        currentRecordingId.value = pickInitialRecordingId(recordings.value, 'first-playable')
    } catch (error) {
        console.error('Failed to fetch album details:', error)
    } finally {
        isLoading.value = false
        setTimeout(() => {
            isCdVisible.value = true
        }, 100)
    }
}

const openAddToPlaylistModal = (recording: Recording) => {
    selectedRecordingForPlaylist.value = recording
    isAddToPlaylistModalOpen.value = true
}

const closeAddToPlaylistModal = () => {
    isAddToPlaylistModalOpen.value = false
}

const buildRecordingLabel = (recording: Recording) => {
    const duration = formatDurationMs(recording.durationMs)
    if (!recording.label) {
        return duration
    }
    if (!duration) {
        return recording.label
    }
    return `${recording.label} · ${duration}`
}

const handleRecordingReorder = (payload: ReorderPayload) => {
    const albumId = Number(route.params.id)
    if (Number.isNaN(albumId)) {
        return
    }

    const nextRecordings = moveItemById(recordings.value, payload)
    if (hasSameItemOrder(recordings.value, nextRecordings)) {
        return
    }

    recordings.value = nextRecordings
    saveStoredItemOrder(
        buildStorageKey(albumId),
        nextRecordings.map((recording) => recording.id),
    )
}

const openEditRecordingModal = (recording: Recording) => {
    if (isEditingRecording.value) {
        return
    }

    editingRecording.value = recording
    editRecordingForm.value = {
        title: recording.title,
        label: recording.label,
        comment: recording.comment,
        type: recording.type,
        isDefault: recording.isDefault,
    }
    editRecordingError.value = ''
    isEditRecordingModalOpen.value = true
}

const closeEditRecordingModal = () => {
    if (isEditingRecording.value) {
        return
    }

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

const updateEditRecordingForm = (value: RecordingEditForm) => {
    editRecordingForm.value = value
}

const submitRecordingEdit = async () => {
    if (!editingRecording.value || isEditingRecording.value) {
        return
    }

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

        const index = recordings.value.findIndex(
            (recording) => recording.id === editingRecording.value!.id,
        )
        if (index !== -1) {
            const current = recordings.value[index]
            if (current) {
                recordings.value[index] = {
                    ...current,
                    title: title.trim(),
                    label: label?.trim() || '',
                    comment: comment?.trim() || '',
                    type: type.trim(),
                }
            }
        }

        closeEditRecordingModal()
    } catch (error) {
        const normalized = normalizeApiError(error)
        editRecordingError.value = normalized.message ?? '更新曲目失败'
    } finally {
        isEditingRecording.value = false
    }
}

onMounted(() => {
    const id = Number(route.params.id)
    if (!Number.isNaN(id)) {
        void fetchAlbum(id)
    }
})

watch(
    () => route.params.id,
    (newId) => {
        const id = Number(newId)
        if (!Number.isNaN(id)) {
            void fetchAlbum(id)
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

        <div v-else class="mx-auto w-full max-w-5xl px-4 pb-32 sm:px-6 lg:px-8">
            <AlbumDetailHero
                :album-data="albumData"
                :is-cd-visible="isCdVisible"
                :has-playable-recording="hasPlayableRecording"
                :is-current-playing="isCurrentRecordingPlaying"
                @play="handlePlay()"
            />

            <MediaListPanel
                title="Tracks"
                :summary="`${recordings.length} Tracks`"
                :items="recordings"
                :playing-id="playingId"
                enable-reorder
                @item-double-click="onRecordingDoubleClick"
                @item-keydown="onRecordingKeydown"
                @item-reorder="handleRecordingReorder"
            >
                <template #actions>
                    <span class="text-[11px] uppercase tracking-[0.24em] text-[#B0AAA0]">
                        拖拽排序 · 当前设备
                    </span>
                </template>
                <template #item="{ item }">
                    <MediaListItem
                        :title="item.title"
                        :label="buildRecordingLabel(item)"
                        :show-add-button="true"
                        :show-edit-button="true"
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

        <RecordingEditModal
            :open="isEditRecordingModalOpen"
            :recording="editingRecording"
            :form="editRecordingForm"
            :error="editRecordingError"
            :is-saving="isEditingRecording"
            :show-default-toggle="false"
            @update:form="updateEditRecordingForm"
            @close="closeEditRecordingModal"
            @submit="submitRecordingEdit"
        />
    </div>
</template>
