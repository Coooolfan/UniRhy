import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import {
    i18n,
    isSupportedLocale,
    LOCALE_STORAGE_KEY,
    setDocumentLocale,
    type SupportedLocale,
} from '@/i18n'

export const PLAYBACK_MODES = ['SYNC', 'INDEPENDENT'] as const
export type PlaybackMode = (typeof PLAYBACK_MODES)[number]

export const DEFAULT_PLAYBACK_MODE: PlaybackMode = 'INDEPENDENT'
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

const persistLocale = (locale: SupportedLocale) => {
    if (typeof window === 'undefined') {
        return
    }
    window.localStorage.setItem(LOCALE_STORAGE_KEY, locale)
}

export const useClientPreferencesStore = defineStore('clientPreferences', () => {
    const playbackMode = ref<PlaybackMode>(readSavedPlaybackMode())
    const isIndependentPlaybackMode = computed(() => playbackMode.value === 'INDEPENDENT')

    const setPlaybackMode = (mode: PlaybackMode) => {
        playbackMode.value = mode
        persistPlaybackMode(mode)
    }

    // i18n 实例在 main.ts 中先于 store 创建，locale 直接以它的当前值为准，
    // 避免和 detectInitialLocale() 的探测逻辑重复一份
    const locale = ref<SupportedLocale>(
        isSupportedLocale(i18n.global.locale.value) ? i18n.global.locale.value : 'zh-CN',
    )

    const setLocale = (next: SupportedLocale) => {
        locale.value = next
        i18n.global.locale.value = next
        setDocumentLocale(next)
        persistLocale(next)
    }

    return {
        playbackMode,
        isIndependentPlaybackMode,
        setPlaybackMode,
        locale,
        setLocale,
    }
})
