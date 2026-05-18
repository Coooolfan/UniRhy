<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Menu, Search } from 'lucide-vue-next'
import { useModal } from '@/composables/useModal'
import { useUserStore } from '@/stores/user'
import avatarPlaceholderUrl from '@/assets/avatar-placeholder.svg'
import { useDashboardLayout } from '@/composables/useDashboardLayout'
import { getPlatformRuntime } from '@/runtime/platform'
import ProfileModal from './ProfileModal.vue'

type Props = {
    modelValue?: string
    placeholder?: string
}

const props = defineProps<Props>()
const emit = defineEmits<{
    (event: 'update:modelValue', value: string): void
}>()

const router = useRouter()
const modal = useModal()
const userStore = useUserStore()
const { toggleSidebar } = useDashboardLayout()
const inputValue = ref(props.modelValue ?? '')
const isWindowsDesktop = getPlatformRuntime().platform === 'windows'

onMounted(() => {
    if (!userStore.user) {
        userStore.fetchUser()
    }
})

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

const openProfileModal = async () => {
    await modal.open(ProfileModal, {
        title: '账号信息',
        size: 'sm',
    })
}

const minimizeWindow = async () => {
    const { getCurrentWindow } = await import('@tauri-apps/api/window')
    await getCurrentWindow().minimize()
}

const toggleMaximizeWindow = async () => {
    const { getCurrentWindow } = await import('@tauri-apps/api/window')
    await getCurrentWindow().toggleMaximize()
}

const closeWindow = async () => {
    const { getCurrentWindow } = await import('@tauri-apps/api/window')
    await getCurrentWindow().close()
}
</script>

<template>
    <header
        class="sticky top-0 z-20 flex items-center gap-3 bg-dashboard-main/90 px-4 pt-[max(1rem,env(safe-area-inset-top))] pb-4 backdrop-blur-sm transition-all duration-300 sm:px-6 md:justify-between lg:px-8"
    >
        <button
            type="button"
            class="inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-[#8C857B] transition-colors hover:bg-white/60 hover:text-[#5E5950] md:hidden"
            aria-label="切换侧边栏"
            @click="toggleSidebar"
        >
            <Menu :size="18" />
        </button>

        <div
            class="flex min-w-0 flex-1 items-center border-b border-[#DCD6CC] pb-1 text-[#8C857B] md:w-64 md:flex-none"
        >
            <Search :size="18" />
            <input
                type="text"
                :value="inputValue"
                :placeholder="placeholder ?? '搜索艺术家、作品...'"
                class="ml-2 w-full border-none bg-transparent text-sm outline-none placeholder-[#8C857B] focus:ring-0"
                @input="handleInput"
                @keydown.enter="handleSearch"
            />
        </div>
        <div
            class="flex shrink-0 items-center gap-1"
            :class="isWindowsDesktop ? 'fixed top-3 right-3 z-50' : ''"
        >
            <button
                type="button"
                class="h-8 w-8 shrink-0 cursor-pointer overflow-hidden rounded-full bg-[#DCD6CC]"
                aria-label="个人中心"
                @click="openProfileModal"
            >
                <img :src="avatarPlaceholderUrl" alt="avatar" class="h-full w-full object-cover" />
            </button>
            <div v-if="isWindowsDesktop" class="flex items-center">
                <button
                    type="button"
                    class="flex h-8 w-9 items-center justify-center text-[#5E5950] transition-colors hover:text-[#161412]"
                    aria-label="最小化窗口"
                    @click="minimizeWindow"
                >
                    <span class="h-px w-2.5 bg-current"></span>
                </button>
                <button
                    type="button"
                    class="flex h-8 w-9 items-center justify-center text-[#5E5950] transition-colors hover:text-[#161412]"
                    aria-label="最大化窗口"
                    @click="toggleMaximizeWindow"
                >
                    <span class="h-2.5 w-2.5 rounded-[2px] border border-current"></span>
                </button>
                <button
                    type="button"
                    class="relative flex h-8 w-9 items-center justify-center text-[#5E5950] transition-colors hover:text-[#C65143]"
                    aria-label="关闭窗口"
                    @click="closeWindow"
                >
                    <span class="absolute h-px w-3 rotate-45 bg-current"></span>
                    <span class="absolute h-px w-3 -rotate-45 bg-current"></span>
                </button>
            </div>
        </div>
    </header>
</template>
