<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useModalContext } from '@/components/modals/modalContext'
import type { AccountForm } from '@/composables/useAccountSettings'

const { t } = useI18n()

type SubmitAccountForm = (payload: AccountForm) => Promise<string | null>

const props = withDefaults(
    defineProps<{
        mode?: 'create' | 'update'
        initialName?: string
        initialEmail?: string
        submitText?: string
        submit: SubmitAccountForm
    }>(),
    {
        mode: 'create',
        initialName: '',
        initialEmail: '',
        submitText: '',
    },
)

const modal = useModalContext<undefined>()
const isSubmitting = ref(false)
const submitError = ref('')

const form = reactive<AccountForm>({
    name: props.initialName,
    email: props.initialEmail,
    password: '',
})

const resolvedSubmitText = () => {
    if (props.submitText) return props.submitText
    return props.mode === 'create' ? t('accountForm.createTitle') : t('accountForm.saveTitle')
}

const handleCancel = () => {
    if (isSubmitting.value) {
        return
    }
    modal.close()
}

const handleSubmit = async () => {
    if (isSubmitting.value) {
        return
    }
    isSubmitting.value = true
    submitError.value = ''
    try {
        const error = await props.submit({
            name: form.name,
            email: form.email,
            password: form.password,
        })
        if (error) {
            submitError.value = error
            return
        }
        modal.resolve(undefined)
    } finally {
        isSubmitting.value = false
    }
}
</script>

<template>
    <div class="space-y-6">
        <p class="font-serif text-xs italic text-[#8A8A8A]">
            <span v-if="mode === 'create'">{{ t('accountForm.createHint') }}</span>
            <span v-else>{{ t('accountForm.editHint') }}</span>
        </p>

        <div class="space-y-6">
            <div>
                <label
                    class="mb-2 block font-serif text-xs uppercase tracking-wider text-[#8A8A8A]"
                >
                    {{ t('accountForm.name') }}
                </label>
                <input
                    v-model="form.name"
                    data-testid="account-form-name"
                    type="text"
                    :placeholder="t('accountForm.namePlaceholder')"
                    autocomplete="off"
                    class="w-full border-b border-[#D6D1C4] bg-[#F7F5F0] p-3 font-serif text-[#3D3D3D] transition-colors placeholder:text-[#BDB9AE] focus:border-[#C67C4E] focus:outline-none"
                />
            </div>

            <div>
                <label
                    class="mb-2 block font-serif text-xs uppercase tracking-wider text-[#8A8A8A]"
                >
                    {{ t('accountForm.email') }}
                </label>
                <input
                    v-model="form.email"
                    data-testid="account-form-email"
                    type="email"
                    placeholder="user@example.com"
                    autocomplete="off"
                    class="w-full border-b border-[#D6D1C4] bg-[#F7F5F0] p-3 font-serif text-[#3D3D3D] transition-colors placeholder:text-[#BDB9AE] focus:border-[#C67C4E] focus:outline-none"
                />
            </div>

            <div>
                <label
                    class="mb-2 block font-serif text-xs uppercase tracking-wider text-[#8A8A8A]"
                >
                    {{
                        mode === 'create'
                            ? t('accountForm.initialPassword')
                            : t('accountForm.newPassword')
                    }}
                </label>
                <input
                    v-model="form.password"
                    data-testid="account-form-password"
                    type="password"
                    :placeholder="
                        mode === 'create'
                            ? t('accountForm.passwordPlaceholderCreate')
                            : t('accountForm.passwordPlaceholderEdit')
                    "
                    autocomplete="new-password"
                    class="w-full border-b border-[#D6D1C4] bg-[#F7F5F0] p-3 font-serif text-[#3D3D3D] transition-colors placeholder:text-[#BDB9AE] focus:border-[#C67C4E] focus:outline-none"
                />
            </div>

            <p v-if="submitError" data-testid="account-form-error" class="text-sm text-[#B95D5D]">
                {{ submitError }}
            </p>

            <div class="mt-8 flex gap-3 border-t border-[#EAE6DE] pt-6">
                <button
                    type="button"
                    data-testid="account-form-cancel"
                    class="flex-1 border border-[#D6D1C4] px-4 py-2.5 text-sm uppercase tracking-wide text-[#8A8A8A] transition-colors hover:bg-[#F7F5F0] hover:text-[#5A5A5A]"
                    :disabled="isSubmitting"
                    @click="handleCancel"
                >
                    {{ t('common.cancel') }}
                </button>
                <button
                    type="button"
                    data-testid="account-form-submit"
                    class="flex-1 bg-[#2B221B] px-4 py-2.5 text-sm uppercase tracking-wide text-[#F7F5F0] shadow-md transition-colors hover:bg-[#C67C4E] disabled:cursor-not-allowed disabled:opacity-60"
                    :disabled="isSubmitting"
                    @click="handleSubmit"
                >
                    <span v-if="isSubmitting">
                        {{
                            mode === 'create' ? t('accountForm.creating') : t('accountForm.saving')
                        }}
                    </span>
                    <span v-else>{{ resolvedSubmitText() }}</span>
                </button>
            </div>
        </div>
    </div>
</template>
