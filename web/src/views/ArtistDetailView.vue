<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { api } from '@/ApiInstance'
import { resolveErrorMessage } from '@/i18n/errors'
import DashboardTopBar from '@/components/dashboard/DashboardTopBar.vue'
import MediaGrid from '@/components/media/MediaGrid.vue'
import ViewModeToggle from '@/components/media/ViewModeToggle.vue'
import PaginationControls from '@/components/common/PaginationControls.vue'
import MediaListPanel from '@/components/MediaListPanel.vue'
import MediaListItem from '@/components/MediaListItem.vue'
import { useModal } from '@/composables/useModal'
import AddRecordingToPlaylistModal from '@/components/playlist/AddRecordingToPlaylistModal.vue'
import ArtistDetailHero, { type ArtistHeroData } from '@/components/artist/ArtistDetailHero.vue'
import ArtistEditModal, { type ArtistEditForm } from '@/components/artist/ArtistEditModal.vue'
import RecordingGridCard from '@/components/recording/RecordingGridCard.vue'
import RecordingEditModal, {
    type RecordingEditForm,
    type RecordingPreview,
} from '@/components/recording/RecordingEditModal.vue'
import {
    formatDurationMs,
    formatLabels,
    normalizeRecordings,
    normalizeLabels,
    resolveCover,
    type NormalizedRecordingBase,
    type RecordingAsset,
} from '@/composables/recordingMedia'
import { useRecordingPlayback } from '@/composables/useRecordingPlayback'
import {
    invalidateResolvedPlayableTracksByRecording,
    pickInitialRecordingIdFromCandidates,
    type RecordingPlaybackCandidate,
} from '@/services/recordingPlaybackResolver'
import { useUserStore } from '@/stores/user'

const PAGE_SIZE = 24

const { t } = useI18n()

const route = useRoute()
const modal = useModal()
const userStore = useUserStore()

const isLoading = ref(true)
const isRecordingsLoading = ref(false)
const artistError = ref('')
const recordingsError = ref('')
const viewMode = ref<'grid' | 'list'>('grid')
const pageIndex = ref(0)
const totalPageCount = ref(0)
const totalRowCount = ref(0)
const currentRecordingId = ref<number | null>(null)

type Recording = RecordingPreview &
    RecordingPlaybackCandidate & {
        label: string
        labels: string[]
        comment: string
        isDefault: boolean
        durationMs: number
    }

type ArtistRecordingDto = Awaited<
    ReturnType<typeof api.artistController.listArtistRecordings>
>['rows'][number]

const artistData = ref<ArtistHeroData>({
    name: '',
    aliases: '',
    comment: '',
    avatar: '',
})

const artistEditInitial = ref<ArtistEditForm>({
    displayName: '',
    alias: [],
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
    fallbackCover: () => artistData.value.avatar,
    initialStrategy: 'first-playable',
})

const fetchArtist = async (id: number) => {
    try {
        artistError.value = ''
        const data = await api.artistController.getArtistById({ id })

        artistData.value = {
            name: data.displayName,
            aliases: data.alias.join(' / '),
            comment: data.comment || '',
            avatar: resolveCover(data.avatar),
        }

        artistEditInitial.value = {
            displayName: data.displayName,
            alias: [...data.alias],
            comment: data.comment ?? '',
        }
    } catch (error) {
        artistError.value = resolveErrorMessage(error, 'errors.fallback.artistLoad')
    }
}

const fetchRecordings = async (id: number) => {
    try {
        isRecordingsLoading.value = true
        recordingsError.value = ''

        const page = await api.artistController.listArtistRecordings({
            id,
            pageIndex: pageIndex.value,
            pageSize: PAGE_SIZE,
        })

        totalPageCount.value = page.totalPageCount
        totalRowCount.value = page.totalRowCount

        recordings.value = normalizeRecordings(page.rows as readonly ArtistRecordingDto[], {
            fallbackArtist: artistData.value.name,
            transform: (recording: ArtistRecordingDto, base: NormalizedRecordingBase) => ({
                ...base,
                label: formatLabels(recording.label),
                labels: normalizeLabels(recording.label),
                comment: recording.comment,
                durationMs: recording.durationMs,
                rawArtists: recording.artists || [],
                assets: (recording.assets || []) as readonly RecordingAsset[],
                isDefault: recording.defaultInWork,
                workId: recording.work.id,
            }),
        })
        currentRecordingId.value = pickInitialRecordingIdFromCandidates(
            recordings.value,
            'first-playable',
        )
    } catch (error) {
        recordingsError.value = resolveErrorMessage(error, 'errors.fallback.artistRecordingLoad')
        recordings.value = []
        totalPageCount.value = 0
        totalRowCount.value = 0
    } finally {
        isRecordingsLoading.value = false
    }
}

const fetchPage = async (id: number) => {
    isLoading.value = true
    await fetchArtist(id)
    await fetchRecordings(id)
    isLoading.value = false
}

const handlePageChange = (nextPageIndex: number) => {
    if (nextPageIndex < 0 || nextPageIndex >= totalPageCount.value) {
        return
    }
    pageIndex.value = nextPageIndex
    const id = Number(route.params.id)
    if (!Number.isNaN(id)) {
        void fetchRecordings(id)
    }
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

const openAddToPlaylistModal = (recording: Recording) => {
    void modal.open(AddRecordingToPlaylistModal, {
        title: t('media.addToPlaylist'),
        size: 'sm',
        props: {
            recordingId: recording.id,
        },
    })
}

const openEditArtistModal = async () => {
    const artistId = Number(route.params.id)
    if (Number.isNaN(artistId)) {
        return
    }

    await modal.open(ArtistEditModal, {
        title: t('artistLibrary.editTitle'),
        size: 'md',
        props: {
            initialForm: { ...artistEditInitial.value, alias: [...artistEditInitial.value.alias] },
            submitText: t('common.saveChanges'),
            submittingText: t('common.saving'),
            onSubmit: async (form: ArtistEditForm) => {
                await api.artistController.updateArtist({
                    id: artistId,
                    body: {
                        displayName: form.displayName,
                        alias: form.alias,
                        comment: form.comment,
                    },
                })
                await fetchArtist(artistId)
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
                label: recording.labels,
                comment: recording.comment,
                isDefault: recording.isDefault,
            } satisfies RecordingEditForm,
            showDefaultToggle: false,
            onSubmit: async ({ title, label, comment }: RecordingEditForm) => {
                await api.recordingController.updateRecording({
                    id: recording.id,
                    body: {
                        title,
                        label,
                        comment,
                    },
                })
                invalidateResolvedPlayableTracksByRecording(recording.id)

                const index = recordings.value.findIndex((item) => item.id === recording.id)
                if (index !== -1) {
                    const current = recordings.value[index]
                    if (current) {
                        recordings.value[index] = {
                            ...current,
                            title,
                            label: formatLabels(label),
                            labels: label,
                            comment,
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
        void fetchPage(id)
    }
})

watch(
    () => route.params.id,
    (newId) => {
        const id = Number(newId)
        if (!Number.isNaN(id)) {
            pageIndex.value = 0
            void fetchPage(id)
        }
    },
)
</script>

<template>
    <div class="flex-1 flex flex-col h-full relative">
        <DashboardTopBar />

        <div v-if="isLoading" class="flex-1 flex items-center justify-center text-[#8C857B]">
            {{ t('common.loading') }}
        </div>

        <div
            v-else-if="artistError"
            class="flex-1 flex items-center justify-center text-sm text-[#B75D5D]"
        >
            {{ artistError }}
            <button
                class="ml-4 text-[#C27E46]"
                type="button"
                @click="fetchPage(Number(route.params.id))"
            >
                {{ t('common.retry') }}
            </button>
        </div>

        <div v-else class="mx-auto w-full max-w-5xl px-4 pb-32 sm:px-6 lg:px-8">
            <ArtistDetailHero
                :artist-data="artistData"
                :has-playable-recording="hasPlayableRecording"
                :is-current-playing="isCurrentRecordingPlaying"
                :can-edit="userStore.isAdmin"
                @play="handlePlay()"
                @edit="openEditArtistModal"
            />

            <div v-if="viewMode === 'list'">
                <MediaListPanel
                    :title="t('media.tracks')"
                    :summary="t('media.trackCount', { count: totalRowCount })"
                    :items="recordings"
                    :playing-id="playingId"
                    @item-double-click="onRecordingDoubleClick"
                    @item-keydown="onRecordingKeydown"
                >
                    <template #actions>
                        <ViewModeToggle v-model="viewMode" />
                    </template>
                    <template #empty>
                        {{ t('artistDetail.emptyTracks') }}
                    </template>
                    <template #item="{ item }">
                        <MediaListItem
                            :title="item.title"
                            :label="buildRecordingLabel(item)"
                            :cover="item.cover"
                            :show-add-button="true"
                            :show-edit-button="userStore.isAdmin"
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

            <div v-else>
                <div
                    class="mb-6 flex flex-col gap-3 border-b border-[#EFEBE4] pb-4 sm:mb-8 sm:flex-row sm:items-center sm:justify-between"
                >
                    <div class="flex flex-col gap-1 sm:flex-row sm:items-end sm:gap-4">
                        <h3 class="font-serif text-2xl text-[#2C2420]">{{ t('media.tracks') }}</h3>
                        <div
                            class="text-xs text-[#8C857B] uppercase tracking-widest leading-none mb-1"
                        >
                            {{ t('media.trackCount', { count: totalRowCount }) }}
                        </div>
                    </div>
                    <ViewModeToggle v-model="viewMode" class="self-start sm:self-auto" />
                </div>

                <div
                    v-if="recordings.length === 0 && !isRecordingsLoading"
                    class="py-12 text-center text-[#8C857B] text-sm font-serif italic"
                >
                    {{ t('artistDetail.emptyTracks') }}
                </div>

                <MediaGrid
                    v-else
                    :class="{ 'pointer-events-none opacity-50': isRecordingsLoading }"
                >
                    <RecordingGridCard
                        v-for="item in recordings"
                        :key="item.id"
                        :title="item.title"
                        :subtitle="item.artist"
                        :cover="item.cover"
                        :label="buildRecordingLabel(item)"
                        :is-playing="
                            audioStore.isPlaying && audioStore.currentTrack?.id === item.id
                        "
                        @play="handlePlay(item)"
                    />
                </MediaGrid>
            </div>

            <p v-if="recordingsError" class="mt-4 text-sm text-[#B75D5D]">
                {{ recordingsError }}
                <button
                    class="ml-4 text-[#C27E46]"
                    type="button"
                    @click="fetchRecordings(Number(route.params.id))"
                >
                    {{ t('common.retry') }}
                </button>
            </p>

            <PaginationControls
                :page-index="pageIndex"
                :total-page-count="totalPageCount"
                :disabled="isRecordingsLoading"
                @change="handlePageChange"
            />
        </div>
    </div>
</template>
