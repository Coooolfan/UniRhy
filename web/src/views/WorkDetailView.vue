<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { api, normalizeApiError } from '@/ApiInstance'
import DashboardTopBar from '@/components/dashboard/DashboardTopBar.vue'
import MediaListPanel from '@/components/MediaListPanel.vue'
import MediaListItem from '@/components/MediaListItem.vue'
import { useModal } from '@/composables/useModal'
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
    resolvePlayableAudio,
    type RecordingAsset,
} from '@/composables/recordingMedia'
import { useRecordingMergeState } from '@/composables/useRecordingMergeState'
import { useRecordingPlayback } from '@/composables/useRecordingPlayback'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const modal = useModal()
const userStore = useUserStore()
const currentRecordingId = ref<number | null>(null)
const isLoading = ref(true)

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

const syncRecordingPlaybackSources = () => {
    recordings.value = recordings.value.map((recording) => {
        const playableAudio = resolvePlayableAudio(recording.assets, userStore.preferredAssetFormat)
        return {
            ...recording,
            audioSrc: playableAudio?.src,
            mediaFileId: playableAudio?.mediaFileId,
        }
    })
}

async function fetchWork(id: number) {
    try {
        isLoading.value = true

        const [, data] = await Promise.all([
            userStore.ensureUserLoaded(),
            api.workController.getWorkById({ id }),
        ])

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
                preferredAssetFormat: userStore.preferredAssetFormat,
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
        recordings.value = normalizedRecordings

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
    toggleSelection: toggleRecordingSelection,
    resetState: resetMergeState,
} = mergeState
mergeStateActions.resetState = resetMergeState

const openEditModal = async () => {
    const id = Number(route.params.id)
    if (Number.isNaN(id)) {
        return
    }

    await modal.open(WorkTitleEditModal, {
        title: '编辑作品',
        size: 'sm',
        props: {
            initialTitle: workData.value.title,
            onSubmit: async (title: string) => {
                const updated = await api.workController.updateWork({
                    id,
                    body: { title },
                })

                workData.value = {
                    ...workData.value,
                    title: updated.title || title,
                }
            },
        },
    })
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
            showDefaultToggle: true,
            onSubmit: async ({ title, label, comment, type, isDefault }: RecordingEditForm) => {
                await api.recordingController.updateRecording({
                    id: recording.id,
                    body: {
                        title: title.trim(),
                        label: label?.trim(),
                        comment: comment?.trim() || '',
                        kind: type.trim(),
                        defaultInWork: isDefault,
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
                            isDefault,
                        }
                    }
                }

                if (isDefault) {
                    recordings.value.forEach((item, recordingIndex) => {
                        if (recordingIndex !== index && item.isDefault) {
                            recordings.value[recordingIndex] = {
                                ...item,
                                isDefault: false,
                            }
                        }
                    })
                }
            },
        },
    })
}

const openAddToPlaylistModal = (recording: Recording) => {
    void modal.open(AddRecordingToPlaylistModal, {
        title: '添加到歌单',
        props: {
            recordingId: recording.id,
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

const buildRecordingSubtitle = (recording: Recording) => {
    const parts = [recording.artist, recording.type].filter(Boolean)
    return parts.join(' · ')
}

const openRecordingMergeModal = async () => {
    if (!hasEnoughSelectedRecordings.value) {
        return
    }

    const workId = Number(route.params.id)
    if (Number.isNaN(workId)) {
        return
    }

    await modal.open(MergeSelectModal, {
        title: '合并曲目',
        size: 'md',
        props: {
            description: '请选择保留的目标曲目，其余已选曲目将合并到该曲目。',
            options: selectedRecordingOptions.value,
            note: '来源曲目的音频资源、专辑关联、歌单关联、艺人关联会并入目标曲目，来源曲目将被删除；Default 标记按后端结果保持原样。',
            modalTestId: 'recording-merge-modal',
            optionRadioTestId: 'recording-merge-target-radio',
            confirmTestId: 'submit-recording-merge-button',
            missingTargetMessage: '请选择一个目标曲目。',
            onConfirm: async (targetId: number) => {
                const sourceIds = selectedRecordingOptions.value
                    .map((option) => option.id)
                    .filter((id) => id !== targetId)

                if (sourceIds.length === 0) {
                    throw new Error('请选择至少一条来源曲目。')
                }

                await api.recordingController.mergeRecording({
                    body: {
                        targetId,
                        needMergeIds: sourceIds,
                    },
                })

                await fetchWork(workId)
                resetMergeState()
            },
        },
    })
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

watch(
    () => userStore.preferredAssetFormat,
    () => {
        if (recordings.value.length === 0) {
            return
        }
        syncRecordingPlaybackSources()
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
                        @click="openRecordingMergeModal"
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
    </div>
</template>
