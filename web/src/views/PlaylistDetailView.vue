<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Pause, Pencil, Play } from 'lucide-vue-next'
import { api, normalizeApiError } from '@/ApiInstance'
import DashboardTopBar from '@/components/dashboard/DashboardTopBar.vue'
import MediaListItem from '@/components/MediaListItem.vue'
import MediaListPanel from '@/components/MediaListPanel.vue'
import PlaylistEditModal from '@/components/playlist/PlaylistEditModal.vue'
import PlaylistRemoveRecordingModal from '@/components/playlist/PlaylistRemoveRecordingModal.vue'
import {
    normalizeRecordings,
    pickInitialRecordingId,
    resolveCover,
} from '@/composables/recordingMedia'
import { useRecordingPlayback, type PlayableRecording } from '@/composables/useRecordingPlayback'
import { usePlaylistStore } from '@/stores/playlist'

const route = useRoute()
const router = useRouter()
const playlistStore = usePlaylistStore()
const currentRecordingId = ref<number | null>(null)
const isLoading = ref(true)

const isEditModalOpen = ref(false)
const isEditing = ref(false)
const editName = ref('')
const editComment = ref('')
const editError = ref('')
const isDeleteConfirming = ref(false)
const isDeletingPlaylist = ref(false)
const deletePlaylistError = ref('')
const recordingPendingRemoval = ref<Recording | null>(null)
const isRemovingRecording = ref(false)
const removeRecordingError = ref('')

type PlaylistData = {
    title: string
    description: string
    cover: string
}

type Recording = PlayableRecording & {
    label: string
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
        removeRecordingError.value = ''

        const data = await api.playlistController.getPlaylist({ id })
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
                transform: (recording, base) => ({
                    ...base,
                    label: recording.label || '',
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

const isDeleteAction = computed(() => editName.value.trim().length === 0)

const openEditModal = () => {
    if (isEditing.value || isDeletingPlaylist.value) {
        return
    }
    editName.value = playlistData.value.title
    editComment.value = playlistData.value.description
    editError.value = ''
    deletePlaylistError.value = ''
    isDeleteConfirming.value = false
    isEditModalOpen.value = true
}

const closeEditModal = () => {
    if (isEditing.value || isDeletingPlaylist.value) {
        return
    }
    isEditModalOpen.value = false
    isDeleteConfirming.value = false
    editName.value = ''
    editComment.value = ''
    editError.value = ''
    deletePlaylistError.value = ''
}

const confirmDeletePlaylist = async () => {
    const id = Number(route.params.id)
    if (Number.isNaN(id) || isDeletingPlaylist.value) {
        return
    }

    isDeletingPlaylist.value = true
    deletePlaylistError.value = ''

    try {
        await api.playlistController.deletePlaylist({ id })
        playlistStore.removePlaylist(id)

        if (
            audioStore.currentTrack &&
            recordings.value.some((recording) => recording.id === audioStore.currentTrack?.id)
        ) {
            audioStore.stop()
        }

        isEditModalOpen.value = false
        await router.push({ name: 'dashboard-home' })
    } catch (error) {
        const normalized = normalizeApiError(error)
        deletePlaylistError.value = normalized.message ?? '删除歌单失败'
    } finally {
        isDeletingPlaylist.value = false
    }
}

const submitEdit = async () => {
    const name = editName.value.trim()
    const id = Number(route.params.id)
    if (Number.isNaN(id)) {
        return
    }

    if (isDeleteAction.value) {
        editError.value = ''
        if (!isDeleteConfirming.value) {
            isDeleteConfirming.value = true
            deletePlaylistError.value = ''
            return
        }
        await confirmDeletePlaylist()
        return
    }

    if (isEditing.value || isDeletingPlaylist.value) {
        return
    }
    isDeleteConfirming.value = false
    isEditing.value = true
    editError.value = ''

    try {
        await api.playlistController.updatePlaylist({
            id,
            body: {
                name,
                comment: editComment.value.trim(),
            },
        })
        playlistStore.upsertPlaylist({ id, name })
        isEditModalOpen.value = false
        await fetchPlaylist(id)
    } catch (error) {
        const normalized = normalizeApiError(error)
        editError.value = normalized.message ?? '更新歌单失败'
    } finally {
        isEditing.value = false
    }
}

const openRemoveRecordingModal = (recording: Recording) => {
    if (isRemovingRecording.value) {
        return
    }
    recordingPendingRemoval.value = recording
    removeRecordingError.value = ''
}

const closeRemoveRecordingModal = () => {
    if (isRemovingRecording.value) {
        return
    }
    recordingPendingRemoval.value = null
    removeRecordingError.value = ''
}

const confirmRemoveRecording = async () => {
    const recording = recordingPendingRemoval.value
    if (!recording || isRemovingRecording.value) {
        return
    }

    const playlistId = Number(route.params.id)
    if (Number.isNaN(playlistId)) {
        return
    }

    isRemovingRecording.value = true
    removeRecordingError.value = ''

    try {
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
        recordingPendingRemoval.value = null
    } catch (error) {
        const normalized = normalizeApiError(error)
        removeRecordingError.value = normalized.message ?? '移除曲目失败'
    } finally {
        isRemovingRecording.value = false
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
    () => editName.value,
    (name) => {
        if (name.trim().length > 0) {
            isDeleteConfirming.value = false
            deletePlaylistError.value = ''
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
                @item-click="onRecordingClick"
                @item-double-click="onRecordingDoubleClick"
                @item-keydown="onRecordingKeydown"
            >
                <template #empty> 前往 Work 或者 Album 详情页添加曲目到您的歌单 </template>

                <template #item="{ item }">
                    <MediaListItem
                        :title="item.title"
                        :label="item.label"
                        :show-remove-button="true"
                        :is-removing="
                            isRemovingRecording && recordingPendingRemoval?.id === item.id
                        "
                        :is-playing="
                            audioStore.isPlaying && audioStore.currentTrack?.id === item.id
                        "
                        @play="handlePlay(item)"
                        @remove="openRemoveRecordingModal(item)"
                    />
                </template>
            </MediaListPanel>
        </div>

        <PlaylistEditModal
            :open="isEditModalOpen"
            :name="editName"
            :comment="editComment"
            :is-delete-action="isDeleteAction"
            :is-delete-confirming="isDeleteConfirming"
            :is-editing="isEditing"
            :is-deleting="isDeletingPlaylist"
            :error="editError"
            :delete-error="deletePlaylistError"
            @update:name="editName = $event"
            @update:comment="editComment = $event"
            @close="closeEditModal"
            @submit="submitEdit"
        />

        <PlaylistRemoveRecordingModal
            :open="Boolean(recordingPendingRemoval)"
            :recording-title="recordingPendingRemoval?.title ?? ''"
            :is-removing="isRemovingRecording"
            :error="removeRecordingError"
            @close="closeRemoveRecordingModal"
            @confirm="confirmRemoveRecording"
        />
    </div>
</template>
