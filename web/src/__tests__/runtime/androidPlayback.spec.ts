import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
    getAndroidPlaybackSystemStatus,
    openAndroidBatterySettings,
    startAndroidPlaybackService,
    stopAndroidPlaybackService,
} from '@/runtime/androidPlayback'

const pluginMocks = vi.hoisted(() => ({
    isServiceRunning: vi.fn<() => Promise<boolean>>(),
    startService: vi.fn(),
    stopService: vi.fn(),
    configureRecovery: vi.fn(),
    isPermissionGranted: vi.fn<() => Promise<boolean>>(),
    checkBatteryOptimizationStatus: vi.fn(),
    openBatterySettings: vi.fn(),
}))

vi.mock('tauri-plugin-background-service', () => ({
    isServiceRunning: pluginMocks.isServiceRunning,
    startService: pluginMocks.startService,
    stopService: pluginMocks.stopService,
    configureRecovery: pluginMocks.configureRecovery,
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
        pluginMocks.configureRecovery.mockReset().mockResolvedValue(undefined)
        pluginMocks.isPermissionGranted.mockReset().mockResolvedValue(true)
        pluginMocks.checkBatteryOptimizationStatus.mockReset().mockResolvedValue({
            isOptimized: false,
            isIgnoringOptimizations: true,
        })
        pluginMocks.openBatterySettings.mockReset().mockResolvedValue(undefined)
    })

    it('starts a media playback service and enables recovery', async () => {
        await startAndroidPlaybackService()

        const expectedConfig = {
            serviceLabel: '音乐播放进行中',
            foregroundServiceType: 'mediaPlayback',
        }
        expect(pluginMocks.startService).toHaveBeenCalledWith(expectedConfig)
        expect(pluginMocks.configureRecovery).toHaveBeenCalledWith({
            enabled: true,
            config: expectedConfig,
        })
    })

    it('stops a running service and disables recovery', async () => {
        pluginMocks.isServiceRunning.mockResolvedValue(true)

        await stopAndroidPlaybackService()

        expect(pluginMocks.stopService).toHaveBeenCalledTimes(1)
        expect(pluginMocks.configureRecovery).toHaveBeenCalledWith({ enabled: false })
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
