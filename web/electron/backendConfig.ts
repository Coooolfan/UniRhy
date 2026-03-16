import fs from 'node:fs/promises'
import path from 'node:path'

const CONFIG_FILE_NAME = 'desktop-runtime.json'
const SUPPORTED_PROTOCOLS = new Set(['http:', 'https:'])

type PersistedDesktopRuntime = {
    backendUrl?: string
}

const isRecord = (value: unknown): value is Record<string, unknown> => {
    return typeof value === 'object' && value !== null
}

const stripTrailingSlash = (value: string) => {
    return value.endsWith('/') ? value.slice(0, -1) : value
}

export const normalizeBackendUrl = (value: string) => {
    const trimmed = value.trim()
    if (trimmed === '') {
        throw new Error('后端端点不能为空')
    }

    let parsedUrl: URL
    try {
        parsedUrl = new URL(trimmed)
    } catch {
        throw new Error('后端端点不是有效的 URL')
    }

    if (!SUPPORTED_PROTOCOLS.has(parsedUrl.protocol)) {
        throw new Error('后端端点必须以 http:// 或 https:// 开头')
    }

    return stripTrailingSlash(parsedUrl.toString())
}

const isMissingFileError = (error: unknown) => {
    return (
        typeof error === 'object' &&
        error !== null &&
        'code' in error &&
        typeof error.code === 'string' &&
        error.code === 'ENOENT'
    )
}

export const getBackendConfigFilePath = (userDataPath: string) => {
    return path.join(userDataPath, CONFIG_FILE_NAME)
}

export const loadPersistedBackendUrl = async (userDataPath: string) => {
    const configFilePath = getBackendConfigFilePath(userDataPath)

    try {
        const raw = await fs.readFile(configFilePath, 'utf8')
        const parsed: unknown = JSON.parse(raw)

        if (!isRecord(parsed) || typeof parsed.backendUrl !== 'string') {
            return undefined
        }

        return normalizeBackendUrl(parsed.backendUrl)
    } catch (error) {
        if (isMissingFileError(error)) {
            return undefined
        }

        return undefined
    }
}

export const persistBackendUrl = async (userDataPath: string, backendUrl: string) => {
    const normalizedBackendUrl = normalizeBackendUrl(backendUrl)
    const configFilePath = getBackendConfigFilePath(userDataPath)

    await fs.mkdir(path.dirname(configFilePath), { recursive: true })
    await fs.writeFile(
        configFilePath,
        JSON.stringify(
            {
                backendUrl: normalizedBackendUrl,
            } satisfies PersistedDesktopRuntime,
            null,
            2,
        ),
        'utf8',
    )

    return normalizedBackendUrl
}
