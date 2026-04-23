import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { PLAYBACK_MODE_STORAGE_KEY, useClientPreferencesStore } from '@/stores/clientPreferences'
import { useUserStore } from '@/stores/user'
import PreferencesView from '@/views/PreferencesView.vue'

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
        setUser()
    })

    it('persists playback mode locally without updating account preferences', async () => {
        const userStore = useUserStore()
        const updateUserSpy = vi.spyOn(userStore, 'updateUser')

        const wrapper = mount(PreferencesView)
        await wrapper.get('[data-test="playback-mode-select"]').setValue('INDEPENDENT')

        expect(window.localStorage.getItem(PLAYBACK_MODE_STORAGE_KEY)).toBe('INDEPENDENT')
        expect(useClientPreferencesStore().playbackMode).toBe('INDEPENDENT')
        expect(updateUserSpy).not.toHaveBeenCalled()
    })

    it('falls back to sync mode when local playback mode is invalid', () => {
        window.localStorage.setItem(PLAYBACK_MODE_STORAGE_KEY, 'UNKNOWN')

        const wrapper = mount(PreferencesView)

        expect(useClientPreferencesStore().playbackMode).toBe('SYNC')
        const select = wrapper.get<HTMLSelectElement>('[data-test="playback-mode-select"]')
        expect(select.element.value).toBe('SYNC')
    })
})
