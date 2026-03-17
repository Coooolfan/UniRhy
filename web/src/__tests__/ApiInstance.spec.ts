import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const TOKEN_STORAGE_KEY = 'unirhy.auth-token'
const AUTH_EXPIRED_MESSAGE = '登录已过期，请重新登录'

const fetchMock = vi.fn<typeof fetch>()
const replaceMock = vi.fn()

const originalLocationDescriptor = Object.getOwnPropertyDescriptor(window, 'location')
const clearTokenCookie = () => {
    document.cookie = 'unirhy-token=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT'
}

const createJsonResponse = (status: number, body: unknown) =>
    Response.json(body, {
        status,
        headers: {
            'content-type': 'application/json;charset=UTF-8',
        },
    })

const createTextResponse = (status: number, body: string) =>
    new Response(body, {
        status,
        headers: {
            'content-type': 'text/plain;charset=UTF-8',
        },
    })

const loadApiInstance = () => {
    vi.resetModules()
    return import('@/ApiInstance')
}

describe('ApiInstance', () => {
    beforeEach(() => {
        fetchMock.mockReset()
        replaceMock.mockReset()
        vi.restoreAllMocks()
        vi.stubGlobal('fetch', fetchMock)
        window.localStorage.clear()
        clearTokenCookie()
        Object.defineProperty(window, 'location', {
            configurable: true,
            value: {
                replace: replaceMock,
            },
        })
    })

    afterEach(() => {
        vi.unstubAllGlobals()
        vi.restoreAllMocks()
        window.localStorage.clear()
        clearTokenCookie()
        if (originalLocationDescriptor) {
            Object.defineProperty(window, 'location', originalLocationDescriptor)
        }
    })

    it('handles concurrent non-login 401 responses with one alert and one redirect', async () => {
        const alertMock = vi.spyOn(window, 'alert').mockImplementation(() => undefined)
        window.localStorage.setItem(TOKEN_STORAGE_KEY, 'test-token')
        document.cookie = 'unirhy-token=test-token; path=/'
        fetchMock.mockImplementation(() =>
            Promise.resolve(createJsonResponse(401, { message: '未登录' })),
        )

        const { api } = await loadApiInstance()
        const results = await Promise.allSettled([
            api.accountController.me(),
            api.playlistController.listPlaylists(),
            api.systemConfigController.get(),
        ])

        expect(fetchMock).toHaveBeenCalledTimes(3)
        expect(results.every((result) => result.status === 'rejected')).toBe(true)
        expect(alertMock).toHaveBeenCalledTimes(1)
        expect(alertMock).toHaveBeenCalledWith(AUTH_EXPIRED_MESSAGE)
        expect(replaceMock).toHaveBeenCalledTimes(1)
        expect(replaceMock).toHaveBeenCalledWith('/')
        expect(window.localStorage.getItem(TOKEN_STORAGE_KEY)).toBeNull()
        expect(document.cookie).not.toContain('unirhy-token=')
    })

    it('does not treat login 401 as an expired-session redirect', async () => {
        const alertMock = vi.spyOn(window, 'alert').mockImplementation(() => undefined)
        window.localStorage.setItem(TOKEN_STORAGE_KEY, 'test-token')
        document.cookie = 'unirhy-token=test-token; path=/'
        fetchMock.mockImplementation(() =>
            Promise.resolve(createJsonResponse(401, { message: '登录失败' })),
        )

        const { api } = await loadApiInstance()

        await expect(
            api.tokenController.login({
                body: {
                    email: 'user@example.com',
                    password: 'secret',
                },
            }),
        ).rejects.toEqual({ message: '登录失败' })

        expect(alertMock).not.toHaveBeenCalled()
        expect(replaceMock).not.toHaveBeenCalled()
        expect(window.localStorage.getItem(TOKEN_STORAGE_KEY)).toBe('test-token')
        expect(document.cookie).toContain('unirhy-token=test-token')
    })

    it('keeps existing non-401 error behavior', async () => {
        const consoleErrorMock = vi.spyOn(console, 'error').mockImplementation(() => undefined)
        fetchMock
            .mockImplementationOnce(() =>
                Promise.resolve(createTextResponse(500, 'server exploded')),
            )
            .mockImplementationOnce(() =>
                Promise.resolve(createJsonResponse(400, { message: '请求参数不正确' })),
            )

        const { api } = await loadApiInstance()

        await expect(api.accountController.me()).rejects.toThrow('请求失败：server exploded')
        expect(consoleErrorMock).toHaveBeenCalledWith(
            '服务器错误:',
            500,
            '/api/accounts/me',
            'server exploded',
        )

        await expect(api.playlistController.listPlaylists()).rejects.toEqual({
            message: '请求参数不正确',
        })
    })
})
