import { ref } from 'vue'
import { api } from '@/ApiInstance'
import { resolveErrorMessage } from '@/i18n/errors'
import type { AccountDto } from '@/__generated/model/dto/AccountDto'

export type Account = AccountDto['AccountController/DEFAULT_ACCOUNT_FETCHER']

export type AccountForm = {
    name: string
    email: string
    password: string
}

type AccountFormValidationResult =
    | { error: string }
    | {
          payload: {
              name: string
              email: string
              password: string
          }
      }

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/u

const validateAccountForm = (
    form: AccountForm,
    options: { mode: 'create' | 'update' },
): AccountFormValidationResult => {
    const name = form.name.trim()
    const email = form.email.trim()
    const password = form.password

    if (!name) {
        return { error: '请填写账号名称' }
    }
    if (!email) {
        return { error: '请填写邮箱' }
    }
    if (!EMAIL_PATTERN.test(email)) {
        return { error: '邮箱格式不正确' }
    }
    if (options.mode === 'create' && !password) {
        return { error: '请填写初始密码' }
    }

    return { payload: { name, email, password } }
}

export const useAccountSettings = () => {
    const accounts = ref<Account[]>([])
    const isLoading = ref(false)
    const isSaving = ref(false)
    const error = ref('')

    const fetchAccounts = async () => {
        isLoading.value = true
        error.value = ''
        try {
            const list = await api.accountController.list()
            accounts.value = [...list]
        } catch (e) {
            error.value = resolveErrorMessage(e, 'errors.fallback.accountList')
        } finally {
            isLoading.value = false
        }
    }

    const createAccount = async (form: AccountForm): Promise<string | null> => {
        const validated = validateAccountForm(form, { mode: 'create' })
        if ('error' in validated) {
            return validated.error
        }
        if (isSaving.value) {
            return '已有保存操作正在执行'
        }

        isSaving.value = true
        try {
            await api.accountController.create({
                body: {
                    name: validated.payload.name,
                    email: validated.payload.email,
                    password: validated.payload.password,
                },
            })
            await fetchAccounts()
            return null
        } catch (e) {
            return resolveErrorMessage(e, 'common.createFailed')
        } finally {
            isSaving.value = false
        }
    }

    const updateAccount = async (
        id: number,
        form: AccountForm,
        currentEmail: string,
    ): Promise<string | null> => {
        const validated = validateAccountForm(form, { mode: 'update' })
        if ('error' in validated) {
            return validated.error
        }
        if (isSaving.value) {
            return '已有保存操作正在执行'
        }

        const emailChanged = validated.payload.email !== currentEmail
        const passwordChanged = validated.payload.password.length > 0

        isSaving.value = true
        try {
            await api.accountController.update({
                id,
                body: { name: validated.payload.name },
            })
            // 邮箱与密码属于登录凭据，走独立端点；管理员重置他人凭据无需当前密码
            if (emailChanged || passwordChanged) {
                await api.accountController.updateCredentials({
                    id,
                    body: {
                        ...(emailChanged ? { email: validated.payload.email } : {}),
                        ...(passwordChanged ? { password: validated.payload.password } : {}),
                    },
                })
            }
            await fetchAccounts()
            return null
        } catch (e) {
            return resolveErrorMessage(e, 'common.updateFailed')
        } finally {
            isSaving.value = false
        }
    }

    const deleteAccount = async (account: Account): Promise<string | null> => {
        if (isSaving.value) {
            return '已有保存操作正在执行'
        }
        isSaving.value = true
        try {
            await api.accountController.delete({ id: account.id })
            await fetchAccounts()
            return null
        } catch (e) {
            return resolveErrorMessage(e, 'common.deleteFailed')
        } finally {
            isSaving.value = false
        }
    }

    return {
        accounts,
        isLoading,
        isSaving,
        error,
        fetchAccounts,
        createAccount,
        updateAccount,
        deleteAccount,
    }
}
