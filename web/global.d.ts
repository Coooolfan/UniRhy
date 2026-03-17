import type { InjectedPlatformRuntime } from './src/runtime/platform.shared'

declare global {
    interface Window {
        __UNIRHY_RUNTIME__?: InjectedPlatformRuntime
        __tenant?: string
    }
}
