import { Api, type ApiErrors } from './__generated'
import { buildApiUrl } from '@/runtime/platform'
const AUTH_EXPIRED_MESSAGE = '登录已过期，请重新登录'
const TOKEN_STORAGE_KEY = 'unirhy.auth-token'
const TOKEN_HEADER_NAME = 'unirhy-token'

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
    window.localStorage.removeItem(TOKEN_STORAGE_KEY)
    document.cookie = `${TOKEN_HEADER_NAME}=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT`
}

export const saveAuthToken = (token: string) => {
    window.localStorage.setItem(TOKEN_STORAGE_KEY, token)
}

export const getAuthToken = (): string | null => {
    return window.localStorage.getItem(TOKEN_STORAGE_KEY)
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

    // TODO: 这里有点怀疑，需要验证tauri下是不是必要的
    const token = getAuthToken()
    if (token) {
        fetchHeaders[TOKEN_HEADER_NAME] = token
    }

    if (!isFormData) {
        // 仅在非FormData时设置content-type，携带二进制文件时，浏览器会自动设置content-type
        fetchHeaders['content-type'] = 'application/json;charset=UTF-8'
    }
    const response = await fetch(buildApiUrl(uri), {
        method,
        credentials: 'include',
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
