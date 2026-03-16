import { afterEach, describe, expect, it } from 'vitest'
import { buildApiUrl, buildWebSocketUrl, getPlatformRuntime } from '@/runtime/platform'

describe('platform runtime', () => {
    afterEach(() => {
        delete window.__UNIRHY_RUNTIME__
    })

    it('defaults to browser-relative api urls', () => {
        Object.defineProperty(window, 'location', {
            configurable: true,
            value: {
                origin: 'http://localhost:5173',
            },
        })

        expect(getPlatformRuntime()).toEqual({
            apiBaseUrl: '',
            platform: 'web',
        })
        expect(buildApiUrl('/api/tokens')).toBe('/api/tokens')
        expect(buildWebSocketUrl('/ws/playback-sync')).toBe('ws://localhost:5173/ws/playback-sync')
    })

    it('reads runtime overrides from the host shell', () => {
        Object.defineProperty(window, 'location', {
            configurable: true,
            value: {
                origin: 'http://localhost:5173',
            },
        })
        window.__UNIRHY_RUNTIME__ = {
            apiBaseUrl: 'http://127.0.0.1:8654/',
            platform: 'macos',
        }

        expect(getPlatformRuntime()).toEqual({
            apiBaseUrl: 'http://127.0.0.1:8654',
            platform: 'macos',
        })
        expect(buildApiUrl('/api/tokens')).toBe('http://127.0.0.1:8654/api/tokens')
        expect(buildWebSocketUrl('/ws/playback-sync')).toBe('ws://127.0.0.1:8654/ws/playback-sync')
    })

    it('falls back to web for unknown runtime platforms', () => {
        Object.defineProperty(window, 'location', {
            configurable: true,
            value: {
                origin: 'http://localhost:5173',
            },
        })
        Object.defineProperty(window, '__UNIRHY_RUNTIME__', {
            configurable: true,
            value: {
                apiBaseUrl: 'http://127.0.0.1:8654/',
                platform: 'desktop',
            },
        })

        expect(getPlatformRuntime()).toEqual({
            apiBaseUrl: 'http://127.0.0.1:8654',
            platform: 'web',
        })
    })
})
