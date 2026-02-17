<script setup lang="ts">
import { Pause, Play } from 'lucide-vue-next'

const props = withDefaults(
    defineProps<{
        title: string
        subtitle: string
        details: string
        badge?: string
        cover?: string
        showPlayButton?: boolean
        playLoading?: boolean
        isPlaying?: boolean
    }>(),
    {
        badge: '',
        cover: '',
        showPlayButton: true,
        playLoading: false,
        isPlaying: false,
    },
)

const emit = defineEmits<{
    (e: 'open'): void
    (e: 'play'): void
}>()
</script>

<template>
    <div class="group cursor-pointer" @click="emit('open')">
        <div
            class="relative aspect-square mb-5 transition-transform duration-500 ease-out perspective-1000"
        >
            <div
                class="absolute top-1/2 left-1/2 w-[90%] h-[90%] -translate-x-1/2 -translate-y-1/2"
            >
                <div
                    class="w-full h-full bg-linear-to-tr from-gray-200 to-gray-100 border border-gray-300 rounded-full shadow-xl transition-all duration-700 ease-out opacity-0 group-hover:opacity-100 transform-gpu group-hover:translate-x-7 group-hover:-translate-y-8 group-hover:rotate-3 flex items-center justify-center relative"
                >
                    <div class="w-1/3 h-1/3 border border-gray-300 rounded-full opacity-50"></div>
                    <div
                        class="absolute w-8 h-8 bg-[#EBE7E0] rounded-full border border-gray-300"
                    ></div>
                </div>
            </div>
            <div
                class="relative z-10 w-full h-full shadow-lg transition-all duration-500 ease-out bg-[#D6D1C7] transform-gpu origin-center group-hover:scale-105 group-hover:-rotate-2 group-hover:-translate-x-3 group-hover:-translate-y-1.5"
            >
                <img v-if="cover" :src="cover" :alt="title" class="w-full h-full object-cover" />
                <div
                    v-else
                    class="w-full h-full flex items-center justify-center text-xs text-[#8C857B]"
                >
                    No Cover
                </div>
            </div>

            <button
                v-if="props.showPlayButton"
                class="absolute bottom-4 right-4 w-10 h-10 bg-white/90 rounded-full shadow-lg flex items-center justify-center text-[#2C2420] opacity-0 group-hover:opacity-100 transition-all duration-300 hover:scale-110 z-20"
                @click.stop="emit('play')"
            >
                <Play
                    v-if="props.playLoading"
                    :size="16"
                    class="animate-pulse"
                    fill="currentColor"
                />
                <Pause v-else-if="props.isPlaying" :size="16" fill="currentColor" />
                <Play v-else :size="16" fill="currentColor" class="ml-0.5" />
            </button>
        </div>

        <div class="text-center md:text-left">
            <h3
                class="font-serif text-lg leading-tight mb-1 truncate text-[#1A1A1A] group-hover:text-[#C27E46] transition-colors"
            >
                {{ title }}
            </h3>
            <p class="text-xs text-[#8C857B] uppercase tracking-wider truncate">
                {{ subtitle }}
            </p>
            <p class="text-[10px] text-[#B0AAA0] mt-1">
                {{ details }} <span v-if="badge">· {{ badge }}</span>
            </p>
        </div>
    </div>
</template>
