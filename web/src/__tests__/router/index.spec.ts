import { beforeEach, describe, expect, it, vi } from 'vitest'

const { getAuthTokenMock, isInitializedMock } = vi.hoisted(() => ({
    getAuthTokenMock: vi.fn<() => string | null>(),
    isInitializedMock: vi.fn<() => Promise<{ initialized: boolean }>>(),
}))

vi.mock('@/ApiInstance', () => ({
    api: {
        systemConfigController: {
            isInitialized: isInitializedMock,
        },
    },
    getAuthToken: getAuthTokenMock,
}))

const loadRouter = async () => {
    vi.resetModules()
    const { default: router } = await import('@/router')
    return router
}

describe('router auth persistence', () => {
    beforeEach(() => {
        getAuthTokenMock.mockReset()
        isInitializedMock.mockReset()
        isInitializedMock.mockResolvedValue({ initialized: true })
        getAuthTokenMock.mockReturnValue(null)
        window.history.replaceState({}, '', '/')
    })

    it('keeps authenticated users on the app home route', async () => {
        getAuthTokenMock.mockReturnValue('persisted-token')
        const router = await loadRouter()

        await router.push('/')

        expect(router.currentRoute.value.fullPath).toBe('/')
    })

    it('blocks the login page when a persisted token exists', async () => {
        getAuthTokenMock.mockReturnValue('persisted-token')
        const router = await loadRouter()

        await router.push('/login')

        expect(router.currentRoute.value.fullPath).toBe('/')
    })

    it('redirects protected app routes to login when no token exists', async () => {
        const router = await loadRouter()

        await router.push('/albums')

        expect(router.currentRoute.value.fullPath).toBe('/login')
    })

    it('redirects unknown routes to home for authenticated users', async () => {
        getAuthTokenMock.mockReturnValue('persisted-token')
        const router = await loadRouter()

        await router.push('/missing-route')

        expect(router.currentRoute.value.fullPath).toBe('/')
    })

    it('redirects unknown routes to login when no token exists', async () => {
        const router = await loadRouter()

        await router.push('/missing-route')

        expect(router.currentRoute.value.fullPath).toBe('/login')
    })

    it('routes unauthenticated protected routes to login before initialization checks', async () => {
        const router = await loadRouter()

        await router.push('/')

        expect(router.currentRoute.value.fullPath).toBe('/login')
        expect(isInitializedMock).not.toHaveBeenCalled()
    })

    it('keeps authenticated users on initialization when the system is not initialized', async () => {
        getAuthTokenMock.mockReturnValue('persisted-token')
        isInitializedMock.mockResolvedValue({ initialized: false })
        const router = await loadRouter()

        await router.push('/')

        expect(router.currentRoute.value.fullPath).toBe('/init')
    })

    it('keeps unauthenticated protected routes on login when initialization check fails', async () => {
        isInitializedMock.mockRejectedValue(new Error('error sending request for url'))
        const router = await loadRouter()

        await router.push('/')

        expect(router.currentRoute.value.fullPath).toBe('/login')
        expect(isInitializedMock).not.toHaveBeenCalled()
    })

    it('checks initialization status only once across route changes', async () => {
        getAuthTokenMock.mockReturnValue('persisted-token')
        const router = await loadRouter()

        await router.push('/')
        await router.push('/albums')
        await router.push('/settings')

        expect(isInitializedMock).toHaveBeenCalledTimes(1)
    })

    it('resolves detail routes under plural collections', async () => {
        const router = await loadRouter()

        expect(router.resolve({ name: 'album-detail', params: { id: '12' } }).fullPath).toBe(
            '/albums/12',
        )
        expect(router.resolve({ name: 'playlist-detail', params: { id: '34' } }).fullPath).toBe(
            '/playlists/34',
        )
        expect(router.resolve({ name: 'work-detail', params: { id: '56' } }).fullPath).toBe(
            '/works/56',
        )
    })
})
