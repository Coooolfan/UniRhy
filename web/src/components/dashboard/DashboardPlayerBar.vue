<script setup lang="ts">
import { MoreHorizontal, Play, SkipBack, SkipForward, Volume2 } from 'lucide-vue-next'
import { featuredAlbum } from './data'
</script>

<template>
    <div class="absolute bottom-6 left-6 right-6 z-50">
        <div
            class="bg-[#FCFBF9] h-20 rounded-sm shadow-[0_8px_30px_rgba(140,130,115,0.2)] border border-[#EBE7E0] flex items-center px-6 justify-between backdrop-blur-xl bg-opacity-95"
        >
            <!-- Now Playing Info -->
            <div class="flex items-center w-1/4">
                <div
                    class="w-12 h-12 bg-[#2C2C2C] shadow-md border-[3px] border-white mr-4 shrink-"
                >
                    <img
                        :src="featuredAlbum.cover"
                        alt="Now Playing"
                        class="w-full h-full object-cover"
                    />
                </div>
                <div class="overflow-hidden">
                    <div class="text-sm font-serif text-[#2C2C2C] truncate">
                        Ballade No. 1 in G Minor
                    </div>
                    <div class="text-xs text-[#9C968B] truncate">Chopin</div>
                </div>
            </div>

            <!-- Controls -->
            <div class="flex flex-col items-center w-2/4 px-4">
                <div class="flex items-center space-x-8 mb-2">
                    <SkipBack
                        :size="18"
                        class="text-[#9C968B] cursor-pointer hover:text-[#4A4A4A]"
                    />
                    <div
                        class="w-10 h-10 rounded-full border border-[#C27E46] flex items-center justify-center cursor-pointer hover:bg-[#C27E46] group transition-all shadow-sm"
                    >
                        <Play
                            :size="16"
                            class="text-[#C27E46] ml-1 group-hover:text-white group-hover:fill-white"
                        />
                    </div>
                    <SkipForward
                        :size="18"
                        class="text-[#9C968B] cursor-pointer hover:text-[#4A4A4A]"
                    />
                </div>
                <!-- Progress Bar -->
                <div
                    class="w-full max-w-2xl h-0.5 relative group cursor-pointer py-2 flex items-center"
                >
                    <!-- Track Background -->
                    <div class="w-full h-0.5 bg-[#EBE7E0] absolute left-0"></div>
                    <!-- Progress Fill -->
                    <div class="h-0.5 w-1/3 bg-[#C27E46] absolute left-0"></div>
                    <!-- Knob -->
                    <div
                        class="w-2 h-2 bg-[#C27E46] rounded-full absolute left-1/3 opacity-0 group-hover:opacity-100 transition-opacity shadow-sm transform -translate-x-1/2"
                    ></div>
                </div>
            </div>

            <!-- Volume & Extras -->
            <div class="flex items-center justify-end w-1/4 space-x-4">
                <MoreHorizontal
                    :size="18"
                    class="text-[#9C968B] cursor-pointer hover:text-[#4A4A4A]"
                />
                <div class="flex items-center space-x-2 group cursor-pointer">
                    <Volume2 :size="18" class="text-[#9C968B] group-hover:text-[#4A4A4A]" />
                    <div class="w-16 h-0.5 bg-[#EBE7E0] relative">
                        <div class="w-2/3 h-full bg-[#9C968B]"></div>
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>
