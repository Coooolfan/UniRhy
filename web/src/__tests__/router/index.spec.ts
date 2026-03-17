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

    it('redirects root to dashboard when a persisted token exists', async () => {
        getAuthTokenMock.mockReturnValue('persisted-token')
        const router = await loadRouter()

        await router.push('/')

        expect(router.currentRoute.value.fullPath).toBe('/dashboard')
    })

    it('blocks the login page when a persisted token exists', async () => {
        getAuthTokenMock.mockReturnValue('persisted-token')
        const router = await loadRouter()

        await router.push('/login')

        expect(router.currentRoute.value.fullPath).toBe('/dashboard')
    })

    it('redirects dashboard routes to login when no token exists', async () => {
        const router = await loadRouter()

        await router.push('/dashboard')

        expect(router.currentRoute.value.fullPath).toBe('/login')
    })

    it('keeps initialization routing ahead of auth routing', async () => {
        getAuthTokenMock.mockReturnValue('persisted-token')
        isInitializedMock.mockResolvedValue({ initialized: false })
        const router = await loadRouter()

        await router.push('/')

        expect(router.currentRoute.value.fullPath).toBe('/init')
    })
})
