<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { api, normalizeApiError } from '@/ApiInstance'
import DashboardTopBar from '@/components/dashboard/DashboardTopBar.vue'
import MediaListPanel from '@/components/MediaListPanel.vue'
import MediaListItem from '@/components/MediaListItem.vue'
import AddRecordingToPlaylistModal from '@/components/playlist/AddRecordingToPlaylistModal.vue'
import WorkDetailHero from '@/components/work/WorkDetailHero.vue'
import WorkTitleEditModal from '@/components/work/WorkTitleEditModal.vue'
import RecordingEditModal, {
    type RecordingEditForm,
    type RecordingPreview,
} from '@/components/recording/RecordingEditModal.vue'
import { resolveAudio, resolveCover, type RecordingAsset } from '@/composables/recordingMedia'
import { useRecordingPlayback } from '@/composables/useRecordingPlayback'

const route = useRoute()
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
const editRecordingForm = ref<RecordingEditForm>({
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

type Recording = RecordingPreview & {
    id: number
    title: string
    artist: string
    type: string
    label: string
    comment: string
    cover: string
    isDefault: boolean
    audioSrc?: string
}

const workData = ref<WorkData>({
    title: '',
    artist: '',
    cover: '',
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
    fallbackCover: () => workData.value.cover,
    trackExtra: () => {
        const workId = Number(route.params.id)
        if (Number.isNaN(workId)) {
            return {}
        }
        return { workId }
    },
})

const fetchWork = async (id: number) => {
    try {
        isLoading.value = true

        const data = await api.workController.getWorkById({ id })

        const defaultRecording =
            data.recordings?.find((recording) => recording.defaultInWork) ?? data.recordings?.[0]
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
                artist: recording.artists.map((artist) => artist.name).join(', '),
                type: recording.kind,
                label: recording.label || '',
                comment: recording.comment,
                cover: resolveCover(recording.cover?.id),
                isDefault: recording.defaultInWork,
                audioSrc: resolveAudio((recording.assets || []) as readonly RecordingAsset[]),
                assets: (recording.assets || []) as readonly RecordingAsset[],
                rawArtists: recording.artists || [],
            }))
            .sort((left, right) => {
                if (left.isDefault === right.isDefault) {
                    return 0
                }
                return left.isDefault ? -1 : 1
            })

        if (recordings.value.length > 0) {
            const defaultRec = recordings.value.find((recording) => recording.isDefault)
            currentRecordingId.value = defaultRec ? defaultRec.id : recordings.value[0]!.id
        } else {
            currentRecordingId.value = null
        }
    } catch (error) {
        console.error('Failed to fetch work details:', error)
    } finally {
        isLoading.value = false
    }
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

const submitEdit = async () => {
    const id = Number(route.params.id)
    const title = editTitle.value.trim()

    if (Number.isNaN(id)) {
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
                    isDefault,
                }
            }
        }

        if (isDefault) {
            recordings.value.forEach((recording, recordingIndex) => {
                if (recordingIndex !== index && recording.isDefault) {
                    recordings.value[recordingIndex] = {
                        ...recording,
                        isDefault: false,
                    }
                }
            })
        }

        recordings.value.sort((left, right) => {
            if (left.isDefault === right.isDefault) {
                return 0
            }
            return left.isDefault ? -1 : 1
        })

        closeEditRecordingModal()
    } catch (error) {
        const normalized = normalizeApiError(error)
        editRecordingError.value = normalized.message ?? '更新录音失败'
    } finally {
        isEditingRecording.value = false
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
    if (!Number.isNaN(id)) {
        void fetchWork(id)
    }
})

watch(
    () => route.params.id,
    (newId) => {
        const id = Number(newId)
        if (!Number.isNaN(id)) {
            void fetchWork(id)
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
            <WorkDetailHero
                :work-data="workData"
                :recordings="recordings"
                :has-playable-recording="hasPlayableRecording"
                :is-current-playing="isCurrentRecordingPlaying"
                @play="handlePlay()"
                @edit-work="openEditModal"
            />

            <MediaListPanel
                title="Recordings"
                summary="Versions & Interpretations"
                :items="recordings"
                :active-id="currentRecordingId"
                :playing-id="playingId"
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

        <WorkTitleEditModal
            :open="isEditModalOpen"
            :title="editTitle"
            :error="editError"
            :is-saving="isEditing"
            @update:title="editTitle = $event"
            @cancel="closeEditModal"
            @save="submitEdit"
        />

        <RecordingEditModal
            :open="isEditRecordingModalOpen"
            :recording="editingRecording"
            :form="editRecordingForm"
            :error="editRecordingError"
            :is-saving="isEditingRecording"
            :show-default-toggle="true"
            @update:form="updateEditRecordingForm"
            @close="closeEditRecordingModal"
            @submit="submitRecordingEdit"
        />
    </div>
</template>
