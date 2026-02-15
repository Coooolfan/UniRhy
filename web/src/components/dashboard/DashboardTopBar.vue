<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Search } from 'lucide-vue-next'
import { useUserStore } from '@/stores/user'
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
            aria-label="个人中心"
            @click="openProfileModal"
        >
            <img
                src="https://picsum.photos/seed/user/100/100"
                alt="avatar"
                class="h-full w-full object-cover"
            />
        </button>
    </header>

    <ProfileModal :is-open="isProfileModalOpen" @close="closeProfileModal" />
</template>
