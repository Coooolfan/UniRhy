<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAudioStore } from '@/stores/audio'
import {
    ChevronUp,
    ChevronRight,
    Pause,
    Play,
    SkipBack,
    SkipForward,
    Volume2,
    VolumeX,
    X,
} from 'lucide-vue-next'

const audioStore = useAudioStore()
const router = useRouter()
const pendingSeekValue = ref<number | null>(null)

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
                class="fixed bottom-0 left-0 right-0 z-50 bg-[#FDFBF7] border-t border-[#EFEBE4] shadow-[0_-4px_6px_-1px_rgba(0,0,0,0.05)] px-4 py-3 md:px-8"
            >
                <div
                    class="max-w-7xl mx-auto flex items-center justify-between gap-4 transition-all duration-280 ease-in transform-gpu will-change-transform will-change-opacity"
                    :class="
                        isExpandedPlayerLeaving
                            ? 'translate-x-8 translate-y-4 opacity-0'
                            : 'translate-x-0 translate-y-0 opacity-100'
                    "
                >
                    <div class="flex items-center gap-4 w-1/4 min-w-0">
                        <div
                            v-if="audioStore.currentTrack.cover"
                            class="w-12 h-12 rounded-sm overflow-hidden bg-[#D6D1C7] shrink-0 shadow-sm"
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
                    </div>

                    <div class="flex flex-col items-center flex-1 max-w-2xl gap-1">
                        <div class="flex items-center gap-6">
                            <button
                                class="text-[#8C857B] hover:text-[#C17D46] transition-colors"
                                disabled
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
                                class="text-[#8C857B] hover:text-[#C17D46] transition-colors"
                                disabled
                            >
                                <SkipForward :size="20" />
                            </button>
                        </div>

                        <div
                            class="w-full flex items-center gap-3 text-xs text-[#8C857B] font-medium tracking-wide"
                        >
                            <span class="w-10 text-right">{{
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

                    <div class="flex items-center justify-end gap-4 w-1/4">
                        <div class="flex items-center gap-2 group">
                            <button
                                @click="toggleMute"
                                class="text-[#8C857B] hover:text-[#C17D46] transition-colors"
                            >
                                <VolumeX v-if="audioStore.volume === 0" :size="18" />
                                <Volume2 v-else :size="18" />
                            </button>
                            <div
                                class="w-20 h-1 bg-[#EFEBE4] rounded-full relative overflow-hidden"
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

                        <button
                            @click="audioStore.stop()"
                            class="p-1 text-[#DCD6CC] hover:text-[#8C857B] transition-colors"
                        >
                            <X :size="20" />
                        </button>

                        <div class="w-px h-4 bg-[#E8E2D8]" aria-hidden="true"></div>

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
                            width: '288px',
                            height: '288px',
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
                            width: isCornerHovered ? '230px' : '200px',
                            height: isCornerHovered ? '230px' : '200px',
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
