import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
    getAndroidPlaybackSystemStatus,
    openAndroidBatterySettings,
} from '@/runtime/androidPlayback'

const pluginMocks = vi.hoisted(() => ({
    isPermissionGranted: vi.fn<() => Promise<boolean>>(),
    checkBatteryOptimizationStatus: vi.fn(),
    openBatterySettings: vi.fn(),
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
        pluginMocks.isPermissionGranted.mockReset().mockResolvedValue(true)
        pluginMocks.checkBatteryOptimizationStatus.mockReset().mockResolvedValue({
            isOptimized: false,
            isIgnoringOptimizations: true,
        })
        pluginMocks.openBatterySettings.mockReset().mockResolvedValue(undefined)
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
