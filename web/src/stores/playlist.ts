import { defineStore } from 'pinia'
import { ref } from 'vue'
import { api, normalizeApiError } from '@/ApiInstance'

export type PlaylistListItem = {
    id: number
    name: string
}

const normalizeName = (name?: string | null) => name?.trim() || '未命名歌单'

export const usePlaylistStore = defineStore('playlist', () => {
    const playlists = ref<PlaylistListItem[]>([])
    const isLoading = ref(false)
    const error = ref('')
    const hasLoaded = ref(false)
    let inFlightFetch: Promise<void> | null = null
    let pendingForceRefetch = false

    const runFetchOnce = async () => {
        isLoading.value = true
        error.value = ''
        try {
            const data = await api.playlistController.listPlaylists()
            playlists.value = data.map((playlist) => ({
                id: playlist.id,
                name: normalizeName(playlist.name),
            }))
            hasLoaded.value = true
        } catch (e) {
            const normalized = normalizeApiError(e)
            error.value = normalized.message ?? '歌单加载失败'
            playlists.value = []
            hasLoaded.value = false
        } finally {
            isLoading.value = false
        }
    }

    const fetchPlaylists = async (force = false) => {
        if (force) {
            pendingForceRefetch = true
        }

        while (true) {
            if (inFlightFetch) {
                await inFlightFetch
                continue
            }

            const shouldFetch = pendingForceRefetch || !hasLoaded.value
            if (!shouldFetch) {
                return
            }

            pendingForceRefetch = false
            const currentFetch = runFetchOnce()
            inFlightFetch = currentFetch

            try {
                await currentFetch
            } finally {
                if (inFlightFetch === currentFetch) {
                    inFlightFetch = null
                }
            }

            if (!pendingForceRefetch) {
                return
            }
        }
    }

    const upsertPlaylist = (playlist: { id: number; name?: string | null }) => {
        const normalized = {
            id: playlist.id,
            name: normalizeName(playlist.name),
        }
        const index = playlists.value.findIndex((item) => item.id === normalized.id)
        if (index >= 0) {
            playlists.value[index] = normalized
            return
        }
        playlists.value.push(normalized)
    }

    const removePlaylist = (id: number) => {
        playlists.value = playlists.value.filter((playlist) => playlist.id !== id)
    }

    return {
        playlists,
        isLoading,
        error,
        hasLoaded,
        fetchPlaylists,
        upsertPlaylist,
        removePlaylist,
    }
})
