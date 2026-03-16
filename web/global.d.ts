import type { InjectedPlatformRuntime } from './src/runtime/platform.shared'

declare global {
    interface Window {
        __tenant?: string
        __UNIRHY_RUNTIME__?: InjectedPlatformRuntime
    }
}
