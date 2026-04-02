<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { formatDurationMs } from '@/composables/recordingMedia'
import { useAudioStore } from '@/stores/audio'
import {
    ArrowDown,
    ArrowUp,
    ChevronUp,
    ChevronRight,
    ChevronDown,
    ListMusic,
    LoaderCircle,
    Pause,
    Play,
    SkipBack,
    SkipForward,
    Trash2,
    Volume2,
    VolumeX,
    X,
} from 'lucide-vue-next'

const audioStore = useAudioStore()
const router = useRouter()
const pendingSeekValue = ref<number | null>(null)
const isQueueExpanded = ref(false)

const updateSeekPreview = (e: Event) => {
    const target = e.target as HTMLInputElement
    pendingSeekValue.value = parseFloat(target.value)
}

const commitSeek = () => {
    if (pendingSeekValue.value === null || !audioStore.canSendRealtimeControl) {
        pendingSeekValue.value = null
        return
    }

    audioStore.seek(pendingSeekValue.value)
    pendingSeekValue.value = null
}

const resetSeekPreview = () => {
    pendingSeekValue.value = null
}

const formatTime = (seconds: number) => {
    const mins = Math.floor(seconds / 60)
    const secs = Math.floor(seconds % 60)
    return `${mins}:${secs.toString().padStart(2, '0')}`
}

const toggleMute = () => {
    if (audioStore.volume > 0) {
        audioStore.setVolume(0)
    } else {
        audioStore.setVolume(1)
    }
}

const hidePlayer = () => {
    isQueueExpanded.value = false
    audioStore.hidePlayer()
}

const showPlayer = () => {
    audioStore.showPlayer()
}

const openPlaybackSyncDebug = () => {
    void router.push({ name: 'playback-sync-debug' })
}

const togglePlayerVisibility = () => {
    if (audioStore.isPlayerHidden) {
        audioStore.showPlayer()
    } else {
        isQueueExpanded.value = false
        audioStore.hidePlayer()
    }
}

const isCornerHovered = ref(false)

const cornerTheme = {
    primary: '#BC5D36',
    sleeveBg: '#EFECE6',
    textLight: '#8B8682',
    border: '#E2DCD3',
}

const isExpandedPlayerLeaving = ref(false)

const onExpandedPlayerBeforeEnter = () => {
    isExpandedPlayerLeaving.value = false
}

const onExpandedPlayerBeforeLeave = () => {
    isExpandedPlayerLeaving.value = true
}

const onExpandedPlayerAfterLeave = () => {
    isExpandedPlayerLeaving.value = false
}

const progressPercentage = computed(() => {
    const totalDuration = audioStore.duration
    if (!Number.isFinite(totalDuration) || totalDuration <= 0) {
        return 0
    }

    const previewTime = pendingSeekValue.value ?? audioStore.currentTime
    const percentage = (previewTime / totalDuration) * 100
    return Math.min(100, Math.max(0, percentage))
})

const displayedCurrentTime = computed(() => {
    return pendingSeekValue.value ?? audioStore.currentTime
})

const transportDisabled = computed(() => {
    return audioStore.isPlaying && !audioStore.canSendRealtimeControl
})

const seekDisabled = computed(() => {
    return !audioStore.canSendRealtimeControl || audioStore.duration <= 0
})

const queueTransportDisabled = computed(() => {
    return !audioStore.canNavigateQueue
})

const handlePlayPrevious = () => {
    void audioStore.playPrevious()
}

const handlePlayNext = () => {
    void audioStore.playNext()
}

const playQueueEntry = (entryId: number) => {
    void audioStore.playQueueEntry(entryId)
}

const clearQueue = () => {
    void audioStore.clearQueue()
}

const toggleQueue = () => {
    isQueueExpanded.value = !isQueueExpanded.value
}

const moveQueueEntry = (entryId: number, direction: -1 | 1) => {
    const queueEntries = [...audioStore.queueEntries]
    const currentIndex = queueEntries.findIndex((entry) => entry.entryId === entryId)
    if (currentIndex < 0) {
        return
    }

    const targetIndex = currentIndex + direction
    if (targetIndex < 0 || targetIndex >= queueEntries.length) {
        return
    }

    const [movedEntry] = queueEntries.splice(currentIndex, 1)
    if (!movedEntry) {
        return
    }
    queueEntries.splice(targetIndex, 0, movedEntry)
    void audioStore.reorderQueue(queueEntries.map((entry) => entry.entryId))
}

const removeQueueEntry = (entryId: number) => {
    void audioStore.removeQueueEntry(entryId)
}

const syncStatusClass = computed(() => {
    switch (audioStore.syncState) {
        case 'calibrating':
            return 'text-[#8C857B]'
        case 'ready':
            return 'text-[#42653F]'
        case 'audio_locked':
            return 'bg-[#FAF0E0] text-[#9A6231] border border-[#EBCFA9]'
        case 'error':
            return 'bg-[#FFF1F1] text-[#B15A5A] border border-[#F0D0D0]'
        default:
            return 'bg-[#F3EEE6] text-[#8C857B] border border-[#E8E0D4]'
    }
})
</script>

<template>
    <div v-if="audioStore.currentTrack">
        <Transition
            enter-active-class="transition-transform duration-320 ease-out"
            enter-from-class="translate-y-full"
            enter-to-class="translate-y-0"
            leave-active-class="transition-transform duration-320 ease-in"
            leave-from-class="translate-y-0"
            leave-to-class="translate-y-full"
            @before-enter="onExpandedPlayerBeforeEnter"
            @before-leave="onExpandedPlayerBeforeLeave"
            @after-leave="onExpandedPlayerAfterLeave"
        >
            <div
                v-show="!audioStore.isPlayerHidden"
                class="fixed bottom-0 left-0 right-0 z-50 border-t border-[#EFEBE4] bg-[#FDFBF7] px-3 pb-[max(0.75rem,env(safe-area-inset-bottom))] pt-3 shadow-[0_-4px_6px_-1px_rgba(0,0,0,0.05)] sm:px-4 md:px-8"
            >
                <div
                    class="mx-auto flex max-w-7xl flex-col gap-2 transition-all duration-280 ease-in transform-gpu will-change-transform will-change-opacity md:flex-row md:items-center md:justify-between md:gap-4"
                    :class="
                        isExpandedPlayerLeaving
                            ? 'translate-x-8 translate-y-4 opacity-0'
                            : 'translate-x-0 translate-y-0 opacity-100'
                    "
                >
                    <div class="flex w-full min-w-0 items-center gap-3 md:w-1/4 md:gap-4">
                        <div
                            v-if="audioStore.currentTrack.cover"
                            class="h-11 w-11 shrink-0 overflow-hidden rounded-sm bg-[#D6D1C7] shadow-sm md:h-12 md:w-12"
                        >
                            <img
                                :src="audioStore.currentTrack.cover"
                                class="w-full h-full object-cover"
                                alt="Cover"
                            />
                        </div>
                        <div class="min-w-0">
                            <div class="font-serif text-[#2C2420] truncate font-medium">
                                {{ audioStore.currentTrack.title }}
                            </div>
                            <div class="mt-0.5 flex items-center gap-2 min-w-0">
                                <div class="text-xs text-[#8C857B] truncate min-w-0">
                                    {{ audioStore.currentTrack.artist }}
                                </div>
                                <button
                                    type="button"
                                    data-test="sync-status"
                                    class="shrink-0 inline-flex cursor-pointer items-center text-[10px] tracking-wide transition-colors hover:text-[#5E5950] focus:outline-none focus-visible:ring-2 focus-visible:ring-[#C17D46]/40"
                                    :class="syncStatusClass"
                                    title="打开同步调试页面"
                                    aria-label="打开同步调试页面"
                                    @click="openPlaybackSyncDebug"
                                >
                                    {{ audioStore.syncStatusText }}
                                </button>
                            </div>
                        </div>
                        <div class="ml-auto flex items-center gap-4 mr- md:hidden">
                            <button
                                type="button"
                                data-test="previous-button-mobile"
                                class="text-[#8C857B] transition-colors hover:text-[#C17D46]"
                                :disabled="queueTransportDisabled"
                                @click="handlePlayPrevious"
                            >
                                <SkipBack :size="18" />
                            </button>

                            <button
                                type="button"
                                @click="
                                    audioStore.isPlaying ? audioStore.pause() : audioStore.resume()
                                "
                                :disabled="transportDisabled"
                                class="flex h-9 w-9 items-center justify-center rounded-full bg-[#C17D46] text-white transition-colors shadow-sm hover:bg-[#A66635]"
                                :class="
                                    transportDisabled
                                        ? 'cursor-not-allowed opacity-50 hover:bg-[#C17D46]'
                                        : ''
                                "
                            >
                                <Pause v-if="audioStore.isPlaying" :size="18" fill="currentColor" />
                                <Play v-else :size="18" fill="currentColor" class="ml-0.5" />
                            </button>

                            <button
                                type="button"
                                data-test="next-button-mobile"
                                class="text-[#8C857B] transition-colors hover:text-[#C17D46]"
                                :disabled="queueTransportDisabled"
                                @click="handlePlayNext"
                            >
                                <SkipForward :size="18" />
                            </button>
                        </div>
                    </div>

                    <div class="flex w-full flex-col items-center gap-1 md:max-w-2xl md:flex-1">
                        <div class="hidden items-center gap-5 sm:gap-6 md:flex">
                            <button
                                data-test="previous-button"
                                class="text-[#8C857B] hover:text-[#C17D46] transition-colors"
                                :disabled="queueTransportDisabled"
                                @click="handlePlayPrevious"
                            >
                                <SkipBack :size="20" />
                            </button>

                            <button
                                data-test="transport-button"
                                @click="
                                    audioStore.isPlaying ? audioStore.pause() : audioStore.resume()
                                "
                                :disabled="transportDisabled"
                                class="w-10 h-10 flex items-center justify-center rounded-full bg-[#C17D46] text-white hover:bg-[#A66635] transition-colors shadow-sm"
                                :class="
                                    transportDisabled
                                        ? 'opacity-50 cursor-not-allowed hover:bg-[#C17D46]'
                                        : ''
                                "
                            >
                                <Pause v-if="audioStore.isPlaying" :size="20" fill="currentColor" />
                                <Play v-else :size="20" fill="currentColor" class="ml-0.5" />
                            </button>

                            <button
                                data-test="next-button"
                                class="text-[#8C857B] hover:text-[#C17D46] transition-colors"
                                :disabled="queueTransportDisabled"
                                @click="handlePlayNext"
                            >
                                <SkipForward :size="20" />
                            </button>
                        </div>

                        <div
                            class="flex w-full items-center gap-2 text-[11px] font-medium tracking-wide text-[#8C857B] sm:gap-3 sm:text-xs"
                        >
                            <span class="w-9 text-right sm:w-10">{{
                                formatTime(displayedCurrentTime)
                            }}</span>
                            <div
                                class="relative flex-1 h-1 bg-[#EFEBE4] rounded-full group"
                                :class="seekDisabled ? 'cursor-not-allowed' : 'cursor-pointer'"
                            >
                                <input
                                    data-test="seek-input"
                                    type="range"
                                    min="0"
                                    :max="audioStore.duration || 100"
                                    :value="displayedCurrentTime"
                                    :disabled="seekDisabled"
                                    @input="updateSeekPreview"
                                    @change="commitSeek"
                                    @pointerup="commitSeek"
                                    @pointercancel="resetSeekPreview"
                                    class="absolute inset-0 w-full h-full opacity-0 z-10"
                                    :class="seekDisabled ? 'cursor-not-allowed' : 'cursor-pointer'"
                                />
                                <div
                                    class="absolute top-0 left-0 h-full bg-[#C17D46] rounded-full pointer-events-none"
                                    :style="{ width: `${progressPercentage}%` }"
                                ></div>
                                <div
                                    class="absolute top-1/2 -translate-x-1/2 -translate-y-1/2 w-2.5 h-2.5 bg-[#C17D46] rounded-full opacity-0 group-hover:opacity-100 transition-opacity pointer-events-none shadow-sm"
                                    :style="{ left: `${progressPercentage}%` }"
                                ></div>
                            </div>
                            <span class="w-10">{{ formatTime(audioStore.duration) }}</span>
                        </div>
                    </div>

                    <div class="flex w-full items-center gap-3 md:w-1/4 md:justify-end">
                        <div class="flex min-w-0 items-center gap-2">
                            <button
                                @click="toggleMute"
                                class="text-[#8C857B] hover:text-[#C17D46] transition-colors"
                            >
                                <VolumeX v-if="audioStore.volume === 0" :size="18" />
                                <Volume2 v-else :size="18" />
                            </button>
                            <div
                                class="relative h-1 w-16 overflow-hidden rounded-full bg-[#EFEBE4] sm:w-20"
                            >
                                <input
                                    type="range"
                                    min="0"
                                    max="1"
                                    step="0.01"
                                    :value="audioStore.volume"
                                    @input="
                                        (e) =>
                                            audioStore.setVolume(
                                                parseFloat((e.target as HTMLInputElement).value),
                                            )
                                    "
                                    class="absolute inset-0 w-full h-full opacity-0 z-10 cursor-pointer"
                                />
                                <div
                                    class="absolute top-0 left-0 h-full bg-[#8C857B] rounded-full pointer-events-none"
                                    :style="{ width: `${audioStore.volume * 100}%` }"
                                ></div>
                            </div>
                        </div>

                        <div class="ml-auto flex items-center gap-3 md:gap-4">
                            <button
                                type="button"
                                data-test="queue-toggle-button"
                                class="inline-flex items-center gap-1 rounded-full border border-[#E8DED0] px-3 py-1.5 text-xs text-[#7C7367] transition-colors hover:border-[#D6B28A] hover:text-[#B56C35]"
                                :disabled="audioStore.queueEntries.length === 0"
                                @click="toggleQueue"
                            >
                                <ListMusic :size="14" />
                                <span>{{ audioStore.queueEntries.length }}</span>
                                <ChevronDown
                                    :size="14"
                                    class="transition-transform duration-200"
                                    :class="isQueueExpanded ? 'rotate-180' : ''"
                                />
                            </button>

                            <button
                                @click="audioStore.stop()"
                                class="p-1 text-[#DCD6CC] hover:text-[#8C857B] transition-colors"
                            >
                                <X :size="20" />
                            </button>

                            <div class="h-4 w-px bg-[#E8E2D8]" aria-hidden="true"></div>

                            <button
                                @click="hidePlayer"
                                class="p-1 text-[#CFC9BF] hover:text-[#8C857B] transition-colors"
                                title="收起播放器"
                                aria-label="收起播放器"
                            >
                                <ChevronRight :size="20" />
                            </button>
                        </div>
                    </div>
                </div>
            </div>
        </Transition>

        <Transition
            enter-active-class="transition-all duration-220 ease-out"
            enter-from-class="translate-x-6 opacity-0"
            enter-to-class="translate-x-0 opacity-100"
            leave-active-class="transition-all duration-180 ease-in"
            leave-from-class="translate-x-0 opacity-100"
            leave-to-class="translate-x-6 opacity-0"
        >
            <aside
                v-if="
                    isQueueExpanded &&
                    audioStore.queueEntries.length > 0 &&
                    !audioStore.isPlayerHidden
                "
                data-test="queue-sidebar"
                class="fixed bottom-[calc(max(6.25rem,env(safe-area-inset-bottom)+5rem))] right-4 top-24 z-[55] flex w-[23rem] flex-col rounded-[1.75rem] border border-[#E8DED0] bg-[#FCF9F4]/98 p-3 shadow-[0_18px_60px_rgba(63,42,20,0.10)] backdrop-blur max-md:left-3 max-md:right-3 max-md:top-auto max-md:max-h-[55vh] max-md:w-auto"
            >
                <div class="mb-3 flex items-start justify-between gap-3 px-1">
                    <div>
                        <div class="text-[11px] uppercase tracking-[0.22em] text-[#8C857B]">
                            Current Queue
                        </div>
                        <div class="mt-1 text-sm font-medium text-[#3E322B]">
                            {{ audioStore.queueEntries.length }} tracks
                        </div>
                    </div>

                    <div class="flex items-center gap-1">
                        <button
                            type="button"
                            data-test="clear-queue-button"
                            class="rounded-full px-2.5 py-1 text-xs text-[#8C857B] transition-colors hover:bg-[#F5EADD] hover:text-[#B55B4A]"
                            :disabled="!audioStore.canSendRealtimeControl"
                            @click="clearQueue"
                        >
                            清空
                        </button>
                        <button
                            type="button"
                            class="rounded-full p-1.5 text-[#8C857B] transition-colors hover:bg-[#F3E8D8] hover:text-[#B56C35]"
                            @click="toggleQueue"
                        >
                            <X :size="14" />
                        </button>
                    </div>
                </div>

                <div class="min-h-0 space-y-1 overflow-y-auto pr-1">
                    <div
                        v-for="(entry, index) in audioStore.queueEntries"
                        :key="entry.entryId"
                        class="group flex items-center gap-3 rounded-2xl px-3 py-2 transition-colors"
                        :class="
                            audioStore.currentQueueEntry?.entryId === entry.entryId
                                ? 'bg-[#F8E8D5]'
                                : 'hover:bg-white'
                        "
                    >
                        <button
                            type="button"
                            class="flex min-w-0 flex-1 items-center gap-3 text-left"
                            @click="playQueueEntry(entry.entryId)"
                        >
                            <div
                                class="flex h-10 w-10 shrink-0 items-center justify-center overflow-hidden rounded-lg bg-[#E8DED0]"
                            >
                                <img
                                    v-if="entry.cover"
                                    :src="entry.cover"
                                    alt="Queue Cover"
                                    class="h-full w-full object-cover"
                                />
                                <Play v-else :size="14" class="text-[#8C857B]" />
                            </div>

                            <div class="min-w-0 flex-1">
                                <div class="flex items-center gap-2">
                                    <LoaderCircle
                                        v-if="
                                            audioStore.currentQueueEntry?.entryId ===
                                                entry.entryId && audioStore.isLoading
                                        "
                                        :size="13"
                                        class="animate-spin text-[#C17D46]"
                                    />
                                    <span
                                        class="truncate text-sm"
                                        :class="
                                            audioStore.currentQueueEntry?.entryId === entry.entryId
                                                ? 'font-semibold text-[#2C2420]'
                                                : 'font-medium text-[#473A32]'
                                        "
                                    >
                                        {{ entry.title }}
                                    </span>
                                </div>
                                <div class="mt-0.5 truncate text-xs text-[#8C857B]">
                                    {{ entry.artist }}
                                </div>
                            </div>
                        </button>

                        <div class="text-xs text-[#8C857B]">
                            {{ formatDurationMs(entry.durationMs) }}
                        </div>

                        <div
                            class="flex items-center gap-1 opacity-100 transition-opacity md:opacity-0 md:group-hover:opacity-100"
                        >
                            <button
                                type="button"
                                class="rounded-md p-1.5 text-[#8C857B] transition-colors hover:bg-[#F3E8D8] hover:text-[#C17D46]"
                                :disabled="index === 0 || !audioStore.canSendRealtimeControl"
                                @click.stop="moveQueueEntry(entry.entryId, -1)"
                            >
                                <ArrowUp :size="14" />
                            </button>
                            <button
                                type="button"
                                class="rounded-md p-1.5 text-[#8C857B] transition-colors hover:bg-[#F3E8D8] hover:text-[#C17D46]"
                                :disabled="
                                    index === audioStore.queueEntries.length - 1 ||
                                    !audioStore.canSendRealtimeControl
                                "
                                @click.stop="moveQueueEntry(entry.entryId, 1)"
                            >
                                <ArrowDown :size="14" />
                            </button>
                            <button
                                type="button"
                                class="rounded-md p-1.5 text-[#8C857B] transition-colors hover:bg-[#FBE7E4] hover:text-[#B55B4A]"
                                :disabled="!audioStore.canSendRealtimeControl"
                                @click.stop="removeQueueEntry(entry.entryId)"
                            >
                                <Trash2 :size="14" />
                            </button>
                        </div>
                    </div>
                </div>
            </aside>
        </Transition>

        <Transition
            enter-active-class="transition-all duration-240 ease-out"
            enter-from-class="translate-x-8 translate-y-8 scale-90 opacity-0"
            enter-to-class="translate-x-0 translate-y-0 scale-100 opacity-100"
            leave-active-class="transition-all duration-200 ease-in"
            leave-from-class="translate-x-0 translate-y-0 scale-100 opacity-100"
            leave-to-class="translate-x-8 translate-y-8 scale-90 opacity-0"
        >
            <div v-if="audioStore.isPlayerHidden" class="fixed bottom-0 right-0 z-50">
                <div
                    class="translate-x-1/2 translate-y-1/2 flex items-center justify-center"
                    @mouseenter="isCornerHovered = true"
                    @mouseleave="isCornerHovered = false"
                >
                    <button
                        type="button"
                        class="absolute rounded-full flex items-center justify-center transition-all duration-600 ease-[cubic-bezier(0.34,1.56,0.64,1)] shadow-[-10px_-10px_30px_rgba(0,0,0,0.04)]"
                        :style="{
                            width: 'min(288px, 62vw)',
                            height: 'min(288px, 62vw)',
                            backgroundColor: cornerTheme.sleeveBg,
                            border: `1px solid ${cornerTheme.border}`,
                            transform: isCornerHovered
                                ? 'scale(1) rotate(0deg)'
                                : 'scale(0.6) rotate(-20deg)',
                            opacity: isCornerHovered ? 1 : 0,
                            pointerEvents: isCornerHovered ? 'auto' : 'none',
                        }"
                        aria-label="展开播放栏"
                        @click="showPlayer"
                    >
                        <svg viewBox="0 0 320 320" class="absolute inset-0 w-full h-full">
                            <defs>
                                <path
                                    id="player-corner-text-arc"
                                    d="M 24,160 A 136,136 0 0,1 160,24"
                                />
                            </defs>
                            <text class="font-sans" letter-spacing="0.06em">
                                <textPath
                                    href="#player-corner-text-arc"
                                    startOffset="50%"
                                    text-anchor="middle"
                                >
                                    <tspan
                                        :fill="cornerTheme.primary"
                                        font-size="14"
                                        font-weight="600"
                                    >
                                        {{ audioStore.currentTrack.title }}
                                    </tspan>
                                    <tspan
                                        :fill="cornerTheme.textLight"
                                        font-size="12"
                                        font-weight="400"
                                    >
                                        • {{ audioStore.currentTrack.artist }}
                                    </tspan>
                                </textPath>
                            </text>
                        </svg>
                    </button>

                    <button
                        type="button"
                        class="absolute rounded-full transition-all duration-500 ease-[cubic-bezier(0.34,1.56,0.64,1)] shadow-[-4px_-4px_15px_rgba(0,0,0,0.1)] flex items-center justify-center overflow-hidden cursor-pointer"
                        :style="{
                            width: isCornerHovered ? 'min(230px, 48vw)' : 'min(200px, 42vw)',
                            height: isCornerHovered ? 'min(230px, 48vw)' : 'min(200px, 42vw)',
                            background: `conic-gradient(from 0deg, transparent 0%, rgba(255,255,255,0.08) 15%, transparent 30%, transparent 50%, rgba(255,255,255,0.08) 65%, transparent 80%), repeating-radial-gradient(circle at 50% 50%, #1A1A1A 0%, #242424 1%, #1A1A1A 2%)`,
                            backgroundColor: '#1A1A1A',
                        }"
                        aria-label="切换播放器展开状态"
                        title="切换播放器展开状态"
                        @click="togglePlayerVisibility"
                    >
                        <div
                            class="absolute inset-0 w-full h-full flex items-center justify-center"
                            :class="
                                audioStore.isPlaying ? 'animate-[spin_48s_linear_infinite]' : ''
                            "
                        >
                            <div
                                class="absolute inset-4 rounded-full border border-white/5 pointer-events-none"
                            ></div>
                            <div
                                class="absolute inset-8 rounded-full border border-white/5 pointer-events-none"
                            ></div>

                            <div
                                class="absolute w-[76px] h-[76px] rounded-full border-[3px] border-[#111] flex items-center justify-center shadow-inner overflow-hidden"
                                :style="{ backgroundColor: cornerTheme.primary }"
                            >
                                <div
                                    class="absolute inset-1 rounded-full border border-white/30 border-dashed"
                                ></div>
                                <div
                                    class="absolute w-[12px] h-[12px] bg-[#EFECE6] rounded-full shadow-[inset_0_2px_4px_rgba(0,0,0,0.5)] z-10 flex items-center justify-center"
                                >
                                    <div class="w-[4px] h-[4px] bg-[#111] rounded-full"></div>
                                </div>
                                <ChevronUp
                                    v-if="isCornerHovered"
                                    :size="20"
                                    class="text-white/80 z-0 absolute drop-shadow-md"
                                />
                            </div>
                        </div>
                    </button>
                </div>
            </div>
        </Transition>
    </div>
</template>
