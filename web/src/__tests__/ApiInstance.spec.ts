import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const EXPIRED_TOKEN_COOKIE = 'token=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT'
const AUTH_EXPIRED_MESSAGE = '登录已过期，请重新登录'

const fetchMock = vi.fn<typeof fetch>()
const replaceMock = vi.fn()

const originalLocationDescriptor = Object.getOwnPropertyDescriptor(window, 'location')

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

const installCookieSpy = () => {
    let cookieValue = ''
    const assignments: string[] = []

    Object.defineProperty(document, 'cookie', {
        configurable: true,
        get: () => cookieValue,
        set: (value: string) => {
            assignments.push(value)

            const [cookiePair] = value.split(';')
            const [rawName, ...rawValueParts] = cookiePair.split('=')
            const cookieName = rawName.trim()
            const nextValue = rawValueParts.join('=').trim()

            if (cookieName === 'token' && nextValue.length === 0) {
                cookieValue = ''
                return
            }

            cookieValue = cookiePair.trim()
        },
    })

    return {
        seed: (value: string) => {
            cookieValue = value
        },
        getValue: () => cookieValue,
        getClearCount: () =>
            assignments.filter((assignment) => assignment === EXPIRED_TOKEN_COOKIE).length,
    }
}

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
        Reflect.deleteProperty(document, 'cookie')
        if (originalLocationDescriptor) {
            Object.defineProperty(window, 'location', originalLocationDescriptor)
        }
    })

    it('handles concurrent non-login 401 responses with one alert and one redirect', async () => {
        const alertMock = vi.spyOn(window, 'alert').mockImplementation(() => undefined)
        const cookieSpy = installCookieSpy()
        cookieSpy.seed('token=active')
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
        expect(cookieSpy.getClearCount()).toBe(1)
        expect(cookieSpy.getValue()).toBe('')
    })

    it('does not treat login 401 as an expired-session redirect', async () => {
        const alertMock = vi.spyOn(window, 'alert').mockImplementation(() => undefined)
        const cookieSpy = installCookieSpy()
        cookieSpy.seed('token=active')
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
        expect(cookieSpy.getClearCount()).toBe(0)
        expect(cookieSpy.getValue()).toBe('token=active')
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
