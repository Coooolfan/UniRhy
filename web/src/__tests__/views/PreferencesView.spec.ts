import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { nextTick } from 'vue'
import { PLAYBACK_MODE_STORAGE_KEY, useClientPreferencesStore } from '@/stores/clientPreferences'
import { useUserStore } from '@/stores/user'
import PreferencesView from '@/views/PreferencesView.vue'

const androidPlaybackMocks = vi.hoisted(() => ({
    isAndroid: false,
    getSystemStatus: vi.fn(),
    requestNotificationPermission: vi.fn(),
    openBatterySettings: vi.fn(),
}))

vi.mock('@/runtime/androidPlayback', () => ({
    isAndroidRuntime: () => androidPlaybackMocks.isAndroid,
    getAndroidPlaybackSystemStatus: androidPlaybackMocks.getSystemStatus,
    requestAndroidNotificationPermission: androidPlaybackMocks.requestNotificationPermission,
    openAndroidBatterySettings: androidPlaybackMocks.openBatterySettings,
}))

vi.mock('@/components/dashboard/DashboardTopBar.vue', () => ({
    default: {
        template: '<div data-test="dashboard-top-bar" />',
    },
}))

const setUser = () => {
    const userStore = useUserStore()
    userStore.user = {
        id: 1,
        name: 'Tester',
        email: 'tester@example.com',
        admin: false,
        preferences: {
            preferredAssetFormat: 'audio/opus',
        },
    }
}

describe('PreferencesView', () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        window.localStorage.clear()
        androidPlaybackMocks.isAndroid = false
        androidPlaybackMocks.getSystemStatus.mockReset().mockResolvedValue({
            notificationPermissionGranted: false,
            batteryOptimizationEnabled: true,
        })
        androidPlaybackMocks.requestNotificationPermission.mockReset().mockResolvedValue('granted')
        androidPlaybackMocks.openBatterySettings.mockReset().mockResolvedValue(undefined)
        setUser()
    })

    it('persists playback mode locally without updating account preferences', async () => {
        const userStore = useUserStore()
        const updateUserSpy = vi.spyOn(userStore, 'updateUser')

        const wrapper = mount(PreferencesView)
        await wrapper.get('[data-test="playback-mode-select"]').setValue('SYNC')

        expect(window.localStorage.getItem(PLAYBACK_MODE_STORAGE_KEY)).toBe('SYNC')
        expect(useClientPreferencesStore().playbackMode).toBe('SYNC')
        expect(updateUserSpy).not.toHaveBeenCalled()
    })

    it('shows Android playback status and opens the relevant system settings', async () => {
        androidPlaybackMocks.isAndroid = true
        androidPlaybackMocks.getSystemStatus
            .mockResolvedValueOnce({
                notificationPermissionGranted: false,
                batteryOptimizationEnabled: true,
            })
            .mockResolvedValue({
                notificationPermissionGranted: true,
                batteryOptimizationEnabled: false,
            })

        const wrapper = mount(PreferencesView)
        await nextTick()
        await Promise.resolve()
        await nextTick()

        expect(wrapper.get('[data-test="notification-status"]').text()).toBe('未允许')
        expect(wrapper.get('[data-test="battery-status"]').text()).toBe('系统优化中')

        await wrapper.get('[data-test="request-notification-permission"]').trigger('click')
        await Promise.resolve()
        await nextTick()

        expect(androidPlaybackMocks.requestNotificationPermission).toHaveBeenCalledTimes(1)
        expect(wrapper.get('[data-test="notification-status"]').text()).toBe('已允许')

        await wrapper.get('[data-test="open-battery-settings"]').trigger('click')
        expect(androidPlaybackMocks.openBatterySettings).toHaveBeenCalledTimes(1)
    })

    it('falls back to independent mode when local playback mode is invalid', () => {
        window.localStorage.setItem(PLAYBACK_MODE_STORAGE_KEY, 'UNKNOWN')

        const wrapper = mount(PreferencesView)

        expect(useClientPreferencesStore().playbackMode).toBe('INDEPENDENT')
        const select = wrapper.get<HTMLSelectElement>('[data-test="playback-mode-select"]')
        expect(select.element.value).toBe('INDEPENDENT')
    })
})
