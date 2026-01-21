<script setup lang="ts">
import { Search } from 'lucide-vue-next'
</script>

<template>
    <div class="flex justify-between items-center mb-10 px-2">
        <div class="flex items-center space-x-2 border-b border-[#D6D1C7] pb-1 w-64">
            <Search :size="16" class="text-[#9C968B]" />
            <input
                type="text"
                placeholder="搜索艺术家、作品..."
                class="bg-transparent border-none outline-none text-sm w-full placeholder-[#9C968B] text-[#4A4A4A] focus:ring-0"
            />
        </div>
        <div
            class="w-8 h-8 rounded-full bg-[#D6D1C7] overflow-hidden cursor-pointer opacity-80 hover:opacity-100 transition-opacity ring-2 ring-white ring-offset-1 ring-offset-[#EBE7E0]"
        >
            <img
                src="https://picsum.photos/seed/user/100/100"
                alt="User"
                class="w-full h-full object-cover"
            />
        </div>
    </div>
</template>
