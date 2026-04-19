<script setup lang="ts">
import { computed, ref, reactive, nextTick, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { LogOut, User, Mail, Lock, Edit2, Eye, EyeOff, SlidersHorizontal } from 'lucide-vue-next'
import { useModalContext } from '@/components/modals/modalContext'
import { useUserStore } from '@/stores/user'
import { normalizeApiError } from '@/ApiInstance'
import avatarPlaceholderUrl from '@/assets/avatar-placeholder.svg'

const router = useRouter()
const modal = useModalContext<undefined>()
const userStore = useUserStore()

type Mode = 'view' | 'edit' | 'logout_confirm'
const mode = ref<Mode>('view')
const showPassword = ref(false)
const isSubmitting = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
const form = reactive({
    name: '',
    email: '',
    password: '',
})
let successResetTimer: ReturnType<typeof setTimeout> | null = null

const contentWidthClass = computed(() => {
    switch (mode.value) {
        case 'edit':
            return 'w-[368px] max-w-full'
        case 'logout_confirm':
            return 'w-[320px] max-w-full'
        default:
            return 'w-[320px] max-w-full'
    }
})

const resetFormFromUser = () => {
    const user = userStore.user
    if (!user) {
        return
    }

    form.name = user.name
    form.email = user.email
    form.password = ''
}

resetFormFromUser()

const startEdit = () => {
    mode.value = 'edit'
    errorMessage.value = ''
    successMessage.value = ''
    resetFormFromUser()

    nextTick(() => {
        const input = document.querySelector('input[name="username"]')
        if (input instanceof HTMLInputElement) {
            input.focus()
        }
    })
}

const cancelEdit = () => {
    mode.value = 'view'
    errorMessage.value = ''
    showPassword.value = false
}

const togglePasswordVisibility = () => {
    showPassword.value = !showPassword.value
}

const handleUpdate = async () => {
    if (isSubmitting.value) return

    isSubmitting.value = true
    errorMessage.value = ''
    successMessage.value = ''

    try {
        await userStore.updateUser({
            name: form.name,
            email: form.email,
            password: form.password.trim() ? form.password : undefined,
        })
        successMessage.value = '个人信息更新成功'
        form.password = ''
        showPassword.value = false
        successResetTimer = setTimeout(() => {
            mode.value = 'view'
            successMessage.value = ''
        }, 1500)
    } catch (error) {
        const normalized = normalizeApiError(error)
        errorMessage.value = normalized.message ?? '更新失败'
    } finally {
        isSubmitting.value = false
    }
}

const openPreferences = () => {
    modal.resolve(undefined)
    router.push({ name: 'preferences' })
}

const requestLogout = () => {
    mode.value = 'logout_confirm'
}

const cancelLogout = () => {
    mode.value = 'view'
}

const confirmLogout = async () => {
    if (isSubmitting.value) return

    isSubmitting.value = true
    try {
        await userStore.logout()
        modal.resolve(undefined)
        router.push({ name: 'login' })
    } catch (error) {
        console.error('Logout failed', error)
        router.push({ name: 'login' })
    } finally {
        isSubmitting.value = false
    }
}

onUnmounted(() => {
    if (successResetTimer) {
        clearTimeout(successResetTimer)
    }
})
</script>

<template>
    <div class="inline-flex flex-col" :class="contentWidthClass">
        <div class="flex flex-1 flex-col">
            <div
                v-if="errorMessage"
                class="mb-4 rounded border border-[#ffe0e0] bg-[#fff5f5] p-3 text-sm text-[#B95D5D]"
            >
                {{ errorMessage }}
            </div>
            <div
                v-if="successMessage"
                class="mb-4 rounded border border-[#c6f6d5] bg-[#f0fff4] p-3 text-sm text-[#2f855a]"
            >
                {{ successMessage }}
            </div>

            <div v-if="mode === 'view'" class="flex flex-1 flex-col space-y-6">
                <div class="mb-6 flex flex-col items-center">
                    <div class="mb-4 h-20 w-20 overflow-hidden rounded-full bg-[#EAE6DE]">
                        <img
                            :src="avatarPlaceholderUrl"
                            alt="avatar"
                            class="h-full w-full object-cover"
                        />
                    </div>
                    <h2 class="text-2xl font-serif text-[#2B221B]">
                        {{ userStore.user?.name }}
                    </h2>
                    <p class="text-[#8C857B]">{{ userStore.user?.email }}</p>
                </div>

                <div class="space-y-3">
                    <button
                        @click="startEdit"
                        class="group flex w-full items-center justify-center gap-2 border border-[#D6D1C4] py-3 text-sm uppercase tracking-wide text-[#2B221B] transition-colors hover:bg-[#F7F5F0]"
                    >
                        <Edit2
                            :size="16"
                            class="text-[#8C857B] transition-colors group-hover:text-[#2B221B]"
                        />
                        编辑资料
                    </button>
                    <button
                        @click="openPreferences"
                        class="group flex w-full items-center justify-center gap-2 border border-[#D6D1C4] py-3 text-sm uppercase tracking-wide text-[#2B221B] transition-colors hover:bg-[#F7F5F0]"
                    >
                        <SlidersHorizontal
                            :size="16"
                            class="text-[#8C857B] transition-colors group-hover:text-[#2B221B]"
                        />
                        个人偏好
                    </button>
                </div>

                <div class="mt-auto flex justify-center border-t border-[#EAE6DE] pt-6">
                    <button
                        type="button"
                        @click="requestLogout"
                        class="flex items-center gap-2 rounded px-4 py-2 text-sm font-medium text-[#B95D5D] transition-colors hover:bg-[#FFF5F5]"
                    >
                        <LogOut :size="16" />
                        退出登录
                    </button>
                </div>
            </div>

            <form
                v-else-if="mode === 'edit'"
                @submit.prevent="handleUpdate"
                class="flex flex-1 flex-col space-y-5"
            >
                <div class="space-y-1">
                    <label class="ml-1 text-xs font-medium uppercase tracking-wider text-[#8C857B]">
                        用户名
                    </label>
                    <div class="group relative">
                        <div
                            class="absolute top-1/2 left-3 -translate-y-1/2 text-[#D6D1C4] transition-colors group-focus-within:text-[#D98C28]"
                        >
                            <User :size="18" />
                        </div>
                        <input
                            name="username"
                            v-model="form.name"
                            type="text"
                            required
                            class="w-full border border-[#D6D1C4] bg-white py-2.5 pr-4 pl-10 text-[#2C2825] outline-none transition-all placeholder-[#E0DCD6] focus:border-[#D98C28] focus:ring-1 focus:ring-[#D98C28]"
                            placeholder="请输入用户名"
                        />
                    </div>
                </div>

                <div class="space-y-1">
                    <label class="ml-1 text-xs font-medium uppercase tracking-wider text-[#8C857B]">
                        邮箱
                    </label>
                    <div class="group relative">
                        <div
                            class="absolute top-1/2 left-3 -translate-y-1/2 text-[#D6D1C4] transition-colors group-focus-within:text-[#D98C28]"
                        >
                            <Mail :size="18" />
                        </div>
                        <input
                            v-model="form.email"
                            type="email"
                            required
                            class="w-full border border-[#D6D1C4] bg-white py-2.5 pr-4 pl-10 text-[#2C2825] outline-none transition-all placeholder-[#E0DCD6] focus:border-[#D98C28] focus:ring-1 focus:ring-[#D98C28]"
                            placeholder="请输入邮箱"
                        />
                    </div>
                </div>

                <div class="space-y-1">
                    <label class="ml-1 text-xs font-medium uppercase tracking-wider text-[#8C857B]">
                        密码
                        <span class="normal-case tracking-normal text-[#D6D1C4]">
                            (留空保持不变)
                        </span>
                    </label>
                    <div class="group relative">
                        <div
                            class="absolute top-1/2 left-3 -translate-y-1/2 text-[#D6D1C4] transition-colors group-focus-within:text-[#D98C28]"
                        >
                            <Lock :size="18" />
                        </div>
                        <input
                            v-model="form.password"
                            :type="showPassword ? 'text' : 'password'"
                            class="w-full border border-[#D6D1C4] bg-white py-2.5 pr-10 pl-10 text-[#2C2825] outline-none transition-all placeholder-[#E0DCD6] focus:border-[#D98C28] focus:ring-1 focus:ring-[#D98C28]"
                            placeholder="设置新密码"
                        />
                        <button
                            type="button"
                            @click="togglePasswordVisibility"
                            class="absolute top-1/2 right-3 -translate-y-1/2 text-[#D6D1C4] transition-colors hover:text-[#8C857B]"
                            tabindex="-1"
                        >
                            <Eye v-if="!showPassword" :size="18" />
                            <EyeOff v-else :size="18" />
                        </button>
                    </div>
                </div>

                <div class="mt-auto flex gap-3 pt-6">
                    <button
                        type="button"
                        @click="cancelEdit"
                        class="flex-1 border border-[#D6D1C4] py-2.5 text-sm uppercase tracking-wide text-[#8A8A8A] transition-colors hover:bg-[#F7F5F0] hover:text-[#5A5A5A]"
                        :disabled="isSubmitting"
                    >
                        取消
                    </button>
                    <button
                        type="submit"
                        class="flex-1 bg-[#2B221B] py-2.5 text-sm uppercase tracking-wide text-[#F7F5F0] shadow-md transition-colors hover:bg-[#3E3228] disabled:cursor-not-allowed disabled:opacity-60"
                        :disabled="isSubmitting"
                    >
                        {{ isSubmitting ? '保存中...' : '保存更改' }}
                    </button>
                </div>
            </form>

            <div
                v-else-if="mode === 'logout_confirm'"
                class="flex flex-1 flex-col justify-center text-center"
            >
                <div class="mb-6">
                    <div
                        class="mb-4 inline-flex h-16 w-16 items-center justify-center rounded-full border border-[#FFE0E0] bg-[#FFF5F5]"
                    >
                        <LogOut :size="28" class="text-[#B95D5D]" />
                    </div>
                    <h3 class="mb-2 font-serif text-2xl text-[#2B221B]">确认退出？</h3>
                </div>

                <div class="mt-6 flex gap-3">
                    <button
                        type="button"
                        @click="cancelLogout"
                        class="flex-1 border border-[#D6D1C4] py-2.5 text-sm uppercase tracking-wide text-[#8A8A8A] transition-colors hover:bg-[#F7F5F0] hover:text-[#5A5A5A]"
                        :disabled="isSubmitting"
                    >
                        取消
                    </button>
                    <button
                        type="button"
                        @click="confirmLogout"
                        class="flex-1 bg-[#B95D5D] py-2.5 text-sm uppercase tracking-wide text-[#FFF] shadow-md transition-colors hover:bg-[#A04545] disabled:cursor-not-allowed disabled:opacity-60"
                        :disabled="isSubmitting"
                    >
                        {{ isSubmitting ? '退出中...' : '确认退出' }}
                    </button>
                </div>
            </div>
        </div>
    </div>
</template>
