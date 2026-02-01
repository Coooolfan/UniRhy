<script setup lang="ts">
import { Search } from 'lucide-vue-next'
</script>

<template>
    <header
        class="sticky top-0 z-20 px-8 py-6 flex justify-between items-center bg-[#EBE7E0]/90 backdrop-blur-sm transition-all duration-300"
    >
        <div class="flex items-center text-[#8C857B] border-b border-[#DCD6CC] pb-1 w-64">
            <Search :size="18" />
            <input
                type="text"
                placeholder="搜索艺术家、作品..."
                class="bg-transparent border-none outline-none ml-2 text-sm placeholder-[#8C857B] w-full focus:ring-0"
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
