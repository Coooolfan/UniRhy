<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { api, normalizeApiError } from '@/ApiInstance'
import DashboardTopBar from '@/components/dashboard/DashboardTopBar.vue'
import MediaListPanel from '@/components/MediaListPanel.vue'
import MediaListItem from '@/components/MediaListItem.vue'
import { useModal } from '@/composables/useModal'
import AddRecordingToPlaylistModal from '@/components/playlist/AddRecordingToPlaylistModal.vue'
import AlbumDetailHero from '@/components/album/AlbumDetailHero.vue'
import AlbumEditModal, { type AlbumEditForm } from '@/components/album/AlbumEditModal.vue'
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
import { hasSameItemOrder, moveItemById, type ReorderPayload } from '@/utils/recordingOrder'

const route = useRoute()
const modal = useModal()
const currentRecordingId = ref<number | null>(null)
const isLoading = ref(true)
const isCdVisible = ref(false)
const isReorderingRecordings = ref(false)
const reorderRecordingError = ref('')

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

const albumEditInitial = ref<AlbumEditForm>({
    title: '',
    kind: '',
    releaseDate: '',
    comment: '',
})

const recordings = ref<Recording[]>([])

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
        reorderRecordingError.value = ''

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

        albumEditInitial.value = {
            title: data.title,
            kind: data.kind ?? '',
            releaseDate: data.releaseDate ? data.releaseDate.slice(0, 10) : '',
            comment: data.comment ?? '',
        }

        recordings.value = normalizeRecordings(
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
    void modal.open(AddRecordingToPlaylistModal, {
        title: '添加到歌单',
        size: 'sm',
        props: {
            recordingId: recording.id,
        },
    })
}

const openEditAlbumModal = async () => {
    const albumId = Number(route.params.id)
    if (Number.isNaN(albumId)) {
        return
    }

    await modal.open(AlbumEditModal, {
        title: '编辑专辑',
        size: 'md',
        props: {
            initialForm: { ...albumEditInitial.value },
            onSubmit: async (formValue: AlbumEditForm) => {
                await api.albumController.updateAlbum({
                    id: albumId,
                    body: {
                        title: formValue.title,
                        kind: formValue.kind,
                        releaseDate: formValue.releaseDate || undefined,
                        comment: formValue.comment,
                    },
                })
                await fetchAlbum(albumId)
            },
        },
    })
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

const handleRecordingReorder = async (payload: ReorderPayload) => {
    const albumId = Number(route.params.id)
    if (Number.isNaN(albumId) || isReorderingRecordings.value) {
        return
    }

    const previousRecordings = [...recordings.value]
    const nextRecordings = moveItemById(previousRecordings, payload)
    if (hasSameItemOrder(previousRecordings, nextRecordings)) {
        return
    }

    recordings.value = nextRecordings
    isReorderingRecordings.value = true
    reorderRecordingError.value = ''

    try {
        await api.albumController.reorderAlbumRecordings({
            id: albumId,
            body: {
                recordingIds: nextRecordings.map((recording) => recording.id),
            },
        })
    } catch (error) {
        recordings.value = previousRecordings
        const normalized = normalizeApiError(error)
        reorderRecordingError.value = normalized.message ?? '调整曲目顺序失败'
    } finally {
        isReorderingRecordings.value = false
    }
}

const openEditRecordingModal = async (recording: Recording) => {
    await modal.open(RecordingEditModal, {
        size: 'xl',
        props: {
            recording,
            initialForm: {
                title: recording.title,
                label: recording.label,
                comment: recording.comment,
                type: recording.type,
                isDefault: recording.isDefault,
            } satisfies RecordingEditForm,
            showDefaultToggle: false,
            onSubmit: async ({ title, label, comment, type }: RecordingEditForm) => {
                await api.recordingController.updateRecording({
                    id: recording.id,
                    body: {
                        title: title.trim(),
                        label: label?.trim(),
                        comment: comment?.trim() || '',
                        kind: type.trim(),
                    },
                })

                const index = recordings.value.findIndex((item) => item.id === recording.id)
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
            },
        },
    })
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
                @edit="openEditAlbumModal"
            />

            <MediaListPanel
                title="Tracks"
                :summary="`${recordings.length} Tracks`"
                :items="recordings"
                :playing-id="playingId"
                enable-reorder
                :reorder-disabled="isReorderingRecordings"
                @item-double-click="onRecordingDoubleClick"
                @item-keydown="onRecordingKeydown"
                @item-reorder="handleRecordingReorder"
            >
                <template #actions>
                    <span class="text-[11px] uppercase tracking-[0.24em] text-[#B0AAA0]">
                        {{ isReorderingRecordings ? '正在保存顺序' : '' }}
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

            <p v-if="reorderRecordingError" class="mt-4 text-sm text-[#B95D5D]">
                {{ reorderRecordingError }}
            </p>
        </div>
    </div>
</template>
