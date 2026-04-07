<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { api, normalizeApiError } from '@/ApiInstance'
import DashboardTopBar from '@/components/dashboard/DashboardTopBar.vue'
import MediaListPanel from '@/components/MediaListPanel.vue'
import MediaListItem from '@/components/MediaListItem.vue'
import MergeSelectModal from '@/components/modals/MergeSelectModal.vue'
import AddRecordingToPlaylistModal from '@/components/playlist/AddRecordingToPlaylistModal.vue'
import RecordingEditModal, {
    type RecordingEditForm,
    type RecordingPreview,
} from '@/components/recording/RecordingEditModal.vue'
import WorkDetailHero from '@/components/work/WorkDetailHero.vue'
import WorkTitleEditModal from '@/components/work/WorkTitleEditModal.vue'
import {
    formatDurationMs,
    normalizeRecordings,
    pickInitialRecordingId,
    resolveCover,
    type RecordingAsset,
} from '@/composables/recordingMedia'
import { useRecordingEditor } from '@/composables/useRecordingEditor'
import { useRecordingMergeState } from '@/composables/useRecordingMergeState'
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
const isAddToPlaylistModalOpen = ref(false)
const selectedRecordingForPlaylist = ref<Recording | null>(null)

const isEditModalOpen = ref(false)
const isEditing = ref(false)
const editTitle = ref('')
const editError = ref('')

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
    durationMs: number
}

type WorkRecordingDto = Awaited<
    ReturnType<typeof api.workController.getWorkById>
>['recordings'][number]

const workData = ref<WorkData>({
    title: '',
    artist: '',
    cover: '',
})

const recordings = ref<Recording[]>([])
const mergeStateActions: { resetState: () => void } = {
    resetState: () => undefined,
}

const buildStorageKey = (workId: number) => buildRecordingOrderStorageKey('work', workId)

const applyRecordingEdit = (
    currentRecordings: readonly Recording[],
    recordingId: number,
    form: RecordingEditForm,
) => {
    return currentRecordings.map((recording) => {
        if (recording.id === recordingId) {
            return {
                ...recording,
                title: form.title,
                label: form.label,
                comment: form.comment,
                type: form.type,
                isDefault: form.isDefault,
            }
        }

        if (form.isDefault && recording.isDefault) {
            return {
                ...recording,
                isDefault: false,
            }
        }

        return recording
    })
}

async function fetchWork(id: number) {
    try {
        isLoading.value = true

        const data = await api.workController.getWorkById({ id })

        const defaultRecording =
            data.recordings?.find((recording) => recording.defaultInWork) ?? data.recordings?.[0]
        const artistName = defaultRecording?.artists?.[0]?.displayName ?? 'Unknown Artist'

        workData.value = {
            title: data.title,
            artist: artistName,
            cover: resolveCover(defaultRecording?.cover),
        }

        const normalizedRecordings = normalizeRecordings(
            (data.recordings || []) as readonly WorkRecordingDto[],
            {
                transform: (recording, base) => ({
                    ...base,
                    type: recording.kind,
                    label: recording.label || '',
                    comment: recording.comment,
                    isDefault: recording.defaultInWork,
                    durationMs: recording.durationMs,
                    assets: (recording.assets || []) as readonly RecordingAsset[],
                    rawArtists: recording.artists || [],
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

        currentRecordingId.value = pickInitialRecordingId(recordings.value, 'default-first')

        mergeStateActions.resetState()
    } catch (error) {
        console.error('Failed to fetch work details:', error)
    } finally {
        isLoading.value = false
    }
}

const {
    audioStore,
    hasPlayableRecording,
    isCurrentRecordingPlaying,
    playingId,
    handlePlay,
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
                defaultInWork: form.isDefault,
            },
        }),
    applyLocalUpdate: applyRecordingEdit,
    parseError: (error) => normalizeApiError(error).message ?? '更新曲目失败',
    fallbackErrorMessage: '更新曲目失败',
})

const mergeState = useRecordingMergeState<Recording>({
    recordings,
    buildOption: (recording) => ({
        id: recording.id,
        title: recording.title,
        subtitle: `${recording.artist}${recording.type ? ' · ' + recording.type : ''}`,
    }),
    mergeRequest: async ({ targetId, sourceIds }) => {
        const workId = Number(route.params.id)
        if (Number.isNaN(workId)) {
            return
        }

        await api.recordingController.mergeRecording({
            body: {
                targetId,
                needMergeIds: sourceIds,
            },
        })
        await fetchWork(workId)
    },
    parseError: (error) => normalizeApiError(error).message ?? '合并曲目失败',
    fallbackErrorMessage: '合并曲目失败',
})

const {
    selectedIds: selectedRecordingIds,
    selectedOptions: selectedRecordingOptions,
    hasEnoughSelectedItems: hasEnoughSelectedRecordings,
    canSubmitMerge: canSubmitRecordingMerge,
    mergeModalOpen,
    mergeTargetId: mergeTargetRecordingId,
    mergeModalError,
    mergeSubmitting,
    toggleSelection: toggleRecordingSelection,
    openMergeModal,
    closeMergeModal,
    submitMerge: submitRecordingMerge,
    resetState: resetMergeState,
} = mergeState
mergeStateActions.resetState = resetMergeState

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

const buildRecordingSubtitle = (recording: Recording) => {
    const parts = [recording.artist, recording.type].filter(Boolean)
    return parts.join(' · ')
}

const handleRecordingReorder = (payload: ReorderPayload) => {
    const workId = Number(route.params.id)
    if (Number.isNaN(workId)) {
        return
    }

    const nextRecordings = moveItemById(recordings.value, payload)
    if (hasSameItemOrder(recordings.value, nextRecordings)) {
        return
    }

    recordings.value = nextRecordings
    saveStoredItemOrder(
        buildStorageKey(workId),
        nextRecordings.map((recording) => recording.id),
    )
}

onMounted(() => {
    const id = Number(route.params.id)
    if (!Number.isNaN(id)) {
        resetMergeState()
        void fetchWork(id)
    }
})

watch(
    () => route.params.id,
    (newId) => {
        const id = Number(newId)
        if (!Number.isNaN(id)) {
            resetMergeState()
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

        <div v-else class="mx-auto w-full max-w-5xl px-4 pb-32 sm:px-6 lg:px-8">
            <WorkDetailHero
                :work-data="workData"
                :recordings="recordings"
                :has-playable-recording="hasPlayableRecording"
                :is-current-playing="isCurrentRecordingPlaying"
                @play="handlePlay()"
                @edit-work="openEditModal"
            />

            <MediaListPanel
                title="Tracks"
                :items="recordings"
                :playing-id="playingId"
                enable-multi-select
                enable-reorder
                :selected-ids="selectedRecordingIds"
                @item-double-click="onRecordingDoubleClick"
                @item-toggle-select="toggleRecordingSelection"
                @item-keydown="onRecordingKeydown"
                @item-reorder="handleRecordingReorder"
            >
                <template #actions>
                    <span class="text-[11px] uppercase tracking-[0.24em] text-[#B0AAA0]">
                        拖拽排序 · 当前设备
                    </span>
                    <button
                        v-if="hasEnoughSelectedRecordings"
                        type="button"
                        data-testid="open-merge-recording-button"
                        class="px-3 py-1 border border-[#C27E46] text-[#C27E46] text-xs tracking-wide transition-colors hover:bg-[#C27E46] hover:text-white uppercase"
                        @click="openMergeModal"
                    >
                        合并
                    </button>
                </template>
                <template #item="{ item }">
                    <MediaListItem
                        :title="item.title"
                        :label="buildRecordingLabel(item)"
                        :cover="item.cover"
                        :show-add-button="true"
                        :show-edit-button="true"
                        :is-default="item.isDefault"
                        :subtitle="buildRecordingSubtitle(item)"
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
            :recording-id="editingRecording?.id ?? null"
            :form="editRecordingForm"
            :error="editRecordingError"
            :is-saving="isEditingRecording"
            :show-default-toggle="true"
            @update:form="updateEditRecordingForm"
            @close="closeEditRecordingModal"
            @submit="submitRecordingEdit"
        />

        <MergeSelectModal
            :open="mergeModalOpen"
            title="合并曲目"
            description="请选择保留的目标曲目，其余已选曲目将合并到该曲目。"
            :options="selectedRecordingOptions"
            :target-id="mergeTargetRecordingId"
            :error="mergeModalError"
            :note="'来源曲目的音频资源、专辑关联、歌单关联、艺人关联会并入目标曲目，来源曲目将被删除；Default 标记按后端结果保持原样。'"
            :submitting="mergeSubmitting"
            :confirm-disabled="!canSubmitRecordingMerge"
            modal-test-id="recording-merge-modal"
            option-radio-test-id="recording-merge-target-radio"
            confirm-test-id="submit-recording-merge-button"
            @update:target-id="mergeTargetRecordingId = $event"
            @close="closeMergeModal"
            @confirm="submitRecordingMerge"
        />
    </div>
</template>
