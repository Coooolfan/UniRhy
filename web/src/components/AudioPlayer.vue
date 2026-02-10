<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
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
const audioRef = ref<HTMLAudioElement | null>(null)

const syncAudioVolume = () => {
    if (audioRef.value) {
        audioRef.value.volume = audioStore.volume
    }
}

// Handle audio events
const onTimeUpdate = () => {
    if (audioRef.value) {
        audioStore.currentTime = audioRef.value.currentTime
    }
}

const onLoadedMetadata = () => {
    if (audioRef.value) {
        audioStore.duration = audioRef.value.duration
        audioStore.isLoading = false
        syncAudioVolume()
    }
}

const onEnded = () => {
    audioStore.isPlaying = false
    audioStore.currentTime = 0
    if (audioRef.value) {
        audioRef.value.currentTime = 0
    }
}

const onError = (e: Event) => {
    console.error('Audio playback error:', e)
    audioStore.error = 'Unable to play audio'
    audioStore.isLoading = false
    audioStore.isPlaying = false
}

const onWaiting = () => {
    audioStore.isLoading = true
}

const onCanPlay = () => {
    audioStore.isLoading = false
    if (audioStore.isPlaying) {
        audioRef.value?.play().catch((e) => {
            console.error('Play failed:', e)
            audioStore.isPlaying = false
        })
    }
}

// Watch store state to control audio element
watch(
    () => audioStore.isPlaying,
    (isPlaying) => {
        if (!audioRef.value) return
        if (isPlaying) {
            audioRef.value.play().catch((e) => {
                console.error('Play failed:', e)
                audioStore.isPlaying = false
            })
        } else {
            audioRef.value.pause()
        }
    },
)

watch(
    () => audioStore.volume,
    () => {
        syncAudioVolume()
    },
    { immediate: true },
)

watch(
    () => audioStore.currentTrack,
    async (newTrack) => {
        if (!newTrack) {
            audioStore.showPlayer()
            return
        }
        await nextTick()
        syncAudioVolume()
    },
)

// Progress bar interaction
const seekTo = (e: Event) => {
    const target = e.target as HTMLInputElement
    const time = parseFloat(target.value)
    if (audioRef.value) {
        audioRef.value.currentTime = time
        audioStore.currentTime = time
    }
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
</script>

<template>
    <div v-if="audioStore.currentTrack">
        <audio
            ref="audioRef"
            :src="audioStore.currentTrack.src"
            @timeupdate="onTimeUpdate"
            @loadedmetadata="onLoadedMetadata"
            @ended="onEnded"
            @error="onError"
            @waiting="onWaiting"
            @canplay="onCanPlay"
            preload="auto"
        ></audio>

        <Transition
            enter-active-class="transition-all duration-300 ease-out"
            enter-from-class="translate-y-full translate-x-10 opacity-0"
            enter-to-class="translate-y-0 translate-x-0 opacity-100"
            leave-active-class="transition-all duration-300 ease-in"
            leave-from-class="translate-y-0 translate-x-0 opacity-100"
            leave-to-class="translate-y-full translate-x-10 opacity-0"
        >
            <div
                v-show="!audioStore.isPlayerHidden"
                class="fixed bottom-0 left-0 right-0 z-50 bg-[#FDFBF7] border-t border-[#EFEBE4] shadow-[0_-4px_6px_-1px_rgba(0,0,0,0.05)] px-4 py-3 md:px-8"
            >
                <div class="max-w-7xl mx-auto flex items-center justify-between gap-4">
                    <!-- Track Info -->
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
                            <div class="text-xs text-[#8C857B] truncate">
                                {{ audioStore.currentTrack.artist }}
                            </div>
                        </div>
                    </div>

                    <!-- Controls & Progress -->
                    <div class="flex flex-col items-center flex-1 max-w-2xl gap-1">
                        <div class="flex items-center gap-6">
                            <button
                                class="text-[#8C857B] hover:text-[#C17D46] transition-colors"
                                disabled
                            >
                                <SkipBack :size="20" />
                            </button>

                            <button
                                @click="
                                    audioStore.isPlaying ? audioStore.pause() : audioStore.resume()
                                "
                                class="w-10 h-10 flex items-center justify-center rounded-full bg-[#C17D46] text-white hover:bg-[#A66635] transition-colors shadow-sm"
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
                                formatTime(audioStore.currentTime)
                            }}</span>
                            <div
                                class="relative flex-1 h-1 bg-[#EFEBE4] rounded-full group cursor-pointer"
                            >
                                <input
                                    type="range"
                                    min="0"
                                    :max="audioStore.duration || 100"
                                    :value="audioStore.currentTime"
                                    @input="seekTo"
                                    class="absolute inset-0 w-full h-full opacity-0 z-10 cursor-pointer"
                                />
                                <div
                                    class="absolute top-0 left-0 h-full bg-[#C17D46] rounded-full pointer-events-none"
                                    :style="{
                                        width: `${(audioStore.currentTime / (audioStore.duration || 1)) * 100}%`,
                                    }"
                                ></div>
                                <div
                                    class="absolute top-1/2 -translate-y-1/2 w-2.5 h-2.5 bg-[#C17D46] rounded-full opacity-0 group-hover:opacity-100 transition-opacity pointer-events-none shadow-sm"
                                    :style="{
                                        left: `${(audioStore.currentTime / (audioStore.duration || 1)) * 100}%`,
                                        transform: 'translate(-50%, -50%)',
                                    }"
                                ></div>
                            </div>
                            <span class="w-10">{{ formatTime(audioStore.duration) }}</span>
                        </div>
                    </div>

                    <!-- Volume & Close -->
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
            enter-active-class="transition-all duration-200 ease-out"
            enter-from-class="translate-y-4 opacity-0"
            enter-to-class="translate-y-0 opacity-100"
            leave-active-class="transition-all duration-200 ease-in"
            leave-from-class="translate-y-0 opacity-100"
            leave-to-class="translate-y-4 opacity-0"
        >
            <button
                v-if="audioStore.isPlayerHidden"
                @click="showPlayer"
                class="fixed right-4 bottom-4 z-50 px-3 py-2 rounded-full bg-[#FDFBF7] border border-[#E6E1D8] text-[#8C857B] shadow-md hover:text-[#C17D46] hover:border-[#D8CEBE] transition-colors flex items-center gap-2"
                aria-label="展开播放栏"
            >
                <ChevronUp :size="16" />
                <span class="text-xs font-medium tracking-wide">展开播放栏</span>
            </button>
        </Transition>
    </div>
</template>
