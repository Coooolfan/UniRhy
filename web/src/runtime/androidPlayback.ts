import { getPlatformRuntime } from '@/runtime/platform'

export type AndroidPlaybackSystemStatus = {
    notificationPermissionGranted: boolean
    batteryOptimizationEnabled: boolean
}

type AndroidPlaybackNativeLifecycleEvent = {
    type: 'androidNotificationStop' | 'androidTimeout'
    fgsType?: string
}

const createPlaybackServiceConfig = () => ({
    serviceLabel: '音乐播放进行中',
    foregroundServiceType: 'mediaPlayback',
})

export const isAndroidRuntime = () => getPlatformRuntime().platform === 'android'

export const getAndroidNotificationPermission = async () => {
    const { isPermissionGranted } = await import('@tauri-apps/plugin-notification')
    return isPermissionGranted()
}

export const requestAndroidNotificationPermission = async () => {
    const { requestPermission } = await import('@tauri-apps/plugin-notification')
    return requestPermission()
}

export const isAndroidPlaybackServiceRunning = async () => {
    const { isServiceRunning } = await import('tauri-plugin-background-service')
    return isServiceRunning()
}

export const startAndroidPlaybackService = async () => {
    const { isServiceRunning, startService } = await import('tauri-plugin-background-service')
    const config = createPlaybackServiceConfig()

    if (!(await isServiceRunning())) {
        await startService(config)
    }
}

export const stopAndroidPlaybackService = async () => {
    const { isServiceRunning, stopService } = await import('tauri-plugin-background-service')

    if (await isServiceRunning()) {
        await stopService()
    }
}

export const listenForAndroidPlaybackStop = async (onStop: () => void) => {
    const { addPluginListener, invoke } = await import('@tauri-apps/api/core')
    const listener = await addPluginListener<AndroidPlaybackNativeLifecycleEvent>(
        'background-service',
        'native-lifecycle-event',
        (event) => {
            if (event.type !== 'androidNotificationStop') {
                return
            }

            onStop()
            void invoke('plugin:background-service|native_lifecycle_event', { event }).catch(
                (error: unknown) => {
                    console.error('Failed to reconcile native Android playback stop', error)
                },
            )
        },
    )

    return () => {
        void listener.unregister()
    }
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
