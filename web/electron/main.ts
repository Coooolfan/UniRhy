import { app, BrowserWindow, ipcMain } from 'electron'
import path from 'node:path'

import { loadPersistedBackendUrl, normalizeBackendUrl, persistBackendUrl } from './backendConfig'
import { startProxyServer, type StartedProxyServer } from './proxy'
import { ELECTRON_BRIDGE_CHANNELS } from '../src/runtime/electron.shared'

const DEFAULT_BACKEND_URL = 'http://127.0.0.1:8654'
const DEFAULT_DEV_SERVER_URL = 'http://localhost:5173'
const DEV_SERVER_WAIT_TIMEOUT_MS = 30_000
const DEV_SERVER_RETRY_INTERVAL_MS = 250

let proxyServer: StartedProxyServer | undefined
let currentBackendUrl = DEFAULT_BACKEND_URL

const isDevelopment = !app.isPackaged

const resolveFallbackBackendUrl = () => {
    const configuredUrl = process.env.UNIRHY_BACKEND_URL?.trim()
    const backendUrl =
        configuredUrl === undefined || configuredUrl === '' ? DEFAULT_BACKEND_URL : configuredUrl

    return normalizeBackendUrl(backendUrl)
}

const resolveDevServerUrl = () => {
    const configuredUrl = process.env.UNIRHY_ELECTRON_DEV_SERVER_URL?.trim()
    return configuredUrl === undefined || configuredUrl === ''
        ? DEFAULT_DEV_SERVER_URL
        : configuredUrl
}

const sleep = (milliseconds: number) => {
    return new Promise<void>((resolve) => {
        setTimeout(resolve, milliseconds)
    })
}

const waitForServer = async (url: string) => {
    const deadline = Date.now() + DEV_SERVER_WAIT_TIMEOUT_MS

    while (Date.now() < deadline) {
        try {
            const response = await fetch(url, {
                signal: AbortSignal.timeout(2_000),
            })

            if (response.ok) {
                return
            }
        } catch {
            // Retry until the dev server is ready or the timeout expires.
        }

        await sleep(DEV_SERVER_RETRY_INTERVAL_MS)
    }

    throw new Error(`Timed out while waiting for development server at ${url}`)
}

const registerBackendUrlHandlers = () => {
    ipcMain.handle(ELECTRON_BRIDGE_CHANNELS.getBackendUrl, () => {
        return currentBackendUrl
    })

    ipcMain.handle(
        ELECTRON_BRIDGE_CHANNELS.setBackendUrl,
        async (_event, nextBackendUrl: string) => {
            const persistedBackendUrl = await persistBackendUrl(
                app.getPath('userData'),
                nextBackendUrl,
            )
            currentBackendUrl = persistedBackendUrl
            return currentBackendUrl
        },
    )
}

const createMainWindow = async () => {
    if (proxyServer === undefined) {
        throw new Error('Proxy server is not ready')
    }

    const preloadPath = path.join(app.getAppPath(), 'dist-electron', 'preload.cjs')
    const mainWindow = new BrowserWindow({
        width: 1440,
        height: 900,
        minWidth: 1024,
        minHeight: 720,
        webPreferences: {
            contextIsolation: true,
            nodeIntegration: false,
            preload: preloadPath,
            sandbox: false,
        },
    })

    await mainWindow.loadURL(proxyServer.url)
}

const bootstrap = async () => {
    await app.whenReady()

    currentBackendUrl =
        (await loadPersistedBackendUrl(app.getPath('userData'))) ?? resolveFallbackBackendUrl()
    const devServerUrl = resolveDevServerUrl()
    registerBackendUrlHandlers()

    if (isDevelopment) {
        await waitForServer(devServerUrl)
    }

    proxyServer = await startProxyServer({
        devServerUrl: isDevelopment ? devServerUrl : undefined,
        getBackendUrl: () => currentBackendUrl,
        staticDir: isDevelopment ? undefined : path.join(app.getAppPath(), 'dist'),
    })

    await createMainWindow()

    app.on('activate', () => {
        if (BrowserWindow.getAllWindows().length === 0) {
            void createMainWindow()
        }
    })
}

app.on('window-all-closed', () => {
    if (process.platform !== 'darwin') {
        app.quit()
    }
})

app.on('before-quit', () => {
    void proxyServer?.close()
})

// oxlint-disable-next-line unicorn/prefer-top-level-await
void bootstrap().catch((error: unknown) => {
    console.error('Failed to start UniRhy desktop application', error)
    app.quit()
})
