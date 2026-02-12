import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import type { PlaylistDto } from '@/__generated/model/dto/PlaylistDto'

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
import { usePlaylistStore } from '@/stores/playlist'

type PlaylistResponse = PlaylistDto['PlaylistController/DEFAULT_PLAYLIST_FETCHER']
type PlaylistListResponse = ReadonlyArray<PlaylistResponse>

const listPlaylistsMock = vi.mocked(api.playlistController.listPlaylists)

const buildPlaylist = (id: number, name: string): PlaylistResponse => ({
    id,
    name,
    comment: '',
})

const createDeferred = <T>() => {
    let resolve!: (value: T | PromiseLike<T>) => void
    let reject!: (reason?: unknown) => void
    const promise = new Promise<T>((res, rej) => {
        resolve = res
        reject = rej
    })

    return {
        promise,
        resolve,
        reject,
    }
}

describe('usePlaylistStore', () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        listPlaylistsMock.mockReset()
    })

    it('fetches playlists on first load', async () => {
        listPlaylistsMock.mockResolvedValueOnce([buildPlaylist(1, '收藏')])
        const store = usePlaylistStore()

        await store.fetchPlaylists()

        expect(listPlaylistsMock).toHaveBeenCalledTimes(1)
        expect(store.playlists).toEqual([{ id: 1, name: '收藏' }])
        expect(store.hasLoaded).toBe(true)
        expect(store.error).toBe('')
    })

    it('skips non-force fetch after cache is loaded', async () => {
        listPlaylistsMock.mockResolvedValueOnce([buildPlaylist(1, '初始歌单')])
        const store = usePlaylistStore()

        await store.fetchPlaylists()
        await store.fetchPlaylists()

        expect(listPlaylistsMock).toHaveBeenCalledTimes(1)
    })

    it('fetches again when force is true', async () => {
        listPlaylistsMock
            .mockResolvedValueOnce([buildPlaylist(1, '初始歌单')])
            .mockResolvedValueOnce([buildPlaylist(2, '刷新后歌单')])
        const store = usePlaylistStore()

        await store.fetchPlaylists()
        await store.fetchPlaylists(true)

        expect(listPlaylistsMock).toHaveBeenCalledTimes(2)
        expect(store.playlists).toEqual([{ id: 2, name: '刷新后歌单' }])
    })

    it('runs one trailing fetch when force arrives during in-flight fetch', async () => {
        const firstRequest = createDeferred<PlaylistListResponse>()
        listPlaylistsMock
            .mockReturnValueOnce(firstRequest.promise)
            .mockResolvedValueOnce([buildPlaylist(2, '第二次结果')])
        const store = usePlaylistStore()

        const firstCall = store.fetchPlaylists()
        const forceCall = store.fetchPlaylists(true)

        firstRequest.resolve([buildPlaylist(1, '第一次结果')])
        await Promise.all([firstCall, forceCall])

        expect(listPlaylistsMock).toHaveBeenCalledTimes(2)
        expect(store.playlists).toEqual([{ id: 2, name: '第二次结果' }])
    })

    it('coalesces multiple force calls into one trailing fetch', async () => {
        listPlaylistsMock.mockResolvedValueOnce([buildPlaylist(1, '已缓存')])
        const store = usePlaylistStore()
        await store.fetchPlaylists()

        listPlaylistsMock.mockClear()

        const forceRequest = createDeferred<PlaylistListResponse>()
        listPlaylistsMock
            .mockReturnValueOnce(forceRequest.promise)
            .mockResolvedValueOnce([buildPlaylist(3, '最终结果')])

        const forceCall1 = store.fetchPlaylists(true)
        const forceCall2 = store.fetchPlaylists(true)
        const forceCall3 = store.fetchPlaylists(true)

        forceRequest.resolve([buildPlaylist(2, '中间结果')])
        await Promise.all([forceCall1, forceCall2, forceCall3])

        expect(listPlaylistsMock).toHaveBeenCalledTimes(2)
        expect(store.playlists).toEqual([{ id: 3, name: '最终结果' }])
    })

    it('keeps failure behavior and retries once when force is queued', async () => {
        listPlaylistsMock.mockRejectedValueOnce(new Error('请求失败'))
        const store = usePlaylistStore()

        await store.fetchPlaylists()

        expect(store.hasLoaded).toBe(false)
        expect(store.playlists).toEqual([])
        expect(store.error).toBe('请求失败')

        const firstRequest = createDeferred<PlaylistListResponse>()
        listPlaylistsMock
            .mockReturnValueOnce(firstRequest.promise)
            .mockResolvedValueOnce([buildPlaylist(5, '恢复结果')])

        const firstCall = store.fetchPlaylists()
        const forceCall = store.fetchPlaylists(true)

        firstRequest.reject(new Error('临时错误'))
        await Promise.all([firstCall, forceCall])

        expect(store.hasLoaded).toBe(true)
        expect(store.playlists).toEqual([{ id: 5, name: '恢复结果' }])
        expect(store.error).toBe('')
    })
})
