import { PLATFORM_KINDS, type PlatformKind, type PlatformRuntime } from './platform.shared'

export const isTauri = () => typeof window !== 'undefined' && '__TAURI_INTERNALS__' in window

const isPlatformKind = (value: string): value is PlatformKind =>
    PLATFORM_KINDS.some((platform) => platform === value)

export async function initPlatformRuntime(): Promise<void> {
    if (!isTauri()) {
        return
    }

    const { invoke } = await import('@tauri-apps/api/core')
    const config = await invoke<{ backend_url: string; platform: string }>('get_runtime_config')

    Reflect.set(window, '__UNIRHY_RUNTIME__', {
        apiBaseUrl: config.backend_url,
        platform: isPlatformKind(config.platform) ? config.platform : 'web',
    })
}

export function getPlatformRuntime(): PlatformRuntime {
    const injected = Reflect.get(window, '__UNIRHY_RUNTIME__')
    return {
        apiBaseUrl: injected?.apiBaseUrl ?? '',
        platform: injected?.platform ?? 'web',
    }
}

export function setPlatformApiBaseUrl(apiBaseUrl: string): void {
    const current = getPlatformRuntime()
    Reflect.set(window, '__UNIRHY_RUNTIME__', {
        ...current,
        apiBaseUrl,
    })
}

export function buildApiUrl(path: string): string {
    return `${getPlatformRuntime().apiBaseUrl}${path}`
}

export function buildWebSocketUrl(path: string): string {
    const { apiBaseUrl } = getPlatformRuntime()
    if (apiBaseUrl.length === 0) {
        if (typeof window === 'undefined') {
            return ''
        }
        const { origin } = window.location
        return `${origin.replace(/^http/iu, 'ws')}${path}`
    }
    return `${apiBaseUrl.replace(/^http/iu, 'ws')}${path}`
}

/**
 * 客户端自身版本号。仅在 Tauri 壳（桌面/移动端打包）中可用，
 * 由打包流程通过 tauri.conf.json 的 version 注入；纯浏览器访问时返回 null。
 */
export async function getClientVersion(): Promise<string | null> {
    if (!isTauri()) {
        return null
    }
    const { getVersion } = await import('@tauri-apps/api/app')
    return getVersion()
}
