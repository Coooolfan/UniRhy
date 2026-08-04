export const PLATFORM_KINDS = ['web', 'macos', 'windows', 'linux', 'android', 'ios'] as const
export type PlatformKind = (typeof PLATFORM_KINDS)[number]
export type InjectedPlatformRuntime = { apiBaseUrl?: string; platform?: PlatformKind }
export type PlatformRuntime = { apiBaseUrl: string; platform: PlatformKind }

/** 具备摄像头扫码能力的平台。 */
export const MOBILE_PLATFORM_KINDS = ['android', 'ios'] as const satisfies readonly PlatformKind[]
export type MobilePlatformKind = (typeof MOBILE_PLATFORM_KINDS)[number]

export const isMobilePlatform = (platform: PlatformKind): platform is MobilePlatformKind =>
    MOBILE_PLATFORM_KINDS.some((kind) => kind === platform)
