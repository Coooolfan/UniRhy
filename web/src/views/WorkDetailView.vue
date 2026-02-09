<script setup lang="ts">
import { onMounted, ref, watch, computed } from 'vue'
import { useRoute } from 'vue-router'
import { Play, Heart, MoreHorizontal, Pause } from 'lucide-vue-next'
import { api } from '@/ApiInstance'
import { useAudioStore } from '@/stores/audio'
import DashboardTopBar from '@/components/dashboard/DashboardTopBar.vue'
import MediaListPanel from '@/components/MediaListPanel.vue'
import StackedCovers from '@/components/StackedCovers.vue'

const route = useRoute()
const audioStore = useAudioStore()
const currentRecordingId = ref<number | null>(null)
const isLoading = ref(true)

type WorkData = {
    title: string
    artist: string
    recordingsCount: number
    cover: string
}

type Recording = {
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
    recordingsCount: 0,
    cover: '',
})

const recordings = ref<Recording[]>([])

const resolveCover = (coverId?: number) => {
    if (coverId !== undefined) {
        return `/api/media/${coverId}`
    }
    return ''
}

type Asset = {
    mediaFile: {
        id: number
        mimeType: string
    }
}

const resolveAudio = (assets: readonly Asset[]) => {
    const audioAsset = assets.find((a) => a.mediaFile.mimeType.startsWith('audio/'))
    if (audioAsset) {
        return `/api/media/${audioAsset.mediaFile.id}`
    }
    return undefined
}

const fetchWork = async (id: number) => {
    try {
        isLoading.value = true

        const data = await api.workController.getWorkById({ id })

        const defaultRecording =
            data.recordings?.find((r) => r.defaultInWork) ?? data.recordings?.[0]
        const artistName = defaultRecording?.artists?.[0]?.name ?? 'Unknown Artist'

        workData.value = {
            title: data.title,
            artist: artistName,
            recordingsCount: data.recordings?.length ?? 0,
            cover: resolveCover(defaultRecording?.cover?.id),
        }

        recordings.value = (data.recordings || []).map((recording) => ({
            id: recording.id,
            title: recording.title || recording.comment || 'Untitled Recording',
            artist: recording.artists.map((a) => a.name).join(', '),
            type: recording.kind,
            label: recording.label || '',
            comment: recording.comment,
            cover: resolveCover(recording.cover?.id),
            isDefault: recording.defaultInWork,
            audioSrc: resolveAudio(recording.assets || []),
        }))

        if (recordings.value.length > 0) {
            // Select default recording or the first one
            const defaultRec = recordings.value.find((r) => r.isDefault)
            currentRecordingId.value = defaultRec ? defaultRec.id : recordings.value[0]!.id
        }
    } catch (error) {
        console.error('Failed to fetch work details:', error)
    } finally {
        isLoading.value = false
    }
}

// Computed to check if the currently selected recording is playing
const isCurrentPlaying = computed(() => {
    return audioStore.isPlaying && audioStore.currentTrack?.id === currentRecordingId.value
})

const handlePlay = (rec?: Recording) => {
    const targetId = rec?.id ?? currentRecordingId.value
    if (!targetId) return

    const targetRec = recordings.value.find((r) => r.id === targetId)
    if (!targetRec || !targetRec.audioSrc) {
        console.warn('No audio source for recording', targetId)
        return
    }

    audioStore.play({
        id: targetRec.id,
        title: targetRec.title,
        artist: targetRec.artist,
        cover: targetRec.cover || workData.value.cover,
        src: targetRec.audioSrc,
        workId: Number(route.params.id),
    })
}

const onRecordingKeydown = (event: KeyboardEvent, rec: Recording) => {
    if (event.key === 'Enter' || event.key === ' ') {
        event.preventDefault()
        onRecordingDoubleClick(rec)
    }
}

// Handle clicking on a recording in the list
const onRecordingClick = (rec: Recording) => {
    currentRecordingId.value = rec.id
}

const onRecordingDoubleClick = (rec: Recording) => {
    currentRecordingId.value = rec.id
    handlePlay(rec)
}

onMounted(() => {
    const id = Number(route.params.id)
    if (!isNaN(id)) {
        fetchWork(id)
    }
})

watch(
    () => route.params.id,
    (newId) => {
        const id = Number(newId)
        if (!isNaN(id)) {
            fetchWork(id)
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
            <!-- 头部卡片 -->
            <div class="mt-8 flex flex-col md:flex-row gap-12 md:gap-32 items-end mb-16">
                <!-- 封面艺术 -->
                <div class="ml-8 mt-4 w-64 h-64 md:w-80 md:h-80 shrink-0">
                    <StackedCovers :items="recordings" :default-cover="workData.cover" />
                </div>

                <!-- 信息 -->
                <div class="flex flex-col gap-4 pb-2 w-full relative z-10">
                    <div
                        class="flex items-center gap-3 text-sm tracking-wider uppercase text-[#8C857B]"
                    >
                        <span>Musical Work</span>
                        <span class="w-8 h-px bg-[#C17D46]"></span>
                        <span>{{ workData.recordingsCount }} Versions</span>
                    </div>

                    <h1 class="text-5xl md:text-7xl font-serif text-[#2C2420] leading-tight">
                        {{ workData.title }}
                    </h1>

                    <div class="text-xl text-[#5E564D] font-serif italic mb-2">
                        Originally by {{ workData.artist }}
                    </div>

                    <div class="flex items-center gap-4 mt-4">
                        <button
                            @click="handlePlay()"
                            class="px-8 py-3 border border-[#C17D46] text-[#C17D46] hover:bg-[#C17D46] hover:text-white transition-all duration-300 flex items-center gap-2 text-sm tracking-widest uppercase font-medium rounded-sm cursor-pointer"
                        >
                            <Pause v-if="isCurrentPlaying" :size="16" />
                            <Play v-else :size="16" fill="currentColor" />
                            {{ isCurrentPlaying ? '暂停播放' : '立即播放' }}
                        </button>
                        <button
                            class="p-3 text-[#8C857B] hover:text-[#C17D46] transition-colors border border-transparent hover:border-[#DCD6CC] rounded-full cursor-pointer"
                        >
                            <Heart :size="20" />
                        </button>
                        <button
                            class="p-3 text-[#8C857B] hover:text-[#C17D46] transition-colors border border-transparent hover:border-[#DCD6CC] rounded-full cursor-pointer"
                        >
                            <MoreHorizontal :size="20" />
                        </button>
                    </div>
                </div>
            </div>

            <MediaListPanel
                title="Recordings"
                summary="Versions & Interpretations"
                :items="recordings"
                :active-id="currentRecordingId"
                :playing-id="audioStore.isPlaying ? (audioStore.currentTrack?.id ?? null) : null"
                @item-click="onRecordingClick"
                @item-double-click="onRecordingDoubleClick"
                @item-keydown="onRecordingKeydown"
            >
                <template #item="{ item, isActive }">
                    <!-- 录音封面缩略图 -->
                    <div
                        class="w-10 h-10 shrink-0 bg-[#D6D1C7] rounded-sm overflow-hidden shadow-sm hidden md:block"
                    >
                        <img
                            v-if="item.cover"
                            :src="item.cover"
                            class="w-full h-full object-cover"
                        />
                    </div>

                    <div class="flex-1 min-w-0">
                        <div class="flex items-center gap-2">
                            <div
                                class="text-base font-medium truncate"
                                :class="isActive ? 'text-[#C17D46]' : 'text-[#4A433B]'"
                            >
                                {{ item.title }}
                            </div>
                            <span
                                v-if="item.isDefault"
                                class="px-1.5 py-0.5 bg-[#EFEAE2] text-[#8C857B] text-[10px] uppercase tracking-wider rounded-xs"
                                >Default</span
                            >
                        </div>
                        <div class="text-sm text-[#8C857B] truncate">
                            {{ item.artist }} <span v-if="item.type" class="mx-1">·</span>
                            {{ item.type }}
                        </div>
                    </div>

                    <div class="hidden lg:block text-xs text-[#B0AAA0] max-w-[200px] truncate ml-4">
                        {{ item.label }}
                    </div>

                    <div
                        class="hidden md:flex opacity-0 group-hover:opacity-100 transition-opacity gap-4 mr-4 text-[#8C857B]"
                    >
                        <button
                            class="hover:text-[#C17D46] transition-colors"
                            @click.stop="handlePlay(item)"
                        >
                            <Play
                                v-if="
                                    !(
                                        audioStore.isPlaying &&
                                        audioStore.currentTrack?.id === item.id
                                    )
                                "
                                :size="16"
                            />
                            <Pause v-else :size="16" />
                        </button>
                    </div>
                </template>
            </MediaListPanel>
        </div>
    </div>
</template>
