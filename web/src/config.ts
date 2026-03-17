const SERVER_URL_STORAGE_KEY = 'unirhy.server-url'

const resolveBaseUrl = (): string => {
    if (typeof window !== 'undefined') {
        const stored = window.localStorage.getItem(SERVER_URL_STORAGE_KEY)
        if (stored && stored.trim().length > 0) {
            return stored.trim().replace(/\/+$/, '')
        }
    }

    const envUrl = import.meta.env.VITE_API_BASE_URL
    if (typeof envUrl === 'string' && envUrl.trim().length > 0) {
        return envUrl.trim().replace(/\/+$/, '')
    }

    return ''
}

let cachedBaseUrl: string | null = null

export const getApiBaseUrl = (): string => {
    if (cachedBaseUrl === null) {
        cachedBaseUrl = resolveBaseUrl()
    }
    return cachedBaseUrl
}

export const getWsBaseUrl = (): string => {
    const base = getApiBaseUrl()
    if (base.length === 0) {
        return ''
    }
    return base.replace(/^http/i, 'ws')
}
