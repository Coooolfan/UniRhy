<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Search, LogOut } from 'lucide-vue-next'
import { api, normalizeApiError } from '@/ApiInstance'

type Props = {
    modelValue?: string
    placeholder?: string
}

const props = defineProps<Props>()
const emit = defineEmits<{
    (event: 'update:modelValue', value: string): void
}>()

const router = useRouter()
const inputValue = ref(props.modelValue ?? '')
const isLogoutModalOpen = ref(false)
const isLoggingOut = ref(false)
const logoutError = ref('')

watch(
    () => props.modelValue,
    (value) => {
        if (value !== undefined) {
            inputValue.value = value
        }
    },
)

const handleInput = (event: Event) => {
    const value = (event.target as HTMLInputElement).value
    inputValue.value = value
    emit('update:modelValue', value)
}

const handleSearch = () => {
    if (inputValue.value.trim()) {
        router.push({
            name: 'search',
            query: { q: inputValue.value.trim() },
        })
    }
}

const openLogoutModal = () => {
    if (isLoggingOut.value) {
        return
    }
    logoutError.value = ''
    isLogoutModalOpen.value = true
}

const closeLogoutModal = () => {
    if (isLoggingOut.value) {
        return
    }
    isLogoutModalOpen.value = false
    logoutError.value = ''
}

const confirmLogout = async () => {
    if (isLoggingOut.value) {
        return
    }

    isLoggingOut.value = true
    logoutError.value = ''
    try {
        await api.tokenController.logout()
        document.cookie = 'token=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT'
        isLogoutModalOpen.value = false
        await router.replace({ name: 'login' })
    } catch (error) {
        const normalized = normalizeApiError(error)
        logoutError.value = normalized.message ?? '退出登录失败'
    } finally {
        isLoggingOut.value = false
    }
}
</script>

<template>
    <header
        class="sticky top-0 z-20 px-8 py-6 flex justify-between items-center bg-[#f2efe9]/90 backdrop-blur-sm transition-all duration-300"
    >
        <div class="flex items-center text-[#8C857B] border-b border-[#DCD6CC] pb-1 w-64">
            <Search :size="18" />
            <input
                type="text"
                :value="inputValue"
                :placeholder="placeholder ?? '搜索艺术家、作品...'"
                class="bg-transparent border-none outline-none ml-2 text-sm placeholder-[#8C857B] w-full focus:ring-0"
                @input="handleInput"
                @keydown.enter="handleSearch"
            />
        </div>
        <button
            type="button"
            class="h-8 w-8 rounded-full bg-[#DCD6CC] overflow-hidden cursor-pointer"
            aria-label="退出登录"
            @click="openLogoutModal"
        >
            <img
                src="https://picsum.photos/seed/user/100/100"
                alt="avatar"
                class="h-full w-full object-cover"
            />
        </button>
    </header>

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
                v-if="isLogoutModalOpen"
                class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-[#2B221B]/60"
                @click.self="closeLogoutModal"
            >
                <div
                    class="bg-[#fffcf5] p-8 w-full max-w-md shadow-[0_8px_30px_rgba(0,0,0,0.12)] border border-[#EAE6DE] relative transform transition-all"
                >
                    <div
                        class="absolute top-0 right-0 w-16 h-16 bg-linear-to-bl from-[#EAE6DE]/30 to-transparent pointer-events-none"
                    ></div>

                    <div class="mb-6 text-center">
                        <div
                            class="inline-flex items-center justify-center w-12 h-12 rounded-full bg-[#FAF9F6] mb-4 border border-[#EAE6DE]"
                        >
                            <LogOut :size="22" class="text-[#B95D5D]" />
                        </div>
                        <h3 class="font-serif text-2xl text-[#2B221B]">退出登录</h3>
                        <p class="text-sm text-[#8C857B] mt-3">确认退出当前账号？</p>
                    </div>

                    <p v-if="logoutError" class="text-sm text-[#B95D5D] mb-4">
                        {{ logoutError }}
                    </p>

                    <div class="flex gap-3 pt-4 border-t border-[#EAE6DE]">
                        <button
                            type="button"
                            class="flex-1 px-4 py-2.5 border border-[#D6D1C4] text-[#8A8A8A] hover:bg-[#F7F5F0] hover:text-[#5A5A5A] transition-colors text-sm uppercase tracking-wide disabled:opacity-60 disabled:cursor-not-allowed"
                            :disabled="isLoggingOut"
                            @click="closeLogoutModal"
                        >
                            取消
                        </button>
                        <button
                            type="button"
                            class="flex-1 px-4 py-2.5 bg-[#2B221B] text-[#F7F5F0] hover:bg-[#B95D5D] transition-colors text-sm uppercase tracking-wide shadow-md disabled:opacity-60 disabled:cursor-not-allowed"
                            :disabled="isLoggingOut"
                            @click="confirmLogout"
                        >
                            {{ isLoggingOut ? '退出中...' : '确认退出' }}
                        </button>
                    </div>
                </div>
            </div>
        </Transition>
    </Teleport>
</template>
