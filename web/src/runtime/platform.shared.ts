export const PLATFORM_KINDS = ['web', 'macos', 'windows', 'linux', 'android', 'ios'] as const
export type PlatformKind = (typeof PLATFORM_KINDS)[number]
export type InjectedPlatformRuntime = { apiBaseUrl?: string; platform?: PlatformKind }
export type PlatformRuntime = { apiBaseUrl: string; platform: PlatformKind }
