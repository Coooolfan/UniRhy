<script setup lang="ts">
import { Play, Pause, Plus, Trash2, Pencil } from 'lucide-vue-next'

defineProps<{
    title: string
    isPlaying: boolean
    label?: string
    cover?: string
    subtitle?: string
    isDefault?: boolean
    showAddButton?: boolean
    showRemoveButton?: boolean
    showEditButton?: boolean
    isRemoving?: boolean
}>()

const emit = defineEmits<{
    (e: 'play'): void
    (e: 'add'): void
    (e: 'remove'): void
    (e: 'edit'): void
}>()
</script>

<template>
    <!-- Cover (Optional) -->
    <div
        v-if="cover"
        class="hidden h-10 w-10 shrink-0 overflow-hidden rounded-sm bg-[#D6D1C7] shadow-sm sm:block"
    >
        <img :src="cover" class="w-full h-full object-cover" />
    </div>

    <!-- Main Content -->
    <div class="flex-1 min-w-0">
        <div class="flex flex-wrap items-center gap-2">
            <!-- Title -->
            <div class="truncate text-base font-medium text-[#4A433B]">
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
        <div v-if="label" class="mt-1 text-xs text-[#B0AAA0] truncate lg:hidden">
            {{ label }}
        </div>
    </div>

    <!-- Label -->
    <div v-if="label" class="hidden lg:block text-xs text-[#B0AAA0] max-w-[200px] truncate ml-4">
        {{ label }}
    </div>

    <!-- Play Button (Hover) -->
    <div
        class="mr-1 flex shrink-0 items-center gap-3 text-[#8C857B] transition-opacity md:mr-4 md:gap-4 md:opacity-0 md:group-hover:opacity-100"
    >
        <button
            v-if="showRemoveButton"
            class="hover:text-[#B95D5D] transition-colors cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
            :title="isRemoving ? '移除中' : '从歌单移除'"
            :disabled="isRemoving"
            @click.stop="emit('remove')"
        >
            <Trash2 :size="16" />
        </button>
        <button
            v-if="showAddButton"
            class="hover:text-[#C17D46] transition-colors cursor-pointer"
            title="添加到歌单"
            @click.stop="emit('add')"
        >
            <Plus :size="16" />
        </button>
        <button
            v-if="showEditButton"
            class="hover:text-[#C17D46] transition-colors cursor-pointer"
            title="关于曲目"
            @click.stop="emit('edit')"
        >
            <Pencil :size="16" />
        </button>
        <button
            class="hover:text-[#C17D46] transition-colors cursor-pointer"
            @click.stop="emit('play')"
        >
            <Pause v-if="isPlaying" :size="16" />
            <Play v-else :size="16" />
        </button>
    </div>
</template>
