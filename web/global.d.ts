import type { ElectronRuntimeBridge } from './src/runtime/electron.shared'
import type { InjectedPlatformRuntime } from './src/runtime/platform.shared'

declare global {
    interface Window {
        __UNIRHY_ELECTRON__?: ElectronRuntimeBridge
        __tenant?: string
        __UNIRHY_RUNTIME__?: InjectedPlatformRuntime
    }
}
