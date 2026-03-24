import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { defineComponent, h, nextTick } from 'vue'
import type { AsyncTaskLogCountRow } from '@/__generated/model/static'

vi.mock('@/ApiInstance', async (importOriginal) => {
    const actual = await importOriginal<typeof import('@/ApiInstance')>()
    return {
        ...actual,
        api: {
            taskController: {
                listTaskLogs: vi.fn(),
            },
            playlistController: {
                listPlaylists: vi.fn(),
            },
        },
    }
})

import { api } from '@/ApiInstance'
import { usePlaylistGenerateMonitor } from '@/composables/usePlaylistGenerateMonitor'

const listTaskLogsMock = vi.mocked(api.taskController.listTaskLogs)
const listPlaylistsMock = vi.mocked(api.playlistController.listPlaylists)

const MonitorHarness = defineComponent({
    name: 'PlaylistGenerateMonitorHarness',
    setup() {
        usePlaylistGenerateMonitor()
        return () => h('div')
    },
})

const flushView = async () => {
    await Promise.resolve()
    await nextTick()
    await Promise.resolve()
    await nextTick()
}

const buildPlaylistGenerateRows = (
    stats: Partial<Record<AsyncTaskLogCountRow['status'], number>>,
): AsyncTaskLogCountRow[] => [
    {
        taskType: 'PLAYLIST_GENERATE',
        status: 'PENDING',
        count: stats.PENDING ?? 0,
    },
    {
        taskType: 'PLAYLIST_GENERATE',
        status: 'RUNNING',
        count: stats.RUNNING ?? 0,
    },
    {
        taskType: 'PLAYLIST_GENERATE',
        status: 'COMPLETED',
        count: stats.COMPLETED ?? 0,
    },
    {
        taskType: 'PLAYLIST_GENERATE',
        status: 'FAILED',
        count: stats.FAILED ?? 0,
    },
]

describe('usePlaylistGenerateMonitor', () => {
    beforeEach(() => {
        const pinia = createPinia()
        setActivePinia(pinia)
        vi.useFakeTimers()
        listTaskLogsMock.mockReset()
        listPlaylistsMock.mockReset()
        listPlaylistsMock.mockResolvedValue([])
    })

    afterEach(() => {
        vi.useRealTimers()
    })

    it('refreshes playlists when playlist-generate tasks settle into a new completion', async () => {
        listTaskLogsMock
            .mockResolvedValueOnce(buildPlaylistGenerateRows({ RUNNING: 1 }))
            .mockResolvedValueOnce(buildPlaylistGenerateRows({ COMPLETED: 1 }))

        const pinia = createPinia()
        setActivePinia(pinia)
        const wrapper = mount(MonitorHarness, {
            global: {
                plugins: [pinia],
            },
        })

        await flushView()
        expect(listTaskLogsMock).toHaveBeenCalledTimes(1)
        expect(listPlaylistsMock).not.toHaveBeenCalled()

        await vi.advanceTimersByTimeAsync(2000)
        await flushView()

        expect(listTaskLogsMock).toHaveBeenCalledTimes(2)
        expect(listPlaylistsMock).toHaveBeenCalledTimes(1)

        wrapper.unmount()
    })

    it('does not refresh playlists when playlist-generate only fails', async () => {
        listTaskLogsMock
            .mockResolvedValueOnce(buildPlaylistGenerateRows({ PENDING: 1 }))
            .mockResolvedValueOnce(buildPlaylistGenerateRows({ FAILED: 1 }))

        const pinia = createPinia()
        setActivePinia(pinia)
        const wrapper = mount(MonitorHarness, {
            global: {
                plugins: [pinia],
            },
        })

        await flushView()
        await vi.advanceTimersByTimeAsync(2000)
        await flushView()

        expect(listPlaylistsMock).not.toHaveBeenCalled()

        wrapper.unmount()
    })

    it('does not refresh playlists when the queue becomes idle without new completions', async () => {
        listTaskLogsMock
            .mockResolvedValueOnce(buildPlaylistGenerateRows({ RUNNING: 1, COMPLETED: 3 }))
            .mockResolvedValueOnce(buildPlaylistGenerateRows({ COMPLETED: 3 }))

        const pinia = createPinia()
        setActivePinia(pinia)
        const wrapper = mount(MonitorHarness, {
            global: {
                plugins: [pinia],
            },
        })

        await flushView()
        await vi.advanceTimersByTimeAsync(2000)
        await flushView()

        expect(listPlaylistsMock).not.toHaveBeenCalled()

        wrapper.unmount()
    })
})
