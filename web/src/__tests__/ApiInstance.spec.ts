import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { api } from '@/ApiInstance'

const fetchMock = vi.fn<typeof fetch>()

describe('ApiInstance', () => {
    beforeEach(() => {
        fetchMock.mockReset()
        vi.stubGlobal('fetch', fetchMock)
        delete window.__UNIRHY_RUNTIME__
        delete window.__tenant
    })

    afterEach(() => {
        delete window.__UNIRHY_RUNTIME__
        delete window.__tenant
    })

    it('includes credentials when logging in against a runtime api host', async () => {
        window.__UNIRHY_RUNTIME__ = {
            apiBaseUrl: 'http://127.0.0.1:8654',
            platform: 'macos',
        }
        fetchMock.mockResolvedValueOnce(new Response(null, { status: 200 }))

        await api.tokenController.login({
            body: {
                email: 'alice@example.com',
                password: 'secret',
            },
        })

        expect(fetchMock).toHaveBeenCalledWith(
            'http://127.0.0.1:8654/api/tokens',
            expect.objectContaining({
                method: 'POST',
                credentials: 'include',
            }),
        )
    })

    it('includes credentials on authenticated runtime api requests', async () => {
        window.__UNIRHY_RUNTIME__ = {
            apiBaseUrl: 'http://127.0.0.1:8654',
            platform: 'macos',
        }
        fetchMock.mockResolvedValueOnce(Response.json({ id: 1, name: 'alice' }))

        await api.accountController.me()

        expect(fetchMock).toHaveBeenCalledWith(
            'http://127.0.0.1:8654/api/accounts/me',
            expect.objectContaining({
                method: 'GET',
                credentials: 'include',
            }),
        )
    })
})
