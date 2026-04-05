import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { defineComponent, h, nextTick, toRef } from 'vue'
import type { AsyncTaskLogCountRow } from '@/__generated/model/static'

vi.mock('@/ApiInstance', async (importOriginal) => {
    const actual = await importOriginal<typeof import('@/ApiInstance')>()
    return {
        ...actual,
        api: {
            playlistController: {
                listPlaylists: vi.fn(),
            },
        },
    }
})

import { api } from '@/ApiInstance'
import { usePlaylistGenerateMonitor } from '@/composables/usePlaylistGenerateMonitor'

const listPlaylistsMock = vi.mocked(api.playlistController.listPlaylists)

const MonitorHarness = defineComponent({
    name: 'PlaylistGenerateMonitorHarness',
    props: {
        rows: {
            type: Array as () => AsyncTaskLogCountRow[],
            required: true,
        },
    },
    setup(props) {
        const rows = toRef(props, 'rows')
        usePlaylistGenerateMonitor(rows)
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
        listPlaylistsMock.mockReset()
        listPlaylistsMock.mockResolvedValue([])
    })

    it('refreshes playlists when playlist-generate tasks settle into a new completion', async () => {
        const pinia = createPinia()
        setActivePinia(pinia)
        const wrapper = mount(MonitorHarness, {
            props: {
                rows: buildPlaylistGenerateRows({ RUNNING: 1 }),
            },
            global: {
                plugins: [pinia],
            },
        })

        await flushView()
        expect(listPlaylistsMock).not.toHaveBeenCalled()

        await wrapper.setProps({
            rows: buildPlaylistGenerateRows({ COMPLETED: 1 }),
        })
        await flushView()

        expect(listPlaylistsMock).toHaveBeenCalledTimes(1)

        wrapper.unmount()
    })

    it('does not refresh playlists when playlist-generate only fails', async () => {
        const pinia = createPinia()
        setActivePinia(pinia)
        const wrapper = mount(MonitorHarness, {
            props: {
                rows: buildPlaylistGenerateRows({ PENDING: 1 }),
            },
            global: {
                plugins: [pinia],
            },
        })

        await flushView()
        await wrapper.setProps({
            rows: buildPlaylistGenerateRows({ FAILED: 1 }),
        })
        await flushView()

        expect(listPlaylistsMock).not.toHaveBeenCalled()

        wrapper.unmount()
    })

    it('does not refresh playlists when the queue becomes idle without new completions', async () => {
        const pinia = createPinia()
        setActivePinia(pinia)
        const wrapper = mount(MonitorHarness, {
            props: {
                rows: buildPlaylistGenerateRows({ RUNNING: 1, COMPLETED: 3 }),
            },
            global: {
                plugins: [pinia],
            },
        })

        await flushView()
        await wrapper.setProps({
            rows: buildPlaylistGenerateRows({ COMPLETED: 3 }),
        })
        await flushView()

        expect(listPlaylistsMock).not.toHaveBeenCalled()

        wrapper.unmount()
    })
})
