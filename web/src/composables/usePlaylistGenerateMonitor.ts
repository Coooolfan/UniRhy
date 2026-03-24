import { onMounted, onUnmounted } from 'vue'
import { api } from '@/ApiInstance'
import type { AsyncTaskLogCountRow } from '@/__generated/model/static'
import { usePlaylistStore } from '@/stores/playlist'

type PlaylistGenerateStats = {
    pending: number
    running: number
    completed: number
    failed: number
}

const PLAYLIST_GENERATE_POLL_INTERVAL_MS = 2000

const emptyStats = (): PlaylistGenerateStats => ({
    pending: 0,
    running: 0,
    completed: 0,
    failed: 0,
})

const readPlaylistGenerateStats = (
    rows: ReadonlyArray<AsyncTaskLogCountRow>,
): PlaylistGenerateStats => {
    const stats = emptyStats()

    rows.forEach((row) => {
        if (row.taskType !== 'PLAYLIST_GENERATE') {
            return
        }

        if (row.status === 'PENDING') {
            stats.pending = row.count
            return
        }

        if (row.status === 'RUNNING') {
            stats.running = row.count
            return
        }

        if (row.status === 'COMPLETED') {
            stats.completed = row.count
            return
        }

        stats.failed = row.count
    })

    return stats
}

export const usePlaylistGenerateMonitor = () => {
    const playlistStore = usePlaylistStore()
    let timer: ReturnType<typeof setInterval> | null = null
    let previousStats: PlaylistGenerateStats | null = null
    let isPolling = false
    let isDisposed = false

    const pollPlaylistGenerateStats = async () => {
        if (isPolling || isDisposed) {
            return
        }

        isPolling = true

        try {
            const nextStats = readPlaylistGenerateStats(await api.taskController.listTaskLogs())
            if (isDisposed) {
                return
            }

            const lastStats = previousStats
            previousStats = nextStats

            const hadActiveTasks = lastStats !== null && lastStats.pending + lastStats.running > 0
            const isIdleNow = nextStats.pending + nextStats.running === 0
            const completedIncreased =
                lastStats !== null && nextStats.completed > lastStats.completed

            if (hadActiveTasks && isIdleNow && completedIncreased) {
                await playlistStore.fetchPlaylists(true)
            }
        } catch {
            // Ignore transient polling failures and retry on the next interval.
        } finally {
            isPolling = false
        }
    }

    onMounted(() => {
        void pollPlaylistGenerateStats()
        timer = setInterval(() => {
            void pollPlaylistGenerateStats()
        }, PLAYLIST_GENERATE_POLL_INTERVAL_MS)
    })

    onUnmounted(() => {
        isDisposed = true
        previousStats = null
        if (timer) {
            clearInterval(timer)
            timer = null
        }
    })
}
