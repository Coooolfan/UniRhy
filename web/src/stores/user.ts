import { defineStore } from 'pinia'
import { ref } from 'vue'
import { api } from '@/ApiInstance'
import type { AccountDto } from '@/__generated/model/dto/AccountDto'
import type { AccountUpdate } from '@/__generated/model/static/AccountUpdate'

export const useUserStore = defineStore('user', () => {
    const user = ref<AccountDto['AccountController/DEFAULT_ACCOUNT_FETCHER'] | null>(null)

    const fetchUser = async () => {
        try {
            user.value = await api.accountController.me()
        } catch (error) {
            console.error('Failed to fetch user', error)
            user.value = null
        }
    }

    const updateUser = async (updateData: AccountUpdate) => {
        if (!user.value) return
        try {
            const updatedUser = await api.accountController.update({
                id: user.value.id,
                body: updateData,
            })
            user.value = updatedUser
            return updatedUser
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
            document.cookie = 'token=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT'
            user.value = null
            // We can't use router here easily if not inside setup,
            // but we can let the component handle navigation or return a promise.
            // For now, let's just clear state.
        }
    }

    const clearUser = () => {
        user.value = null
    }

    return {
        user,
        fetchUser,
        updateUser,
        logout,
        clearUser,
    }
})
