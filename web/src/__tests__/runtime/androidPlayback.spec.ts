import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
    getAndroidPlaybackSystemStatus,
    listenForAndroidPlaybackStop,
    openAndroidBatterySettings,
    startAndroidPlaybackService,
    stopAndroidPlaybackService,
} from '@/runtime/androidPlayback'

const pluginMocks = vi.hoisted(() => ({
    isServiceRunning: vi.fn<() => Promise<boolean>>(),
    startService: vi.fn(),
    stopService: vi.fn(),
    addPluginListener: vi.fn(),
    invoke: vi.fn(),
    unregister: vi.fn(),
    nativeLifecycleHandler: undefined as
        | ((event: { type: string; fgsType?: string }) => void)
        | undefined,
    isPermissionGranted: vi.fn<() => Promise<boolean>>(),
    checkBatteryOptimizationStatus: vi.fn(),
    openBatterySettings: vi.fn(),
}))

vi.mock('tauri-plugin-background-service', () => ({
    isServiceRunning: pluginMocks.isServiceRunning,
    startService: pluginMocks.startService,
    stopService: pluginMocks.stopService,
}))

vi.mock('@tauri-apps/api/core', () => ({
    addPluginListener: pluginMocks.addPluginListener,
    invoke: pluginMocks.invoke,
}))

vi.mock('@tauri-apps/plugin-notification', () => ({
    isPermissionGranted: pluginMocks.isPermissionGranted,
}))

vi.mock('tauri-plugin-android-battery-optimization-api', () => ({
    checkBatteryOptimizationStatus: pluginMocks.checkBatteryOptimizationStatus,
    openBatterySettings: pluginMocks.openBatterySettings,
}))

describe('Android playback runtime', () => {
    beforeEach(() => {
        vi.restoreAllMocks()
        pluginMocks.isServiceRunning.mockReset().mockResolvedValue(false)
        pluginMocks.startService.mockReset().mockResolvedValue(undefined)
        pluginMocks.stopService.mockReset().mockResolvedValue(undefined)
        pluginMocks.nativeLifecycleHandler = undefined
        pluginMocks.unregister.mockReset()
        pluginMocks.addPluginListener
            .mockReset()
            .mockImplementation(
                (_plugin: string, _event: string, handler: (event: { type: string }) => void) => {
                    pluginMocks.nativeLifecycleHandler = handler
                    return Promise.resolve({ unregister: pluginMocks.unregister })
                },
            )
        pluginMocks.invoke.mockReset().mockResolvedValue(undefined)
        pluginMocks.isPermissionGranted.mockReset().mockResolvedValue(true)
        pluginMocks.checkBatteryOptimizationStatus.mockReset().mockResolvedValue({
            isOptimized: false,
            isIgnoringOptimizations: true,
        })
        pluginMocks.openBatterySettings.mockReset().mockResolvedValue(undefined)
    })

    it('starts a media playback service without enabling recovery', async () => {
        await startAndroidPlaybackService()

        const expectedConfig = {
            serviceLabel: '音乐播放进行中',
            foregroundServiceType: 'mediaPlayback',
        }
        expect(pluginMocks.startService).toHaveBeenCalledWith(expectedConfig)
    })

    it('stops a running service', async () => {
        pluginMocks.isServiceRunning.mockResolvedValue(true)

        await stopAndroidPlaybackService()

        expect(pluginMocks.stopService).toHaveBeenCalledTimes(1)
    })

    it('forwards native notification stops to playback and the Rust lifecycle manager', async () => {
        const onStop = vi.fn()
        const unlisten = await listenForAndroidPlaybackStop(() => {
            onStop()
        })

        expect(pluginMocks.addPluginListener).toHaveBeenCalledWith(
            'background-service',
            'native-lifecycle-event',
            expect.any(Function),
        )

        pluginMocks.nativeLifecycleHandler?.({ type: 'androidTimeout', fgsType: 'mediaPlayback' })
        expect(onStop).not.toHaveBeenCalled()

        pluginMocks.nativeLifecycleHandler?.({ type: 'androidNotificationStop' })
        expect(onStop).toHaveBeenCalledTimes(1)
        expect(pluginMocks.invoke).toHaveBeenCalledWith(
            'plugin:background-service|native_lifecycle_event',
            { event: { type: 'androidNotificationStop' } },
        )

        unlisten()
        expect(pluginMocks.unregister).toHaveBeenCalledTimes(1)
    })

    it('combines notification and battery status and opens battery settings', async () => {
        pluginMocks.isPermissionGranted.mockResolvedValue(false)
        pluginMocks.checkBatteryOptimizationStatus.mockResolvedValue({
            isOptimized: true,
            isIgnoringOptimizations: false,
        })

        await expect(getAndroidPlaybackSystemStatus()).resolves.toEqual({
            notificationPermissionGranted: false,
            batteryOptimizationEnabled: true,
        })

        await openAndroidBatterySettings()
        expect(pluginMocks.openBatterySettings).toHaveBeenCalledTimes(1)
    })
})
