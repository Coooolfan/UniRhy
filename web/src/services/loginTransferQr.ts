// oxlint-disable no-bitwise -- 二进制载荷的打包/解包本就以位运算表达，改写成算术反而更难读
// oxlint-disable max-classes-per-file -- 两个错误类型都描述本模块的载荷契约，拆分反而割裂语义
/**
 * 登录交接载荷的规范形式是一段紧凑二进制：
 *
 * ```text
 * flags(1B) | 地址 | port(2B) | secret(10B)
 * ```
 *
 * flags 的 bit0-2 是协议版本，bit3 是 scheme（0=http，1=https），bit4-5 是地址类型。
 * 各呈现介质按自身特点选择编码：二维码用数字模式（QR 数字模式 3.32 bit/字符，
 * 几乎无浪费，比 Base64 的字节模式省一档版本），深链接与 NFC 用 Base64URL，
 * 蓝牙广播直接发原始字节。
 */
export type LoginTransferTarget = {
    serverUrl: string
    secret: string
}

const PAYLOAD_VERSION = 1
const SECRET_BYTES = 10
const ADDR_IPV4 = 0
const ADDR_HOST = 1

/**
 * 域名后缀字典：把常见后缀压成一个索引，载荷里用 `索引 = 位置 + 1`，`0` 表示未命中。
 *
 * 只允许在末尾追加，不得重排或删除——否则旧客户端会把索引解析成别的后缀。
 */
const HOST_SUFFIXES = [
    '.com',
    '.net',
    '.org',
    '.cn',
    '.io',
    '.dev',
    '.app',
    '.me',
    '.xyz',
    '.top',
    '.local',
    '.lan',
    '.internal',
    '.duckdns.org',
    '.ddns.net',
    '.no-ip.org',
    '.tailscale.net',
    '.ts.net',
    '.zerotier.com',
    '.nip.io',
    '.sslip.io',
] as const

const LOOPBACK_HOSTNAME_PATTERN = /^(?:localhost|127(?:\.\d{1,3}){3}|0\.0\.0\.0|\[::1?\])$/iu
const IPV4_PATTERN = /^(?:\d{1,3}\.){3}\d{1,3}$/u
const SECRET_PATTERN = /^[A-Za-z0-9_-]{14}$/u
const DIGITS_PATTERN = /^\d+$/u

/** 服务端地址只在本机可达，手机扫码后无法连接。 */
export class LoopbackServerUrlError extends Error {
    public constructor(serverUrl: string) {
        super(`Server URL is only reachable on this device: ${serverUrl}`)
        this.name = 'LoopbackServerUrlError'
    }
}

/** 载荷版本或字典索引超出当前客户端的认知，通常意味着需要升级客户端。 */
export class UnsupportedPayloadError extends Error {
    public constructor(detail: string) {
        super(`Unsupported login transfer payload: ${detail}`)
        this.name = 'UnsupportedPayloadError'
    }
}

const isLoopbackHostname = (hostname: string): boolean => LOOPBACK_HOSTNAME_PATTERN.test(hostname)

const normalizeServerUrl = (rawUrl: string): string => {
    const url = new URL(rawUrl)
    if (!['http:', 'https:'].includes(url.protocol)) {
        throw new Error('Unsupported server URL protocol')
    }
    if (url.username || url.password || url.search || url.hash) {
        throw new Error('Invalid server URL')
    }
    return url.toString().replace(/\/$/u, '')
}

/**
 * 校验服务端地址可被其他设备访问，并返回规范化后的地址。
 *
 * 打包函数内部无条件调用它，因此不存在绕过校验产出不可用载荷的入口；
 * 界面也可以提前调用它做预检，避免为注定失败的地址先创建交接。
 */
export const assertReachableServerUrl = (rawUrl: string): string => {
    const serverUrl = normalizeServerUrl(rawUrl)
    if (isLoopbackHostname(new URL(serverUrl).hostname)) {
        throw new LoopbackServerUrlError(serverUrl)
    }
    return serverUrl
}

const base64UrlToBytes = (value: string): Uint8Array => {
    const padded = value.split('-').join('+').split('_').join('/')
    const suffix = padded.length % 4 === 0 ? '' : '='.repeat(4 - (padded.length % 4))
    const binary = atob(padded + suffix)
    return Uint8Array.from(binary, (char) => char.codePointAt(0) ?? 0)
}

const bytesToBase64Url = (bytes: Uint8Array): string => {
    let binary = ''
    for (const byte of bytes) binary += String.fromCodePoint(byte)
    return btoa(binary).split('+').join('-').split('/').join('_').replace(/=+$/u, '')
}

const digitsForBytes = (byteLength: number): number => Math.ceil(byteLength * 8 * Math.log10(2))

const bytesToDigits = (bytes: Uint8Array): string => {
    let value = 0n
    for (const byte of bytes) value = (value << 8n) | BigInt(byte)
    return value.toString().padStart(digitsForBytes(bytes.length), '0')
}

const digitsToBytes = (digits: string): Uint8Array => {
    let byteLength = 0
    for (let candidate = 1; candidate <= 128; candidate += 1) {
        if (digitsForBytes(candidate) === digits.length) {
            byteLength = candidate
            break
        }
    }
    if (byteLength === 0) throw new UnsupportedPayloadError(`digit length ${digits.length}`)

    let value = BigInt(digits)
    const bytes = new Uint8Array(byteLength)
    for (let index = byteLength - 1; index >= 0; index -= 1) {
        bytes[index] = Number(value & 255n)
        value >>= 8n
    }
    if (value !== 0n) throw new UnsupportedPayloadError('payload overflows declared length')
    return bytes
}

/** 把目标实例与密钥打包成规范二进制形式。 */
export const packLoginTransferPayload = (target: LoginTransferTarget): Uint8Array => {
    const serverUrl = assertReachableServerUrl(target.serverUrl)
    if (!SECRET_PATTERN.test(target.secret)) throw new Error('Invalid login transfer secret')

    const url = new URL(serverUrl)
    const https = url.protocol === 'https:'
    const port = Number(url.port || (https ? 443 : 80))
    const hostname = url.hostname

    const isIpv4 = IPV4_PATTERN.test(hostname)
    const addrType = isIpv4 ? ADDR_IPV4 : ADDR_HOST

    let address: number[]
    if (isIpv4) {
        address = hostname.split('.').map(Number)
        if (address.some((part) => part > 255)) throw new Error('Invalid IPv4 address')
    } else {
        let bestIndex = -1
        HOST_SUFFIXES.forEach((suffix, index) => {
            const isLonger = bestIndex < 0 || suffix.length > HOST_SUFFIXES[bestIndex].length
            if (hostname.endsWith(suffix) && isLonger) bestIndex = index
        })
        const label = bestIndex < 0 ? hostname : hostname.slice(0, -HOST_SUFFIXES[bestIndex].length)
        const labelBytes = new TextEncoder().encode(label)
        if (labelBytes.length > 255) throw new Error('Host label is too long')
        address = [bestIndex + 1, labelBytes.length, ...labelBytes]
    }

    const flags = PAYLOAD_VERSION | (https ? 1 << 3 : 0) | (addrType << 4)
    return Uint8Array.from([
        flags,
        ...address,
        (port >> 8) & 255,
        port & 255,
        ...base64UrlToBytes(target.secret),
    ])
}

/** 解析规范二进制形式，还原目标实例与密钥。 */
export const unpackLoginTransferPayload = (bytes: Uint8Array): LoginTransferTarget => {
    if (bytes.length < 1 + 2 + SECRET_BYTES) throw new UnsupportedPayloadError('payload too short')

    const flags = bytes[0]
    const version = flags & 0b111
    if (version !== PAYLOAD_VERSION) throw new UnsupportedPayloadError(`version ${version}`)
    const https = ((flags >> 3) & 1) === 1
    const addrType = (flags >> 4) & 0b11

    let cursor = 1
    let hostname: string
    if (addrType === ADDR_IPV4) {
        hostname = Array.from(bytes.slice(cursor, cursor + 4)).join('.')
        cursor += 4
    } else if (addrType === ADDR_HOST) {
        const suffixIndex = bytes[cursor]
        const labelLength = bytes[cursor + 1]
        cursor += 2
        const label = new TextDecoder().decode(bytes.slice(cursor, cursor + labelLength))
        cursor += labelLength
        if (suffixIndex > HOST_SUFFIXES.length) {
            throw new UnsupportedPayloadError(`host suffix index ${suffixIndex}`)
        }
        hostname = suffixIndex === 0 ? label : label + HOST_SUFFIXES[suffixIndex - 1]
    } else {
        throw new UnsupportedPayloadError(`address type ${addrType}`)
    }

    const port = (bytes[cursor] << 8) | bytes[cursor + 1]
    cursor += 2
    const secretBytes = bytes.slice(cursor, cursor + SECRET_BYTES)
    if (secretBytes.length !== SECRET_BYTES) throw new UnsupportedPayloadError('secret truncated')

    const scheme = https ? 'https' : 'http'
    const defaultPort = https ? 443 : 80
    const authority = port === defaultPort ? hostname : `${hostname}:${port}`
    return {
        serverUrl: `${scheme}://${authority}`,
        secret: bytesToBase64Url(secretBytes),
    }
}

/** 二维码编码：纯数字，命中 QR 数字模式。 */
export const encodeLoginTransferQr = (target: LoginTransferTarget): string =>
    bytesToDigits(packLoginTransferPayload(target))

export const decodeLoginTransferQr = (payload: string): LoginTransferTarget => {
    const digits = payload.trim()
    if (!DIGITS_PATTERN.test(digits)) throw new UnsupportedPayloadError('not a numeric payload')
    return unpackLoginTransferPayload(digitsToBytes(digits))
}

/** 深链接与 NFC 编码：保留可识别的 scheme，便于系统层分发。 */
export const encodeLoginTransferUri = (target: LoginTransferTarget): string =>
    `unirhy://t/${bytesToBase64Url(packLoginTransferPayload(target))}`

export const decodeLoginTransferUri = (uri: string): LoginTransferTarget => {
    const url = new URL(uri.trim())
    if (url.protocol !== 'unirhy:' || url.hostname !== 't') {
        throw new UnsupportedPayloadError('unrecognized URI')
    }
    return unpackLoginTransferPayload(base64UrlToBytes(url.pathname.replace(/^\//u, '')))
}

/**
 * 从扫码内容中提取服务端地址。
 *
 * 登录交接载荷取其实例地址，其余内容按纯 http(s) URL 解析，
 * 因此既兼容本应用生成的登录二维码，也兼容只包含地址的普通二维码。
 */
export const parseServerUrlFromScan = (content: string): string => {
    try {
        return decodeLoginTransferQr(content).serverUrl
    } catch {
        // 不是交接载荷则按纯 URL 处理
    }
    try {
        return normalizeServerUrl(content.trim())
    } catch {
        throw new UnsupportedPayloadError('no server address in scanned content')
    }
}
