import { defineStore } from 'pinia'
import { ref } from 'vue'

export type AudioTrack = {
    id: number
    title: string
    artist: string
    cover?: string
    src: string
    workId?: number
}

export const useAudioStore = defineStore('audio', () => {
    // State
    const currentTrack = ref<AudioTrack | null>(null)
    const isPlaying = ref(false)
    const currentTime = ref(0)
    const duration = ref(0)
    const volume = ref(1.0)
    const isLoading = ref(false)
    const error = ref<string | null>(null)

    // Actions
    function play(track: AudioTrack) {
        if (currentTrack.value?.id === track.id) {
            // Toggle play/pause if same track
            isPlaying.value = !isPlaying.value
        } else {
            // New track
            currentTrack.value = track
            isPlaying.value = true
            error.value = null
        }
    }

    function pause() {
        isPlaying.value = false
    }

    function resume() {
        if (currentTrack.value) {
            isPlaying.value = true
        }
    }

    function stop() {
        isPlaying.value = false
        currentTrack.value = null
        currentTime.value = 0
    }

    function seek(time: number) {
        currentTime.value = time
    }

    function setVolume(vol: number) {
        volume.value = Math.max(0, Math.min(1, vol))
    }

    return {
        currentTrack,
        isPlaying,
        currentTime,
        duration,
        volume,
        isLoading,
        error,
        play,
        pause,
        resume,
        stop,
        seek,
        setVolume,
    }
})
