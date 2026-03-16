import {
    PLATFORM_KINDS,
    type InjectedPlatformRuntime,
    type PlatformKind,
    type PlatformRuntime,
} from '@/runtime/platform.shared'

const DEFAULT_PLATFORM_RUNTIME: PlatformRuntime = {
    apiBaseUrl: '',
    platform: 'web',
}

const normalizeApiBaseUrl = (value: unknown) => {
    if (typeof value !== 'string') {
        return ''
    }

    const trimmed = value.trim()
    if (trimmed === '' || trimmed === '/') {
        return ''
    }

    return trimmed.endsWith('/') ? trimmed.slice(0, -1) : trimmed
}

const normalizePlatformKind = (value: unknown): PlatformKind => {
    if (typeof value !== 'string') {
        return 'web'
    }

    return PLATFORM_KINDS.find((platform) => platform === value) ?? 'web'
}

export const getPlatformRuntime = (): PlatformRuntime => {
    if (typeof window === 'undefined') {
        return DEFAULT_PLATFORM_RUNTIME
    }

    const runtime: InjectedPlatformRuntime | undefined = window.__UNIRHY_RUNTIME__
    return {
        apiBaseUrl: normalizeApiBaseUrl(runtime?.apiBaseUrl),
        platform: normalizePlatformKind(runtime?.platform),
    }
}

const resolveApiOrigin = (apiBaseUrl: string) => {
    if (typeof window === 'undefined') {
        return ''
    }

    if (apiBaseUrl === '') {
        return window.location.origin
    }

    return new URL(apiBaseUrl, window.location.origin).origin
}

export const buildApiUrl = (path: string) => {
    return `${getPlatformRuntime().apiBaseUrl}${path}`
}

export const buildWebSocketUrl = (path: string) => {
    if (typeof window === 'undefined') {
        return ''
    }

    const apiOrigin = resolveApiOrigin(getPlatformRuntime().apiBaseUrl)
    const wsOrigin = apiOrigin.replace(/^http/i, 'ws')
    return new URL(path, `${wsOrigin}/`).toString()
}
