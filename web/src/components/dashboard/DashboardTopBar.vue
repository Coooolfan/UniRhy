<script setup lang="ts">
import { ref, watch } from 'vue'
import { Search } from 'lucide-vue-next'

type Props = {
    modelValue?: string
    placeholder?: string
}

const props = defineProps<Props>()
const emit = defineEmits<{
    (event: 'update:modelValue', value: string): void
}>()

const inputValue = ref(props.modelValue ?? '')

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
</script>

<template>
    <header
        class="sticky top-0 z-20 px-8 py-6 flex justify-between items-center bg-[#EBE7E0]/90 backdrop-blur-sm transition-all duration-300"
    >
        <div class="flex items-center text-[#8C857B] border-b border-[#DCD6CC] pb-1 w-64">
            <Search :size="18" />
            <input
                type="text"
                :value="inputValue"
                :placeholder="placeholder ?? '搜索艺术家、作品...'"
                class="bg-transparent border-none outline-none ml-2 text-sm placeholder-[#8C857B] w-full focus:ring-0"
                @input="handleInput"
            />
        </div>
        <div class="h-8 w-8 rounded-full bg-[#DCD6CC] overflow-hidden cursor-pointer">
            <img
                src="https://picsum.photos/seed/user/100/100"
                alt="avatar"
                class="h-full w-full object-cover"
            />
        </div>
    </header>
</template>
