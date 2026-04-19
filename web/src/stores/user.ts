import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { api, clearAuthToken } from '@/ApiInstance'
import type { AccountDto } from '@/__generated/model/dto/AccountDto'
import type { AccountUpdate } from '@/__generated/model/static/AccountUpdate'

export const DEFAULT_PREFERRED_ASSET_FORMAT = 'audio/opus'

export const useUserStore = defineStore('user', () => {
    const user = ref<AccountDto['AccountController/DEFAULT_ACCOUNT_FETCHER'] | null>(null)
    let userRequestPromise: Promise<
        AccountDto['AccountController/DEFAULT_ACCOUNT_FETCHER'] | null
    > | null = null

    const preferredAssetFormat = computed(
        () => user.value?.preferences.preferredAssetFormat ?? DEFAULT_PREFERRED_ASSET_FORMAT,
    )

    const loadUser = async () => {
        try {
            const nextUser = await api.accountController.me()
            user.value = nextUser
            return nextUser
        } catch (error) {
            console.error('Failed to fetch user', error)
            user.value = null
            return null
        }
    }

    const fetchUser = () => {
        if (!userRequestPromise) {
            userRequestPromise = loadUser().finally(() => {
                userRequestPromise = null
            })
        }
        return userRequestPromise
    }

    const ensureUserLoaded = () => {
        if (user.value) {
            return user.value
        }
        return fetchUser()
    }

    const getPreferredAssetFormat = async () => {
        const loadedUser = user.value ?? (await ensureUserLoaded())
        return loadedUser?.preferences.preferredAssetFormat ?? DEFAULT_PREFERRED_ASSET_FORMAT
    }

    const updateUser = async (updateData: AccountUpdate) => {
        if (!user.value) return
        try {
            const updatedUser = await api.accountController.update({
                id: user.value.id,
                body: updateData,
            })
            user.value = updatedUser
        } catch (error) {
            console.error('Failed to update user', error)
            throw error
        }
    }

    const logout = async () => {
        try {
            await api.tokenController.logout()
        } catch (error) {
            console.error('Logout failed', error)
            // Continue with local cleanup even if API fails
        } finally {
            clearAuthToken()
            user.value = null
            userRequestPromise = null
            // We can't use router here easily if not inside setup,
            // but we can let the component handle navigation or return a promise.
            // For now, let's just clear state.
        }
    }

    const clearUser = () => {
        user.value = null
        userRequestPromise = null
    }

    return {
        user,
        preferredAssetFormat,
        fetchUser,
        ensureUserLoaded,
        getPreferredAssetFormat,
        updateUser,
        logout,
        clearUser,
    }
})
