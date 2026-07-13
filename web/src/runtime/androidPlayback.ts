import { getPlatformRuntime } from '@/runtime/platform'

export type AndroidPlaybackSystemStatus = {
    notificationPermissionGranted: boolean
    batteryOptimizationEnabled: boolean
}

export const isAndroidRuntime = () => getPlatformRuntime().platform === 'android'

export const getAndroidNotificationPermission = async () => {
    const { isPermissionGranted } = await import('@tauri-apps/plugin-notification')
    return isPermissionGranted()
}

export const requestAndroidNotificationPermission = async () => {
    const { requestPermission } = await import('@tauri-apps/plugin-notification')
    return requestPermission()
}

export const getAndroidPlaybackSystemStatus = async (): Promise<AndroidPlaybackSystemStatus> => {
    const [{ isPermissionGranted }, { checkBatteryOptimizationStatus }] = await Promise.all([
        import('@tauri-apps/plugin-notification'),
        import('tauri-plugin-android-battery-optimization-api'),
    ])
    const [notificationPermissionGranted, batteryStatus] = await Promise.all([
        isPermissionGranted(),
        checkBatteryOptimizationStatus(),
    ])

    return {
        notificationPermissionGranted,
        batteryOptimizationEnabled: batteryStatus.isOptimized,
    }
}

export const openAndroidBatterySettings = async () => {
    const { openBatterySettings } = await import('tauri-plugin-android-battery-optimization-api')
    await openBatterySettings()
}
