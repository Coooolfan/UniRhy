import { type Ref, watch } from 'vue'
import { getActivePinia } from 'pinia'
import type { AsyncTaskLogCountRow } from '@/__generated/model/static'
import { usePlaylistStore } from '@/stores/playlist'

type PlaylistGenerateStats = {
    pending: number
    running: number
    completed: number
    failed: number
}

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

export const usePlaylistGenerateMonitor = (rows: Ref<ReadonlyArray<AsyncTaskLogCountRow>>) => {
    const playlistStore = getActivePinia() ? usePlaylistStore() : null
    let previousStats: PlaylistGenerateStats | null = readPlaylistGenerateStats(rows.value)

    watch(rows, async (nextRows) => {
        if (!playlistStore) {
            previousStats = readPlaylistGenerateStats(nextRows)
            return
        }

        const nextStats = readPlaylistGenerateStats(nextRows)
        const lastStats = previousStats
        previousStats = nextStats

        const hadActiveTasks = lastStats !== null && lastStats.pending + lastStats.running > 0
        const isIdleNow = nextStats.pending + nextStats.running === 0
        const completedIncreased = lastStats !== null && nextStats.completed > lastStats.completed

        if (hadActiveTasks && isIdleNow && completedIncreased) {
            await playlistStore.fetchPlaylists(true)
        }
    })
}
