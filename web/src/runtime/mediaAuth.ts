import { getAuthToken } from '@/ApiInstance'
import { buildApiUrl, getPlatformRuntime } from '@/runtime/platform'

export const MEDIA_AUTH_TOKEN_QUERY_PARAM = 'unirhy-proxy-token'

const isAbsoluteUrl = (value: string) => /^(?:[a-z][a-z\d+.-]*:|\/\/)/i.test(value)

const appendProxyToken = (url: string) => {
    const token = getAuthToken()
    const { apiBaseUrl } = getPlatformRuntime()
    if (!token || apiBaseUrl.length === 0) {
        return url
    }

    try {
        const proxyBaseUrl = new URL(apiBaseUrl)
        const resolvedUrl = new URL(url, proxyBaseUrl)
        if (resolvedUrl.origin !== proxyBaseUrl.origin) {
            return resolvedUrl.toString()
        }

        resolvedUrl.searchParams.set(MEDIA_AUTH_TOKEN_QUERY_PARAM, token)
        return resolvedUrl.toString()
    } catch {
        return url
    }
}

export const buildMediaUrl = (path: string) => {
    return appendProxyToken(buildApiUrl(path))
}

export const normalizeMediaUrl = (url: string) => {
    if (isAbsoluteUrl(url)) {
        return appendProxyToken(url)
    }
    return buildMediaUrl(url)
}
