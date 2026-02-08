<script setup lang="ts">
import { onMounted, ref, watch, computed } from 'vue'
import { useRoute } from 'vue-router'
import { Play, Heart, MoreHorizontal, Share2, Pause } from 'lucide-vue-next'
import { api } from '@/ApiInstance'
import { useAudioStore } from '@/stores/audio'
import DashboardTopBar from '@/components/dashboard/DashboardTopBar.vue'

const route = useRoute()
const audioStore = useAudioStore()
const currentRecordingId = ref<number | null>(null)
const isLoading = ref(true)
const isCdVisible = ref(false)

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
        isCdVisible.value = false

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
        setTimeout(() => {
            isCdVisible.value = true
        }, 100)
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

// Handle clicking on a recording in the list
const onRecordingClick = (rec: Recording) => {
    // If clicking the already selected recording, toggle play
    if (currentRecordingId.value === rec.id) {
        handlePlay(rec)
    } else {
        // Just select it
        currentRecordingId.value = rec.id
        // Optional: Auto-play when switching selection? For now, let's just select.
        // User might want to see details without playing immediately.
        // But if we want it to feel like a player playlist, maybe we play.
        // Let's stick to "click to select", double click or play button to play?
        // Or keep play button separate.
        // The original code had @click="currentRecording = rec.id"
    }
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
                <div
                    class="relative z-0 group shrink-0 w-64 h-64 md:w-80 md:h-80 select-none perspective-1000"
                >
                    <!-- 滑出的黑胶唱片效果 (Work使用黑胶风格以区别于Album的CD风格) -->
                    <div
                        class="absolute top-2 right-2 bottom-2 left-2 bg-[#1a1a1a] rounded-full shadow-lg flex items-center justify-center transition-transform duration-2000 ease-out z-0"
                        :class="isCdVisible ? 'translate-x-16 md:translate-x-24' : 'translate-x-0'"
                    >
                        <!-- 唱片纹理 -->
                        <div
                            class="absolute inset-2 rounded-full border-4 border-[#2a2a2a] opacity-50"
                        ></div>
                        <div
                            class="absolute inset-8 rounded-full border border-[#333] opacity-30"
                        ></div>
                        <!-- 唱片中心标签 -->
                        <div
                            class="w-1/3 h-1/3 bg-[#C17D46] rounded-full flex items-center justify-center shadow-inner"
                        >
                            <div class="w-1.5 h-1.5 bg-black rounded-full"></div>
                        </div>
                    </div>

                    <!-- 封面 -->
                    <div
                        class="relative w-full h-full shadow-xl rounded-sm overflow-hidden bg-[#2C2420] z-10"
                    >
                        <img
                            v-if="workData.cover"
                            :src="workData.cover"
                            alt="Work Cover"
                            class="w-full h-full object-cover"
                        />
                        <div
                            v-else
                            class="w-full h-full flex items-center justify-center bg-[#2C2420] text-[#8C857B]"
                        >
                            <span class="text-xs">No Cover</span>
                        </div>
                    </div>
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

            <!-- 版本列表 -->
            <div class="bg-[#FDFBF7] rounded-sm shadow-sm p-8 md:p-12 relative">
                <div
                    class="absolute -bottom-2 -right-2 w-full h-full bg-[#F5F1EA] rounded-sm -z-10 transform translate-x-1 translate-y-1"
                ></div>

                <div class="flex items-center justify-between mb-8 border-b border-[#EFEBE4] pb-4">
                    <h3 class="font-serif text-2xl text-[#2C2420]">Recordings</h3>
                    <div class="text-xs text-[#8C857B] uppercase tracking-widest">
                        Versions & Interpretations
                    </div>
                </div>

                <div class="flex flex-col">
                    <div
                        v-for="(rec, index) in recordings"
                        :key="rec.id"
                        @click="onRecordingClick(rec)"
                        class="group flex items-center gap-6 py-4 px-4 rounded-sm transition-all duration-200 cursor-pointer border-b border-transparent hover:bg-[#F2EFE9]"
                        :class="{ 'bg-[#F2EFE9]': currentRecordingId === rec.id }"
                    >
                        <div
                            class="w-6 text-center font-serif text-lg"
                            :class="
                                currentRecordingId === rec.id
                                    ? 'text-[#C17D46]'
                                    : 'text-[#DCD6CC] group-hover:text-[#8C857B]'
                            "
                        >
                            <div
                                v-if="
                                    audioStore.isPlaying && audioStore.currentTrack?.id === rec.id
                                "
                                class="flex gap-0.5 justify-center h-4 items-end"
                            >
                                <div class="w-0.5 bg-[#C17D46] h-2 animate-pulse"></div>
                                <div class="w-0.5 bg-[#C17D46] h-4 animate-pulse delay-75"></div>
                                <div class="w-0.5 bg-[#C17D46] h-3 animate-pulse delay-150"></div>
                            </div>
                            <span v-else>{{ index + 1 }}</span>
                        </div>

                        <!-- 录音封面缩略图 -->
                        <div
                            class="w-10 h-10 shrink-0 bg-[#D6D1C7] rounded-sm overflow-hidden shadow-sm hidden md:block"
                        >
                            <img
                                v-if="rec.cover"
                                :src="rec.cover"
                                class="w-full h-full object-cover"
                            />
                        </div>

                        <div class="flex-1 min-w-0">
                            <div class="flex items-center gap-2">
                                <div
                                    class="text-base font-medium truncate"
                                    :class="
                                        currentRecordingId === rec.id
                                            ? 'text-[#C17D46]'
                                            : 'text-[#4A433B]'
                                    "
                                >
                                    {{ rec.title }}
                                </div>
                                <span
                                    v-if="rec.isDefault"
                                    class="px-1.5 py-0.5 bg-[#EFEAE2] text-[#8C857B] text-[10px] uppercase tracking-wider rounded-xs"
                                    >Default</span
                                >
                            </div>
                            <div class="text-sm text-[#8C857B] truncate">
                                {{ rec.artist }} <span v-if="rec.type" class="mx-1">·</span>
                                {{ rec.type }}
                            </div>
                        </div>

                        <div
                            class="hidden lg:block text-xs text-[#B0AAA0] max-w-[200px] truncate ml-4"
                        >
                            {{ rec.label }}
                        </div>

                        <div
                            class="hidden md:flex opacity-0 group-hover:opacity-100 transition-opacity gap-4 mr-4 text-[#8C857B]"
                        >
                            <button
                                class="hover:text-[#C17D46] transition-colors"
                                @click.stop="handlePlay(rec)"
                            >
                                <Play
                                    v-if="
                                        !(
                                            audioStore.isPlaying &&
                                            audioStore.currentTrack?.id === rec.id
                                        )
                                    "
                                    :size="16"
                                />
                                <Pause v-else :size="16" />
                            </button>
                            <Heart :size="16" class="hover:text-[#C17D46]" />
                            <Share2 :size="16" class="hover:text-[#C17D46]" />
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>
