import { Api, type ApiErrors } from './__generated'

const BASE_URL = ''
const AUTH_EXPIRED_MESSAGE = '登录已过期，请重新登录'

declare global {
    interface Window {
        __tenant?: string
    }
}

export type ApiErrorShape = {
    message?: string
    [key: string]: unknown
}

const isRecord = (value: unknown): value is Record<string, unknown> =>
    typeof value === 'object' && value !== null

let hasHandledAuthExpiry = false

const isLoginRequest = (uri: string, method: string) =>
    uri.includes('/api/tokens') && method === 'POST'

export const clearAuthToken = () => {
    document.cookie = 'token=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT'
}

const handleAuthExpiry = () => {
    if (!hasHandledAuthExpiry) {
        hasHandledAuthExpiry = true
        clearAuthToken()
        window.alert(AUTH_EXPIRED_MESSAGE)
        window.location.replace('/')
    }

    throw new Error(AUTH_EXPIRED_MESSAGE)
}

export function normalizeApiError(error: unknown): ApiErrorShape
export function normalizeApiError<C extends keyof ApiErrors, M extends keyof ApiErrors[C]>(
    error: unknown,
    _controller: C,
    _method: M,
): ApiErrors[C][M]
export function normalizeApiError(
    error: unknown,
    _controller?: keyof ApiErrors,
    _method?: string,
): ApiErrorShape {
    if (isRecord(error)) {
        return error as ApiErrorShape
    }

    if (error instanceof Error) {
        return { message: error.message }
    }

    if (typeof error === 'string') {
        return { message: error }
    }

    return { message: '未知错误' }
}

// 导出全局变量`api`
export const api = new Api(async ({ uri, method, headers, body }) => {
    const tenant = window.__tenant
    const isFormData = body instanceof FormData
    const fetchHeaders: HeadersInit = {
        ...headers,
        ...(tenant !== undefined && tenant !== '' ? { tenant } : {}),
    }
    if (!isFormData) {
        // 仅在非FormData时设置content-type，携带二进制文件时，浏览器会自动设置content-type
        fetchHeaders['content-type'] = 'application/json;charset=UTF-8'
    }
    const response = await fetch(`${BASE_URL}${uri}`, {
        method,
        headers: fetchHeaders,
        ...(method !== 'GET' ? { body: isFormData ? body : JSON.stringify(body) } : {}),
    })

    // 401处理：排除登录接口，避免循环
    if (response.status === 401 && !isLoginRequest(uri, method)) {
        handleAuthExpiry()
    }

    if (Math.floor(response.status / 100) === 5) {
        const text = await response.text()
        console.error('服务器错误:', response.status, uri, text)
        throw new Error('请求失败：' + text)
    }

    if (Math.floor(response.status / 100) !== 2) {
        throw await response.json()
    }

    const text = await response.text()
    if (text.length === 0) {
        return null
    }
    return JSON.parse(text)
})
