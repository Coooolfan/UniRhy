<script setup lang="ts">
import { Play, Pause } from 'lucide-vue-next'

defineProps<{
    title: string
    isActive: boolean
    isPlaying: boolean
    label?: string
    cover?: string
    subtitle?: string
    isDefault?: boolean
}>()

const emit = defineEmits<{
    (e: 'play'): void
}>()
</script>

<template>
    <!-- Cover (Optional) -->
    <div
        v-if="cover"
        class="w-10 h-10 shrink-0 bg-[#D6D1C7] rounded-sm overflow-hidden shadow-sm hidden md:block"
    >
        <img :src="cover" class="w-full h-full object-cover" />
    </div>

    <!-- Main Content -->
    <div class="flex-1 min-w-0">
        <div class="flex items-center gap-2">
            <!-- Title -->
            <div
                class="text-base font-medium truncate"
                :class="isActive ? 'text-[#C17D46]' : 'text-[#4A433B]'"
            >
                {{ title }}
            </div>
            <!-- Default Badge (Optional) -->
            <span
                v-if="isDefault"
                class="px-1.5 py-0.5 bg-[#EFEAE2] text-[#8C857B] text-[10px] uppercase tracking-wider rounded-xs"
                >Default</span
            >
        </div>
        <!-- Subtitle (Optional) -->
        <div v-if="subtitle" class="text-sm text-[#8C857B] truncate">
            {{ subtitle }}
        </div>
    </div>

    <!-- Label -->
    <div v-if="label" class="hidden lg:block text-xs text-[#B0AAA0] max-w-[200px] truncate ml-4">
        {{ label }}
    </div>

    <!-- Play Button (Hover) -->
    <div
        class="hidden md:flex opacity-0 group-hover:opacity-100 transition-opacity gap-4 mr-4 text-[#8C857B]"
    >
        <button
            class="hover:text-[#C17D46] transition-colors cursor-pointer"
            @click.stop="emit('play')"
        >
            <Pause v-if="isPlaying" :size="16" />
            <Play v-else :size="16" />
        </button>
    </div>
</template>
