<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
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
const selectedRecordingIds = ref<Set<number>>(new Set())
const lastSelectedRecordingId = ref<number | null>(null)
const mergeModalOpen = ref(false)
const mergeTargetRecordingId = ref<number | null>(null)
const mergeModalError = ref('')
const mergeSubmitting = ref(false)

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

type SelectedRecordingOption = {
    id: number
    title: string
    subtitle: string
}

const workData = ref<WorkData>({
    title: '',
    artist: '',
    cover: '',
})

const recordings = ref<Recording[]>([])
const selectedRecordingOptions = computed<SelectedRecordingOption[]>(() =>
    recordings.value
        .filter((recording) => selectedRecordingIds.value.has(recording.id))
        .map((recording) => ({
            id: recording.id,
            title: recording.title,
            subtitle: `${recording.artist}${recording.type ? ' · ' + recording.type : ''}`,
        })),
)
const hasEnoughSelectedRecordings = computed(() => selectedRecordingOptions.value.length >= 2)
const canSubmitRecordingMerge = computed(
    () =>
        !mergeSubmitting.value &&
        selectedRecordingOptions.value.length >= 2 &&
        mergeTargetRecordingId.value !== null,
)

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

const resetMergeState = () => {
    selectedRecordingIds.value = new Set()
    lastSelectedRecordingId.value = null
    mergeModalOpen.value = false
    mergeTargetRecordingId.value = null
    mergeModalError.value = ''
    mergeSubmitting.value = false
}

const isRecordingSelected = (recording: Recording) => selectedRecordingIds.value.has(recording.id)

const toggleRecordingSelection = (recording: Recording, event?: MouseEvent) => {
    const nextSelected = new Set(selectedRecordingIds.value)

    if (event?.shiftKey && lastSelectedRecordingId.value !== null) {
        const lastIndex = recordings.value.findIndex((r) => r.id === lastSelectedRecordingId.value)
        const currentIndex = recordings.value.findIndex((r) => r.id === recording.id)

        if (lastIndex !== -1 && currentIndex !== -1) {
            const start = Math.min(lastIndex, currentIndex)
            const end = Math.max(lastIndex, currentIndex)

            for (let i = start; i <= end; i++) {
                const rec = recordings.value[i]
                if (rec) {
                    nextSelected.add(rec.id)
                }
            }
        }
    } else {
        if (nextSelected.has(recording.id)) {
            nextSelected.delete(recording.id)
        } else {
            nextSelected.add(recording.id)
        }
        lastSelectedRecordingId.value = recording.id
    }

    selectedRecordingIds.value = nextSelected
}

const openMergeModal = () => {
    if (!hasEnoughSelectedRecordings.value) {
        return
    }
    mergeModalError.value = ''
    mergeTargetRecordingId.value = selectedRecordingOptions.value[0]?.id ?? null
    mergeModalOpen.value = true
}

const closeMergeModal = () => {
    if (mergeSubmitting.value) {
        return
    }
    mergeModalOpen.value = false
    mergeModalError.value = ''
    mergeTargetRecordingId.value = null
}

const submitRecordingMerge = async () => {
    if (selectedRecordingOptions.value.length < 2) {
        mergeModalError.value = '至少选择 2 条录音后才能合并。'
        return
    }

    if (mergeTargetRecordingId.value === null) {
        mergeModalError.value = '请选择一个目标录音。'
        return
    }

    const sourceRecordingIds = selectedRecordingOptions.value
        .map((recording) => recording.id)
        .filter((id) => id !== mergeTargetRecordingId.value)

    if (sourceRecordingIds.length === 0) {
        mergeModalError.value = '请选择至少一条来源录音。'
        return
    }

    const workId = Number(route.params.id)
    if (Number.isNaN(workId)) {
        return
    }

    mergeSubmitting.value = true
    mergeModalError.value = ''
    try {
        await api.recordingController.mergeRecording({
            body: {
                targetId: mergeTargetRecordingId.value,
                needMergeIds: sourceRecordingIds,
            },
        })

        resetMergeState()
        await fetchWork(workId)
    } catch (error) {
        const normalized = normalizeApiError(error)
        mergeModalError.value = normalized.message ?? '合并录音失败'
    } finally {
        mergeSubmitting.value = false
    }
}

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

        resetMergeState()
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
                :items="recordings"
                :active-id="currentRecordingId"
                :playing-id="playingId"
                selection-style="ribbon"
                :selected-ids="selectedRecordingIds"
                @item-double-click="onRecordingDoubleClick"
                @item-toggle-select="toggleRecordingSelection"
                @item-keydown="onRecordingKeydown"
            >
                <template #actions>
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
                    v-if="mergeModalOpen"
                    data-testid="recording-merge-modal"
                    class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-[#2B221B]/60"
                    @click.self="closeMergeModal"
                >
                    <div
                        class="bg-[#fffcf5] w-full max-w-lg max-h-[85vh] flex flex-col shadow-[0_8px_30px_rgba(0,0,0,0.12)] border border-[#EAE6DE]"
                    >
                        <div class="px-8 pt-8 pb-6 border-b border-[#EAE6DE]">
                            <h3 class="font-serif text-2xl text-[#2B221B]">合并录音</h3>
                            <p class="text-sm text-[#8C857B] mt-2">
                                请选择保留的目标录音，其余已选录音将合并到该录音。
                            </p>
                        </div>

                        <div class="px-8 py-6 overflow-y-auto">
                            <div class="space-y-3">
                                <label
                                    v-for="recording in selectedRecordingOptions"
                                    :key="recording.id"
                                    class="flex items-start gap-3 p-3 border border-[#EAE6DE] cursor-pointer hover:bg-[#F7F5F0] transition-colors"
                                >
                                    <input
                                        v-model="mergeTargetRecordingId"
                                        type="radio"
                                        data-testid="recording-merge-target-radio"
                                        :value="recording.id"
                                        class="mt-1 accent-[#C27E46]"
                                    />
                                    <span class="min-w-0">
                                        <span class="block text-[#2B221B] font-serif truncate">
                                            {{ recording.title }}
                                        </span>
                                        <span class="block text-xs text-[#8C857B] truncate">
                                            {{ recording.subtitle }}
                                        </span>
                                    </span>
                                </label>
                            </div>

                            <p class="mt-4 text-xs text-[#8C857B] leading-relaxed">
                                来源录音的音频资源、专辑关联、歌单关联、艺人关联会并入目标录音，来源录音将被删除；Default
                                标记按后端结果保持原样。
                            </p>

                            <p v-if="mergeModalError" class="text-sm text-[#B95D5D] mt-4">
                                {{ mergeModalError }}
                            </p>
                        </div>

                        <div class="p-8 pt-6 border-t border-[#EAE6DE] grid grid-cols-2 gap-3">
                            <button
                                type="button"
                                class="px-4 py-2.5 border border-[#D6D1C4] text-[#8A8A8A] hover:bg-[#F7F5F0] hover:text-[#5A5A5A] transition-colors text-sm tracking-wide"
                                :disabled="mergeSubmitting"
                                @click="closeMergeModal"
                            >
                                取消
                            </button>
                            <button
                                type="button"
                                data-testid="submit-recording-merge-button"
                                class="px-4 py-2.5 bg-[#C27E46] text-white text-sm tracking-wide transition-colors hover:bg-[#B06D39] disabled:opacity-50 disabled:cursor-not-allowed"
                                :disabled="!canSubmitRecordingMerge"
                                @click="submitRecordingMerge"
                            >
                                {{ mergeSubmitting ? '合并中...' : '确认合并' }}
                            </button>
                        </div>
                    </div>
                </div>
            </Transition>
        </Teleport>
    </div>
</template>
