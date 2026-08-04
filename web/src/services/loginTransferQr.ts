export type LoginTransferQrPayload = {
    serverUrl: string
    transferId: string
    secret: string
}

const QR_PROTOCOL = 'unirhy:'
const QR_HOST = 'login-transfer'
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/iu
const CREDENTIAL_PATTERN = /^[A-Za-z0-9_-]{43}$/u
const LOOPBACK_HOSTNAME_PATTERN = /^(?:localhost|127(?:\.\d{1,3}){3}|0\.0\.0\.0|\[::1?\])$/iu

/**
 * 服务端地址只在本机可达，手机扫码后无法连接。
 *
 * 单独成类，便于调用方把它映射成「请配置局域网或公网地址」这类具体指引，
 * 而不是笼统的二维码错误。
 */
export class LoopbackServerUrlError extends Error {
    public constructor(serverUrl: string) {
        super(`Server URL is only reachable on this device: ${serverUrl}`)
        this.name = 'LoopbackServerUrlError'
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
 * [buildLoginTransferQrPayload] 内部无条件调用它，因此不存在「绕过校验产出不可用二维码」的入口；
 * 界面也可以提前调用它做预检，避免为一个注定失败的地址先创建交接。
 */
export const assertReachableServerUrl = (rawUrl: string): string => {
    const serverUrl = normalizeServerUrl(rawUrl)
    if (isLoopbackHostname(new URL(serverUrl).hostname)) {
        throw new LoopbackServerUrlError(serverUrl)
    }
    return serverUrl
}

/** 构建二维码载荷；回环地址在此被拒绝，编解码本身不会产出扫不通的码。 */
export const buildLoginTransferQrPayload = (payload: LoginTransferQrPayload): string => {
    const url = new URL(`${QR_PROTOCOL}//${QR_HOST}`)
    url.searchParams.set('v', '1')
    url.searchParams.set('server', assertReachableServerUrl(payload.serverUrl))
    url.searchParams.set('transfer', payload.transferId)
    url.searchParams.set('secret', payload.secret)
    return url.toString()
}

export const parseLoginTransferQrPayload = (rawPayload: string): LoginTransferQrPayload => {
    const url = new URL(rawPayload.trim())
    if (url.protocol !== QR_PROTOCOL || url.hostname !== QR_HOST) {
        throw new Error('Unsupported QR code')
    }
    if (url.searchParams.get('v') !== '1') {
        throw new Error('Unsupported QR code version')
    }

    const serverUrl = url.searchParams.get('server')
    const transferId = url.searchParams.get('transfer')
    const secret = url.searchParams.get('secret')
    if (!serverUrl || !transferId || !secret) {
        throw new Error('Incomplete QR code')
    }
    if (!UUID_PATTERN.test(transferId) || !CREDENTIAL_PATTERN.test(secret)) {
        throw new Error('Invalid QR code')
    }

    return {
        serverUrl: normalizeServerUrl(serverUrl),
        transferId,
        secret,
    }
}
