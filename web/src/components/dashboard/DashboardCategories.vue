<script setup lang="ts">
import { categories } from './data'
</script>

<template>
    <div
        class="relative z-10 flex space-x-8 mb-10 overflow-x-auto px-2 py-4 no-scrollbar items-center"
    >
        <span
            v-for="(cat, idx) in categories"
            :key="idx"
            class="text-base text-[#8A857D] hover:text-[#C27E46] cursor-pointer whitespace-nowrap transition-colors border-b-2 border-transparent hover:border-[#C27E46] pb-1 leading-relaxed"
        >
            {{ cat }}
        </span>
    </div>
</template>
