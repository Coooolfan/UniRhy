<script setup lang="ts">
import { ref, reactive, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { LogOut, X, User, Mail, Lock, Edit2, Eye, EyeOff } from 'lucide-vue-next'
import { useUserStore } from '@/stores/user'
import { normalizeApiError } from '@/ApiInstance'

const props = defineProps<{
    isOpen: boolean
}>()

const emit = defineEmits<{
    (event: 'close'): void
}>()

const router = useRouter()
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

// Initialize form with user data when modal opens or user data changes
watch([() => props.isOpen, () => userStore.user], ([isOpen, user]) => {
    if (isOpen) {
        mode.value = 'view'
        errorMessage.value = ''
        successMessage.value = ''
        showPassword.value = false

        if (user) {
            form.name = user.name
            form.email = user.email
            form.password = ''
        }
    }
})

const handleClose = () => {
    if (isSubmitting.value) return
    emit('close')
}

const startEdit = () => {
    mode.value = 'edit'
    errorMessage.value = ''
    successMessage.value = ''
    // Reset form to current user data
    if (userStore.user) {
        form.name = userStore.user.name
        form.email = userStore.user.email
        form.password = ''
    }
    // Focus first input
    nextTick(() => {
        const input = document.querySelector('input[name="username"]') as HTMLInputElement
        if (input) input.focus()
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
            // Only send password if it's not empty (checking trimmed value)
            password: form.password.trim() ? form.password : undefined,
        })
        successMessage.value = '个人信息更新成功'
        form.password = '' // Clear password after successful update
        showPassword.value = false
        // Return to view mode after short delay to show success message?
        // Or stay in edit mode? Usually view mode reflects updated data.
        setTimeout(() => {
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
        emit('close')
        router.push({ name: 'login' })
    } catch (error) {
        console.error('Logout failed', error)
        // Even if API fails, we should redirect to login (store handles cookie cleanup)
        router.push({ name: 'login' })
    } finally {
        isSubmitting.value = false
    }
}
</script>

<template>
    <Teleport to="body">
        <Transition
            enter-active-class="transition duration-200 ease-out"
            enter-from-class="opacity-0"
            enter-to-class="opacity-100"
            leave-active-class="transition duration-150 ease-in"
            leave-from-class="opacity-100"
            leave-to-class="opacity-0"
        >
            <div
                v-if="isOpen"
                class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-[#2B221B]/60"
                @click.self="handleClose"
            >
                <div
                    class="bg-[#fffcf5] w-full max-w-md shadow-[0_8px_30px_rgba(0,0,0,0.12)] border border-[#EAE6DE] relative transform transition-all overflow-hidden min-h-[400px] flex flex-col"
                >
                    <!-- Header -->
                    <div
                        class="px-8 py-6 border-b border-[#EAE6DE] flex justify-between items-center bg-[#FAF9F6]"
                    >
                        <h3 class="font-serif text-xl text-[#2B221B]">
                            {{
                                mode === 'edit'
                                    ? '编辑个人信息'
                                    : mode === 'logout_confirm'
                                      ? '退出确认'
                                      : '账号信息'
                            }}
                        </h3>
                        <button
                            @click="handleClose"
                            class="text-[#8C857B] hover:text-[#2B221B] transition-colors"
                            :disabled="isSubmitting"
                        >
                            <X :size="20" />
                        </button>
                    </div>

                    <!-- Content -->
                    <div class="p-8 flex-1 flex flex-col">
                        <!-- Messages -->
                        <div
                            v-if="errorMessage"
                            class="mb-4 p-3 bg-[#fff5f5] text-[#B95D5D] text-sm rounded border border-[#ffe0e0]"
                        >
                            {{ errorMessage }}
                        </div>
                        <div
                            v-if="successMessage"
                            class="mb-4 p-3 bg-[#f0fff4] text-[#2f855a] text-sm rounded border border-[#c6f6d5]"
                        >
                            {{ successMessage }}
                        </div>

                        <!-- View Mode -->
                        <div v-if="mode === 'view'" class="space-y-6 flex-1">
                            <div class="flex flex-col items-center mb-6">
                                <div
                                    class="w-20 h-20 rounded-full bg-[#EAE6DE] mb-4 overflow-hidden"
                                >
                                    <img
                                        src="https://picsum.photos/seed/user/200/200"
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
                                    class="w-full py-3 border border-[#D6D1C4] text-[#2B221B] hover:bg-[#F7F5F0] transition-colors text-sm uppercase tracking-wide flex items-center justify-center gap-2 group"
                                >
                                    <Edit2
                                        :size="16"
                                        class="text-[#8C857B] group-hover:text-[#2B221B] transition-colors"
                                    />
                                    编辑资料
                                </button>
                            </div>

                            <div class="mt-auto pt-6 border-t border-[#EAE6DE] flex justify-center">
                                <button
                                    type="button"
                                    @click="requestLogout"
                                    class="flex items-center gap-2 px-4 py-2 text-[#B95D5D] hover:bg-[#FFF5F5] transition-colors rounded text-sm font-medium"
                                >
                                    <LogOut :size="16" />
                                    退出登录
                                </button>
                            </div>
                        </div>

                        <!-- Edit Mode -->
                        <form
                            v-else-if="mode === 'edit'"
                            @submit.prevent="handleUpdate"
                            class="space-y-5 flex-1 flex flex-col"
                        >
                            <div class="space-y-1">
                                <label
                                    class="text-xs uppercase tracking-wider text-[#8C857B] font-medium ml-1"
                                    >用户名</label
                                >
                                <div class="relative group">
                                    <div
                                        class="absolute left-3 top-1/2 -translate-y-1/2 text-[#D6D1C4] group-focus-within:text-[#D98C28] transition-colors"
                                    >
                                        <User :size="18" />
                                    </div>
                                    <input
                                        name="username"
                                        v-model="form.name"
                                        type="text"
                                        required
                                        class="w-full bg-white border border-[#D6D1C4] pl-10 pr-4 py-2.5 text-[#2C2825] focus:border-[#D98C28] focus:ring-1 focus:ring-[#D98C28] outline-none transition-all placeholder-[#E0DCD6]"
                                        placeholder="请输入用户名"
                                    />
                                </div>
                            </div>

                            <div class="space-y-1">
                                <label
                                    class="text-xs uppercase tracking-wider text-[#8C857B] font-medium ml-1"
                                    >邮箱</label
                                >
                                <div class="relative group">
                                    <div
                                        class="absolute left-3 top-1/2 -translate-y-1/2 text-[#D6D1C4] group-focus-within:text-[#D98C28] transition-colors"
                                    >
                                        <Mail :size="18" />
                                    </div>
                                    <input
                                        v-model="form.email"
                                        type="email"
                                        required
                                        class="w-full bg-white border border-[#D6D1C4] pl-10 pr-4 py-2.5 text-[#2C2825] focus:border-[#D98C28] focus:ring-1 focus:ring-[#D98C28] outline-none transition-all placeholder-[#E0DCD6]"
                                        placeholder="请输入邮箱"
                                    />
                                </div>
                            </div>

                            <div class="space-y-1">
                                <label
                                    class="text-xs uppercase tracking-wider text-[#8C857B] font-medium ml-1"
                                    >密码
                                    <span class="text-[#D6D1C4] normal-case tracking-normal"
                                        >(留空保持不变)</span
                                    ></label
                                >
                                <div class="relative group">
                                    <div
                                        class="absolute left-3 top-1/2 -translate-y-1/2 text-[#D6D1C4] group-focus-within:text-[#D98C28] transition-colors"
                                    >
                                        <Lock :size="18" />
                                    </div>
                                    <input
                                        v-model="form.password"
                                        :type="showPassword ? 'text' : 'password'"
                                        class="w-full bg-white border border-[#D6D1C4] pl-10 pr-10 py-2.5 text-[#2C2825] focus:border-[#D98C28] focus:ring-1 focus:ring-[#D98C28] outline-none transition-all placeholder-[#E0DCD6]"
                                        placeholder="设置新密码"
                                    />
                                    <button
                                        type="button"
                                        @click="togglePasswordVisibility"
                                        class="absolute right-3 top-1/2 -translate-y-1/2 text-[#D6D1C4] hover:text-[#8C857B] transition-colors"
                                        tabindex="-1"
                                    >
                                        <Eye v-if="!showPassword" :size="18" />
                                        <EyeOff v-else :size="18" />
                                    </button>
                                </div>
                            </div>

                            <div class="pt-6 flex gap-3 mt-auto">
                                <button
                                    type="button"
                                    @click="cancelEdit"
                                    class="flex-1 py-2.5 border border-[#D6D1C4] text-[#8A8A8A] hover:bg-[#F7F5F0] hover:text-[#5A5A5A] transition-colors text-sm uppercase tracking-wide"
                                    :disabled="isSubmitting"
                                >
                                    取消
                                </button>
                                <button
                                    type="submit"
                                    class="flex-1 py-2.5 bg-[#2B221B] text-[#F7F5F0] hover:bg-[#3E3228] transition-colors text-sm uppercase tracking-wide shadow-md disabled:opacity-60 disabled:cursor-not-allowed"
                                    :disabled="isSubmitting"
                                >
                                    {{ isSubmitting ? '保存中...' : '保存更改' }}
                                </button>
                            </div>
                        </form>

                        <!-- Logout Confirm Mode -->
                        <div
                            v-else-if="mode === 'logout_confirm'"
                            class="flex-1 flex flex-col justify-center text-center"
                        >
                            <div class="mb-6">
                                <div
                                    class="inline-flex items-center justify-center w-16 h-16 rounded-full bg-[#FFF5F5] mb-4 border border-[#FFE0E0]"
                                >
                                    <LogOut :size="28" class="text-[#B95D5D]" />
                                </div>
                                <h3 class="font-serif text-2xl text-[#2B221B] mb-2">确认退出？</h3>
                            </div>

                            <div class="flex gap-3 mt-6">
                                <button
                                    type="button"
                                    @click="cancelLogout"
                                    class="flex-1 py-2.5 border border-[#D6D1C4] text-[#8A8A8A] hover:bg-[#F7F5F0] hover:text-[#5A5A5A] transition-colors text-sm uppercase tracking-wide"
                                    :disabled="isSubmitting"
                                >
                                    取消
                                </button>
                                <button
                                    type="button"
                                    @click="confirmLogout"
                                    class="flex-1 py-2.5 bg-[#B95D5D] text-[#FFF] hover:bg-[#A04545] transition-colors text-sm uppercase tracking-wide shadow-md disabled:opacity-60 disabled:cursor-not-allowed"
                                    :disabled="isSubmitting"
                                >
                                    {{ isSubmitting ? '退出中...' : '确认退出' }}
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </Transition>
    </Teleport>
</template>
