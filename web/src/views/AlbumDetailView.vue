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
import { useRecordingEditor } from '@/composables/useRecordingEditor'
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

const applyRecordingEdit = (
    currentRecordings: readonly Recording[],
    recordingId: number,
    form: RecordingEditForm,
) =>
    currentRecordings.map((recording) => {
        if (recording.id !== recordingId) {
            return recording
        }

        return {
            ...recording,
            title: form.title,
            label: form.label,
            comment: form.comment,
            type: form.type,
            isDefault: form.isDefault,
        }
    })

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

const {
    isEditRecordingModalOpen,
    isEditingRecording,
    editingRecording,
    editRecordingForm,
    editRecordingError,
    openEditRecordingModal,
    closeEditRecordingModal,
    updateEditRecordingForm,
    submitRecordingEdit,
} = useRecordingEditor<Recording>({
    recordings,
    submitUpdate: (recordingId, form) =>
        api.recordingController.updateRecording({
            id: recordingId,
            body: {
                title: form.title,
                label: form.label,
                comment: form.comment,
                kind: form.type,
            },
        }),
    applyLocalUpdate: applyRecordingEdit,
    parseError: (error) => normalizeApiError(error).message ?? '更新曲目失败',
    fallbackErrorMessage: '更新曲目失败',
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
            :recording-id="editingRecording?.id ?? null"
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
