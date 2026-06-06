<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useModalContext } from '@/components/modals/modalContext'
import type { AccountForm } from '@/composables/useAccountSettings'

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
    return props.mode === 'create' ? '创建账号' : '保存修改'
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
            <span v-if="mode === 'create'">新账号将以非管理员身份登入 UniRhy</span>
            <span v-else>留空密码字段表示保持当前密码不变</span>
        </p>

        <div class="space-y-6">
            <div>
                <label
                    class="mb-2 block font-serif text-xs uppercase tracking-wider text-[#8A8A8A]"
                >
                    账号名称
                </label>
                <input
                    v-model="form.name"
                    data-testid="account-form-name"
                    type="text"
                    placeholder="例如：listener"
                    autocomplete="off"
                    class="w-full border-b border-[#D6D1C4] bg-[#F7F5F0] p-3 font-serif text-[#3D3D3D] transition-colors placeholder:text-[#BDB9AE] focus:border-[#C67C4E] focus:outline-none"
                />
            </div>

            <div>
                <label
                    class="mb-2 block font-serif text-xs uppercase tracking-wider text-[#8A8A8A]"
                >
                    邮箱
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
                    {{ mode === 'create' ? '初始密码' : '新密码（可选）' }}
                </label>
                <input
                    v-model="form.password"
                    data-testid="account-form-password"
                    type="password"
                    :placeholder="mode === 'create' ? '请输入密码' : '不修改请留空'"
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
                    取消
                </button>
                <button
                    type="button"
                    data-testid="account-form-submit"
                    class="flex-1 bg-[#2B221B] px-4 py-2.5 text-sm uppercase tracking-wide text-[#F7F5F0] shadow-md transition-colors hover:bg-[#C67C4E] disabled:cursor-not-allowed disabled:opacity-60"
                    :disabled="isSubmitting"
                    @click="handleSubmit"
                >
                    <span v-if="isSubmitting">
                        {{ mode === 'create' ? '正在创建...' : '正在保存...' }}
                    </span>
                    <span v-else>{{ resolvedSubmitText() }}</span>
                </button>
            </div>
        </div>
    </div>
</template>
