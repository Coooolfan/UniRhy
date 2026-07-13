<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { Loader2, Plus, UserPlus } from 'lucide-vue-next'
import { useModal } from '@/composables/useModal'
import AccountCard from '@/components/settings/AccountCard.vue'
import AccountFormDialogContent from '@/components/settings/AccountFormDialogContent.vue'
import type { Account, AccountForm } from '@/composables/useAccountSettings'

type Props = {
    accounts: ReadonlyArray<Account>
    currentAccountId: number | null
    isLoading: boolean
    isSaving: boolean
    error: string
    createAccount: (form: AccountForm) => Promise<string | null>
    updateAccount: (id: number, form: AccountForm, currentEmail: string) => Promise<string | null>
    deleteAccount: (account: Account) => Promise<string | null>
    canManage?: boolean
}

const props = defineProps<Props>()
const modal = useModal()
const { t } = useI18n()

const openCreateAccountModal = async () => {
    if (props.isSaving) {
        return
    }
    await modal.open(AccountFormDialogContent, {
        title: t('account.addAccount'),
        size: 'md',
        closable: false,
        closeOnBackdrop: false,
        closeOnEscape: false,
        props: {
            mode: 'create',
            submit: props.createAccount,
        },
    })
}

const openEditAccountModal = async (account: Account) => {
    if (props.isSaving) {
        return
    }
    await modal.open(AccountFormDialogContent, {
        title: t('account.editAccountTitle'),
        size: 'md',
        closable: false,
        closeOnBackdrop: false,
        closeOnEscape: false,
        props: {
            mode: 'update',
            initialName: account.name,
            initialEmail: account.email,
            submit: (form: AccountForm) => props.updateAccount(account.id, form, account.email),
        },
    })
}

const confirmDeleteAccount = async (account: Account) => {
    if (props.isSaving) {
        return
    }
    const confirmed = await modal.confirm({
        title: t('account.deleteAccount'),
        content: t('account.deleteConfirm', { name: account.name }),
        confirmText: t('common.confirmDelete'),
        cancelText: t('common.cancel'),
        tone: 'danger',
    })
    if (!confirmed) {
        return
    }
    const error = await props.deleteAccount(account)
    if (!error) {
        return
    }
    await modal.alert({
        title: t('common.deleteFailedTitle'),
        content: error,
        confirmText: t('common.confirm'),
        tone: 'danger',
    })
}
</script>

<template>
    <section class="animate-in fade-in font-serif duration-500">
        <div
            class="mb-4 flex items-center justify-between gap-3 border-b border-[#E0Dcd0] pb-2 sm:mb-6"
        >
            <h2 class="font-serif text-2xl tracking-wide text-[#4A3B32]">
                {{ t('account.title') }}
            </h2>
            <button
                v-if="canManage"
                class="group flex w-auto shrink-0 items-center justify-center gap-2 bg-[#C67C4E] px-3 py-2 text-sm text-[#F7F5F0] shadow-md transition-all duration-300 hover:bg-[#A6633C] hover:shadow-lg disabled:cursor-not-allowed disabled:opacity-50 sm:px-6 sm:text-base"
                :disabled="isSaving"
                @click="openCreateAccountModal"
            >
                <Plus :size="16" />
                <span>{{ t('account.addAccount') }}</span>
            </button>
        </div>

        <div
            v-if="error"
            class="mb-4 border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700"
        >
            {{ error }}
        </div>

        <p class="mb-4 text-xs italic text-[#8A8A8A]">
            {{ t('account.description') }}
        </p>

        <div v-if="isLoading" class="flex items-center justify-center py-10 text-sm text-[#6B635B]">
            <Loader2 class="mr-2 h-4 w-4 animate-spin text-[#C27E46]" />
            {{ t('account.loading') }}
        </div>

        <div
            v-else-if="accounts.length === 0"
            class="flex flex-col items-center justify-center border border-dashed border-[#D6D1C4] py-12 text-center"
        >
            <UserPlus class="h-10 w-10 text-[#C27E46]" />
            <p class="mt-4 text-sm text-[#6B635B]">{{ t('account.empty') }}</p>
            <p v-if="canManage" class="mt-1 text-xs text-[#9C968B]">{{ t('account.emptyHint') }}</p>
        </div>

        <div v-else class="grid grid-cols-1 items-start gap-6 md:grid-cols-2">
            <AccountCard
                v-for="account in accounts"
                :key="account.id"
                :account="account"
                :is-saving="isSaving"
                :is-current="account.id === currentAccountId"
                :can-manage="canManage"
                @edit="openEditAccountModal(account)"
                @delete="confirmDeleteAccount(account)"
            />
        </div>
    </section>
</template>
