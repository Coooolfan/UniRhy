import { describe, expect, it } from 'vitest'
import {
    assertReachableServerUrl,
    buildLoginTransferQrPayload,
    LoopbackServerUrlError,
    parseLoginTransferQrPayload,
} from '@/services/loginTransferQr'

const payload = {
    serverUrl: 'https://music.example.com/',
    transferId: '018f3cf4-2e9a-7d92-8ae4-5d3f640a69bc',
    secret: 'Q0W5wYI3US6oxCLyY7XB7qfwePzP0W8B3Gxmpc6x7hs',
}

describe('login transfer QR payload', () => {
    it('round trips a valid payload', () => {
        const encoded = buildLoginTransferQrPayload(payload)

        expect(parseLoginTransferQrPayload(encoded)).toEqual({
            ...payload,
            serverUrl: 'https://music.example.com',
        })
    })

    it('rejects unsupported protocols and malformed credentials', () => {
        expect(() =>
            parseLoginTransferQrPayload(
                'https://music.example.com/login-transfer?v=1&transfer=x&secret=y',
            ),
        ).toThrow()
        expect(() =>
            buildLoginTransferQrPayload({ ...payload, serverUrl: 'file:///tmp/server' }),
        ).toThrow()
    })

    it('detects loopback instance URLs', () => {
        expect(() => assertReachableServerUrl('http://localhost:8654')).toThrow(
            LoopbackServerUrlError,
        )
        expect(() => assertReachableServerUrl('http://127.0.0.1:8654')).toThrow(
            LoopbackServerUrlError,
        )
        expect(() => assertReachableServerUrl('http://127.1.2.3:8654')).toThrow(
            LoopbackServerUrlError,
        )
        expect(() => assertReachableServerUrl('http://0.0.0.0:8654')).toThrow(
            LoopbackServerUrlError,
        )
        expect(() => assertReachableServerUrl('http://[::1]:8654')).toThrow(LoopbackServerUrlError)
        expect(assertReachableServerUrl('https://music.example.com')).toBe(
            'https://music.example.com',
        )
    })

    it('refuses to encode a payload pointing at a loopback instance', () => {
        expect(() =>
            buildLoginTransferQrPayload({ ...payload, serverUrl: 'http://localhost:8654' }),
        ).toThrow(LoopbackServerUrlError)
    })
})
