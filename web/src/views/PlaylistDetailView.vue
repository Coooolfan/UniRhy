<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Pause, Pencil, Play } from 'lucide-vue-next'
import { api, normalizeApiError } from '@/ApiInstance'
import DashboardTopBar from '@/components/dashboard/DashboardTopBar.vue'
import MediaListItem from '@/components/MediaListItem.vue'
import MediaListPanel from '@/components/MediaListPanel.vue'
import { useModal } from '@/composables/useModal'
import PlaylistEditModal from '@/components/playlist/PlaylistEditModal.vue'
import PlaylistRemoveRecordingModal from '@/components/playlist/PlaylistRemoveRecordingModal.vue'
import {
    normalizeRecordings,
    pickInitialRecordingId,
    resolvePlayableAudio,
    resolveCover,
    type RecordingAsset,
} from '@/composables/recordingMedia'
import { useRecordingPlayback, type PlayableRecording } from '@/composables/useRecordingPlayback'
import { useUserStore } from '@/stores/user'
import { hasSameItemOrder, moveItemById, type ReorderPayload } from '@/utils/recordingOrder'
import { usePlaylistStore } from '@/stores/playlist'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const playlistStore = usePlaylistStore()
const modal = useModal()
const currentRecordingId = ref<number | null>(null)
const isLoading = ref(true)
const isReorderingRecordings = ref(false)
const reorderRecordingError = ref('')

type PlaylistData = {
    title: string
    description: string
    cover: string
}

type Recording = PlayableRecording & {
    label: string
    assets: readonly RecordingAsset[]
}

type PlaylistRecordingDto = Awaited<
    ReturnType<typeof api.playlistController.getPlaylist>
>['recordings'][number]

const playlistData = ref<PlaylistData>({
    title: '',
    description: '',
    cover: '',
})

const recordings = ref<Recording[]>([])

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
    fallbackCover: () => playlistData.value.cover,
})

const fetchPlaylist = async (id: number) => {
    try {
        isLoading.value = true
        reorderRecordingError.value = ''

        const [, data] = await Promise.all([
            userStore.ensureUserLoaded(),
            api.playlistController.getPlaylist({ id }),
        ])
        const firstCover =
            data.recordings && data.recordings.length > 0 ? data.recordings[0]?.cover : undefined

        playlistData.value = {
            title: data.name,
            description: data.comment || '',
            cover: firstCover ? resolveCover(firstCover) : '',
        }

        recordings.value = normalizeRecordings(
            (data.recordings || []) as readonly PlaylistRecordingDto[],
            {
                preferredAssetFormat: userStore.preferredAssetFormat,
                transform: (recording, base) => ({
                    ...base,
                    label: recording.label || '',
                    assets: (recording.assets || []) as readonly RecordingAsset[],
                }),
            },
        )
        currentRecordingId.value = pickInitialRecordingId(recordings.value, 'first-playable')
    } catch (error) {
        console.error('Failed to fetch playlist details:', error)
    } finally {
        isLoading.value = false
    }
}

const confirmDeletePlaylist = async () => {
    const id = Number(route.params.id)
    if (Number.isNaN(id)) {
        return
    }

    await api.playlistController.deletePlaylist({ id })
    playlistStore.removePlaylist(id)

    if (
        audioStore.currentTrack &&
        recordings.value.some((recording) => recording.id === audioStore.currentTrack?.id)
    ) {
        audioStore.stop()
    }

    await router.push({ name: 'dashboard-home' })
}

const openEditModal = async () => {
    const id = Number(route.params.id)
    if (Number.isNaN(id)) {
        return
    }

    await modal.open(PlaylistEditModal, {
        title: '编辑歌单',
        size: 'sm',
        props: {
            initialName: playlistData.value.title,
            initialComment: playlistData.value.description,
            onSave: async ({ name, comment }: { name: string; comment: string }) => {
                await api.playlistController.updatePlaylist({
                    id,
                    body: {
                        name,
                        comment,
                    },
                })
                playlistStore.upsertPlaylist({ id, name })
                await fetchPlaylist(id)
            },
            onDelete: confirmDeletePlaylist,
        },
    })
}

const removeRecording = async (recording: Recording) => {
    const playlistId = Number(route.params.id)
    if (Number.isNaN(playlistId)) {
        return
    }

    await api.playlistController.removeRecordingFromPlaylist({
        id: playlistId,
        recordingId: recording.id,
    })

    const nextRecordings = recordings.value.filter((item) => item.id !== recording.id)
    recordings.value = nextRecordings

    if (currentRecordingId.value === recording.id) {
        currentRecordingId.value = pickInitialRecordingId(nextRecordings, 'first-playable')
    }

    if (audioStore.currentTrack?.id === recording.id) {
        audioStore.stop()
    }

    playlistData.value = {
        ...playlistData.value,
        cover: nextRecordings[0]?.cover || '',
    }
}

const openRemoveRecordingModal = async (recording: Recording) => {
    await modal.open(PlaylistRemoveRecordingModal, {
        title: '移除曲目',
        size: 'sm',
        tone: 'danger',
        props: {
            recordingTitle: recording.title,
            onConfirm: () => removeRecording(recording),
        },
    })
}

const handleRecordingReorder = async (payload: ReorderPayload) => {
    const playlistId = Number(route.params.id)
    if (Number.isNaN(playlistId) || isReorderingRecordings.value) {
        return
    }

    const previousRecordings = [...recordings.value]
    const nextRecordings = moveItemById(previousRecordings, payload)
    if (hasSameItemOrder(previousRecordings, nextRecordings)) {
        return
    }

    recordings.value = nextRecordings
    playlistData.value = {
        ...playlistData.value,
        cover: nextRecordings[0]?.cover || '',
    }

    isReorderingRecordings.value = true
    reorderRecordingError.value = ''

    try {
        await api.playlistController.reorderPlaylistRecordings({
            id: playlistId,
            body: {
                recordingIds: nextRecordings.map((recording) => recording.id),
            },
        })
    } catch (error) {
        recordings.value = previousRecordings
        playlistData.value = {
            ...playlistData.value,
            cover: previousRecordings[0]?.cover || '',
        }
        const normalized = normalizeApiError(error)
        reorderRecordingError.value = normalized.message ?? '调整曲目顺序失败'
    } finally {
        isReorderingRecordings.value = false
    }
}

onMounted(() => {
    const id = Number(route.params.id)
    if (!Number.isNaN(id)) {
        fetchPlaylist(id)
    }
})

watch(
    () => route.params.id,
    (newId) => {
        const id = Number(newId)
        if (!Number.isNaN(id)) {
            fetchPlaylist(id)
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
            <div
                class="group mb-12 mt-6 flex flex-col items-center gap-8 md:mb-16 md:mt-8 md:flex-row md:items-end md:gap-12"
            >
                <div
                    class="relative z-0 h-56 w-56 shrink-0 overflow-hidden rounded-sm bg-[#2C2420] shadow-xl sm:h-64 sm:w-64 md:h-80 md:w-80"
                >
                    <img
                        v-if="playlistData.cover"
                        :src="playlistData.cover"
                        alt="Playlist Cover"
                        class="w-full h-full object-cover"
                    />
                    <div
                        v-else
                        class="w-full h-full flex items-center justify-center bg-[#2C2420] text-[#8C857B]"
                    >
                        <span class="text-xs">No Cover</span>
                    </div>
                </div>

                <div class="relative z-10 flex w-full flex-col gap-4 pb-2 text-center md:text-left">
                    <div
                        class="flex flex-wrap items-center justify-center gap-3 text-sm tracking-wider uppercase text-[#8C857B] md:justify-start"
                    >
                        <span>Playlist</span>
                        <button
                            class="cursor-pointer p-1 text-[#8C857B] opacity-100 transition-all hover:text-[#C17D46] md:opacity-0 md:group-hover:opacity-100"
                            title="编辑歌单"
                            @click="openEditModal"
                        >
                            <Pencil :size="14" />
                        </button>
                    </div>

                    <h1
                        class="font-serif text-4xl leading-tight text-[#2C2420] sm:text-5xl md:text-7xl"
                    >
                        {{ playlistData.title }}
                    </h1>

                    <p class="mx-auto max-w-2xl text-sm text-[#8C857B] line-clamp-3 md:mx-0">
                        {{ playlistData.description }}
                    </p>

                    <div class="mt-4 flex items-center justify-center gap-4 md:justify-start">
                        <button
                            :disabled="!hasPlayableRecording"
                            class="flex w-full items-center justify-center gap-2 rounded-sm border border-[#C17D46] px-6 py-3 text-sm font-medium tracking-widest text-[#C17D46] uppercase transition-all duration-300 hover:bg-[#C17D46] hover:text-white disabled:cursor-not-allowed disabled:opacity-50 disabled:hover:bg-transparent disabled:hover:text-[#C17D46] sm:w-auto sm:px-8"
                            @click="handlePlay()"
                        >
                            <Pause v-if="isCurrentRecordingPlaying" :size="16" />
                            <Play v-else :size="16" fill="currentColor" />
                            {{ isCurrentRecordingPlaying ? '暂停播放' : '立即播放' }}
                        </button>
                    </div>
                </div>
            </div>

            <MediaListPanel
                title="Tracks"
                :summary="`${recordings.length} Tracks`"
                :items="recordings"
                :playing-id="playingId"
                enable-reorder
                :reorder-disabled="isReorderingRecordings"
                @item-click="onRecordingClick"
                @item-double-click="onRecordingDoubleClick"
                @item-keydown="onRecordingKeydown"
                @item-reorder="handleRecordingReorder"
            >
                <template #actions>
                    <span class="text-[11px] uppercase tracking-[0.24em] text-[#B0AAA0]">
                        {{ isReorderingRecordings ? '正在保存顺序' : '' }}
                    </span>
                </template>
                <template #empty> 前往 Work 或者 Album 详情页添加曲目到您的歌单 </template>

                <template #item="{ item }">
                    <MediaListItem
                        :title="item.title"
                        :label="item.label"
                        :show-remove-button="true"
                        :is-playing="
                            audioStore.isPlaying && audioStore.currentTrack?.id === item.id
                        "
                        @play="handlePlay(item)"
                        @remove="openRemoveRecordingModal(item)"
                    />
                </template>
            </MediaListPanel>

            <p v-if="reorderRecordingError" class="mt-4 text-sm text-[#B95D5D]">
                {{ reorderRecordingError }}
            </p>
        </div>
    </div>
</template>
