import { PLATFORM_KINDS, type PlatformKind, type PlatformRuntime } from './platform.shared'

const isTauri = () => typeof window !== 'undefined' && '__TAURI_INTERNALS__' in window

const isPlatformKind = (value: string): value is PlatformKind =>
    PLATFORM_KINDS.some((platform) => platform === value)

export async function initPlatformRuntime(): Promise<void> {
    if (!isTauri()) {
        return
    }

    const { invoke } = await import('@tauri-apps/api/core')
    const config = await invoke<{ proxy_url: string; platform: string }>('get_runtime_config')

    window.__UNIRHY_RUNTIME__ = {
        apiBaseUrl: config.proxy_url,
        platform: isPlatformKind(config.platform) ? config.platform : 'web',
    }
}

export function getPlatformRuntime(): PlatformRuntime {
    const injected = window.__UNIRHY_RUNTIME__
    return {
        apiBaseUrl: injected?.apiBaseUrl ?? '',
        platform: injected?.platform ?? 'web',
    }
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
        return `${origin.replace(/^http/i, 'ws')}${path}`
    }
    return `${apiBaseUrl.replace(/^http/i, 'ws')}${path}`
}
