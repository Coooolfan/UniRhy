import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

export const PLAYBACK_MODES = ['SYNC', 'INDEPENDENT'] as const
export type PlaybackMode = (typeof PLAYBACK_MODES)[number]

export const DEFAULT_PLAYBACK_MODE: PlaybackMode = 'SYNC'
export const PLAYBACK_MODE_STORAGE_KEY = 'unirhy.playback.mode'

const isPlaybackMode = (value: string | null): value is PlaybackMode => {
    return value === 'SYNC' || value === 'INDEPENDENT'
}

const readSavedPlaybackMode = (): PlaybackMode => {
    if (typeof window === 'undefined') {
        return DEFAULT_PLAYBACK_MODE
    }

    const savedMode = window.localStorage.getItem(PLAYBACK_MODE_STORAGE_KEY)
    return isPlaybackMode(savedMode) ? savedMode : DEFAULT_PLAYBACK_MODE
}

const persistPlaybackMode = (mode: PlaybackMode) => {
    if (typeof window === 'undefined') {
        return
    }
    window.localStorage.setItem(PLAYBACK_MODE_STORAGE_KEY, mode)
}

export const useClientPreferencesStore = defineStore('clientPreferences', () => {
    const playbackMode = ref<PlaybackMode>(readSavedPlaybackMode())
    const isIndependentPlaybackMode = computed(() => playbackMode.value === 'INDEPENDENT')

    const setPlaybackMode = (mode: PlaybackMode) => {
        playbackMode.value = mode
        persistPlaybackMode(mode)
    }

    return {
        playbackMode,
        isIndependentPlaybackMode,
        setPlaybackMode,
    }
})
