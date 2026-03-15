<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Menu, Search } from 'lucide-vue-next'
import { useUserStore } from '@/stores/user'
import avatarPlaceholderUrl from '@/assets/avatar-placeholder.svg'
import { useDashboardLayout } from '@/composables/useDashboardLayout'
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
const userStore = useUserStore()
const { toggleSidebar } = useDashboardLayout()
const inputValue = ref(props.modelValue ?? '')
const isProfileModalOpen = ref(false)

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

const openProfileModal = () => {
    isProfileModalOpen.value = true
}

const closeProfileModal = () => {
    isProfileModalOpen.value = false
}
</script>

<template>
    <header
        class="sticky top-0 z-20 flex items-center gap-3 bg-dashboard-main/90 px-4 py-4 backdrop-blur-sm transition-all duration-300 sm:px-6 lg:px-8"
    >
        <button
            type="button"
            class="inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-[#8C857B] transition-colors hover:bg-white/60 hover:text-[#5E5950]"
            aria-label="切换侧边栏"
            @click="toggleSidebar"
        >
            <Menu :size="18" />
        </button>

        <div
            class="flex min-w-0 flex-1 items-center border-b border-[#DCD6CC] pb-1 text-[#8C857B] md:max-w-80 lg:max-w-md"
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
        <button
            type="button"
            class="h-8 w-8 shrink-0 cursor-pointer overflow-hidden rounded-full bg-[#DCD6CC]"
            aria-label="个人中心"
            @click="openProfileModal"
        >
            <img :src="avatarPlaceholderUrl" alt="avatar" class="h-full w-full object-cover" />
        </button>
    </header>

    <ProfileModal :is-open="isProfileModalOpen" @close="closeProfileModal" />
</template>
