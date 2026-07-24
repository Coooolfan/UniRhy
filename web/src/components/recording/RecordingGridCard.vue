<script setup lang="ts">
import { Pause, Play } from 'lucide-vue-next'

withDefaults(
    defineProps<{
        title: string
        subtitle: string
        cover?: string
        label?: string
        isPlaying?: boolean
    }>(),
    {
        cover: '',
        label: '',
        isPlaying: false,
    },
)

const emit = defineEmits<{
    (e: 'play'): void
}>()
</script>

<template>
    <div class="group cursor-pointer" @click="emit('play')">
        <div class="relative mb-4 aspect-square">
            <div
                class="h-full w-full overflow-hidden rounded-sm bg-[#D6D1C7] shadow-md transition-all duration-300 group-hover:scale-[1.03] group-hover:shadow-lg"
            >
                <img v-if="cover" :src="cover" :alt="title" class="h-full w-full object-cover" />
                <div
                    v-else
                    class="flex h-full w-full items-center justify-center text-xs text-[#8C857B]"
                >
                    No Cover
                </div>
            </div>

            <button
                class="absolute bottom-3 right-3 z-20 flex h-10 w-10 items-center justify-center rounded-full bg-white/90 text-[#2C2420] opacity-100 shadow-lg transition-all duration-300 hover:scale-110 sm:opacity-0 sm:group-hover:opacity-100"
                @click.stop="emit('play')"
            >
                <Pause v-if="isPlaying" :size="16" fill="currentColor" />
                <Play v-else :size="16" fill="currentColor" class="ml-0.5" />
            </button>
        </div>

        <div class="text-center md:text-left">
            <h3
                class="mb-1 truncate font-serif text-lg leading-tight text-[#1A1A1A] transition-colors group-hover:text-[#C27E46]"
            >
                {{ title }}
            </h3>
            <p class="truncate text-xs uppercase tracking-wider text-[#8C857B]">
                {{ subtitle }}
            </p>
            <p v-if="label" class="mt-1 truncate text-xs text-[#B0AAA0]">
                {{ label }}
            </p>
        </div>
    </div>
</template>
