import { contextBridge, ipcRenderer } from 'electron'

import {
    ELECTRON_BRIDGE_CHANNELS,
    type ElectronRuntimeBridge,
} from '../src/runtime/electron.shared'
import type { InjectedPlatformRuntime } from '../src/runtime/platform.shared'

const resolvePlatform = (): InjectedPlatformRuntime['platform'] => {
    if (process.platform === 'darwin') {
        return 'macos'
    }

    if (process.platform === 'win32') {
        return 'windows'
    }

    return 'linux'
}

contextBridge.exposeInMainWorld('__UNIRHY_RUNTIME__', {
    apiBaseUrl: '',
    platform: resolvePlatform(),
} satisfies InjectedPlatformRuntime)

contextBridge.exposeInMainWorld('__UNIRHY_ELECTRON__', {
    getBackendUrl: () => ipcRenderer.invoke(ELECTRON_BRIDGE_CHANNELS.getBackendUrl),
    setBackendUrl: (backendUrl: string) =>
        ipcRenderer.invoke(ELECTRON_BRIDGE_CHANNELS.setBackendUrl, backendUrl),
} satisfies ElectronRuntimeBridge)
