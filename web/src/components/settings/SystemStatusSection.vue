<script setup lang="ts">
import { Database } from 'lucide-vue-next'
import type { SystemConfig } from '@/composables/useStorageSettings'

type Props = {
    activeFsLabel: string
    systemConfig: SystemConfig
    isLoading: boolean
    error: string
}

defineProps<Props>()
</script>

<template>
    <section class="mb-16 animate-in fade-in duration-500">
        <h2
            class="text-2xl font-serif text-[#4A3B32] mb-6 tracking-wide border-b border-[#E0Dcd0] pb-2"
        >
            全局状态
        </h2>

        <div class="space-y-8">
            <div class="bg-[#FAF9F6] p-8 shadow-[0_4px_20px_-5px_rgba(0,0,0,0.05)] relative">
                <!-- 纸张叠层效果 -->
                <div
                    class="absolute top-0 right-0 -mr-2 -mt-2 w-full h-full bg-white border border-[#EAE6DE] -z-10 shadow-sm"
                ></div>

                <h3 class="font-serif text-lg text-[#4A3B32] mb-6 flex items-center gap-2">
                    <Database :size="20" class="text-[#C68C53]" />
                    System Status
                </h3>

                <div class="flex items-center space-x-12">
                    <div class="text-center">
                        <div class="text-xs text-[#8A8A8A] mb-2 uppercase tracking-widest">
                            FS Provider
                        </div>
                        <div class="font-serif text-xl text-[#C68C53]">
                            <span v-if="isLoading">Loading...</span>
                            <span v-else>{{ activeFsLabel }}</span>
                        </div>
                        <div class="text-[10px] text-[#B0AAA0] uppercase tracking-widest mt-2">
                            ID: {{ systemConfig.fsProviderId ?? '-' }}
                        </div>
                    </div>
                </div>

                <div v-if="error" class="text-sm text-[#B95D5D] mt-4">
                    {{ error }}
                </div>
            </div>
        </div>
    </section>
</template>
