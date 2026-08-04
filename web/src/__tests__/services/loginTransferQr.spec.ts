// oxlint-disable no-bitwise -- 需要直接篡改载荷字节来构造非法版本号
import { describe, expect, it } from 'vitest'
import {
    assertReachableServerUrl,
    decodeLoginTransferDigits,
    decodeLoginTransferScan,
    decodeLoginTransferUri,
    encodeLoginTransferDigits,
    encodeLoginTransferUri,
    LoopbackServerUrlError,
    packLoginTransferPayload,
    UnsupportedPayloadError,
} from '@/services/loginTransferQr'

const SECRET = 'q6urq6urq6urqw'

describe('login transfer payload codec', () => {
    it.each([
        ['http://192.168.0.145:8655', 'IPv4 + 端口'],
        ['https://music.example.com', '域名命中 .com 字典'],
        ['https://nas.duckdns.org', '域名命中多段后缀字典'],
        ['https://host.tailscale.net', 'Tailscale 后缀'],
        ['http://unirhy.local:8654', 'mDNS 后缀'],
        ['https://server.unusual-tld-xyzzy', '未命中字典的后缀'],
        ['http://10.0.0.2', '默认端口省略'],
    ])('round trips %s (%s)', (serverUrl) => {
        const target = { serverUrl, secret: SECRET }
        expect(decodeLoginTransferDigits(encodeLoginTransferDigits(target))).toEqual(target)
        expect(decodeLoginTransferUri(encodeLoginTransferUri(target))).toEqual(target)
    })

    it('encodes IPv4 targets into 17 bytes / 41 digits', () => {
        const target = { serverUrl: 'http://192.168.0.145:8655', secret: SECRET }
        expect(packLoginTransferPayload(target)).toHaveLength(17)
        expect(encodeLoginTransferDigits(target)).toHaveLength(41)
    })

    it('keeps dictionary hits shorter than raw suffixes', () => {
        const withDict = encodeLoginTransferDigits({
            serverUrl: 'https://nas.duckdns.org',
            secret: SECRET,
        })
        const withoutDict = encodeLoginTransferDigits({
            serverUrl: 'https://nas.unusual-tld-xyzzy',
            secret: SECRET,
        })
        expect(withDict.length).toBeLessThan(withoutDict.length)
    })

    it('emits only digits for the digits form', () => {
        expect(encodeLoginTransferDigits({ serverUrl: 'http://10.0.0.2', secret: SECRET })).toMatch(
            /^\d+$/u,
        )
    })

    it('rejects loopback instance URLs', () => {
        for (const url of [
            'http://localhost:8654',
            'http://127.0.0.1:8654',
            'http://127.1.2.3:8654',
            'http://0.0.0.0:8654',
        ]) {
            expect(() => assertReachableServerUrl(url)).toThrow(LoopbackServerUrlError)
            expect(() => encodeLoginTransferDigits({ serverUrl: url, secret: SECRET })).toThrow(
                LoopbackServerUrlError,
            )
        }
        expect(assertReachableServerUrl('https://music.example.com')).toBe(
            'https://music.example.com',
        )
    })

    it('rejects malformed payloads', () => {
        expect(() => decodeLoginTransferDigits('not-digits')).toThrow(UnsupportedPayloadError)
        expect(() => decodeLoginTransferDigits('123')).toThrow(UnsupportedPayloadError)
        expect(() => decodeLoginTransferUri('https://example.com/t/abc')).toThrow(
            UnsupportedPayloadError,
        )
    })

    it('rejects an unknown payload version', () => {
        const bytes = packLoginTransferPayload({
            serverUrl: 'http://10.0.0.2',
            secret: SECRET,
        })
        bytes[0] = (bytes[0] & 0b11111000) | 7
        const tampered = `unirhy://t/${btoa(String.fromCodePoint(...bytes))
            .split('+')
            .join('-')
            .split('/')
            .join('_')
            .replace(/=+$/u, '')}`
        expect(() => decodeLoginTransferUri(tampered)).toThrow(UnsupportedPayloadError)
    })

    it('decodes both URI and digits forms at the scan entry', () => {
        const target = { serverUrl: 'http://192.168.0.145:8655', secret: SECRET }
        expect(decodeLoginTransferScan(encodeLoginTransferUri(target))).toEqual(target)
        expect(decodeLoginTransferScan(encodeLoginTransferDigits(target))).toEqual(target)
        expect(() => decodeLoginTransferScan('neither-form')).toThrow(UnsupportedPayloadError)
    })
})
