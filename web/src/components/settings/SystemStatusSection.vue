<script setup lang="ts">
import { computed } from 'vue'
import { Activity, Folder, HardDrive } from 'lucide-vue-next'
import type { StorageNode, SystemConfig } from '@/composables/useStorageSettings'

type Props = {
    activeFsLabel: string
    systemConfig: SystemConfig
    activeNode: StorageNode | null
    isLoading: boolean
    error: string
}

const props = defineProps<Props>()

const nodeName = computed(() => {
    if (props.isLoading) {
        return 'Loading...'
    }
    return props.activeNode?.name || props.activeFsLabel || 'Default'
})

const nodeId = computed(() => {
    if (props.isLoading) {
        return '-'
    }
    return props.systemConfig.fsProviderId ?? '-'
})

const nodePath = computed(() => {
    if (props.isLoading) {
        return 'Loading...'
    }
    return props.activeNode?.parentPath || './data'
})
</script>

<template>
    <section class="mb-16 animate-in fade-in duration-500">
        <h2 class="text-2xl font-serif text-[#2C2A28] mb-2">全局状态</h2>
        <div class="h-px w-full bg-[#E8E4D9] mb-6"></div>

        <div
            class="flex w-full flex-col overflow-hidden shadow-sm transition-all duration-300 hover:shadow-md md:h-36 md:flex-row"
        >
            <div
                class="relative flex h-28 flex-col items-center justify-center overflow-hidden border border-[#E0DCD0] bg-[#EBE6D9] md:h-auto md:w-36 md:rounded-l-sm md:border-r-0"
            >
                <div
                    class="absolute top-0 left-0 w-full h-full bg-linear-to-b from-white/20 to-transparent"
                ></div>
                <HardDrive
                    class="text-[#8A857B] mb-2 relative z-10"
                    :size="32"
                    stroke-width="1.5"
                />
                <div class="flex items-center gap-1.5 relative z-10">
                    <span class="relative flex h-2 w-2">
                        <span
                            class="animate-ping absolute inline-flex h-full w-full rounded-full bg-[#B87A5B] opacity-75"
                        ></span>
                        <span class="relative inline-flex rounded-full h-2 w-2 bg-[#B87A5B]"></span>
                    </span>
                    <span class="text-[10px] text-[#8A857B] tracking-wider">ONLINE</span>
                </div>
            </div>

            <div
                class="relative flex flex-1 flex-col gap-5 overflow-hidden border border-t-0 border-[#E0DCD0] bg-[#FCFBF9] p-5 sm:p-6 md:flex-row md:items-center md:justify-between md:rounded-r-sm md:border-t md:p-7"
            >
                <div class="flex h-full flex-col justify-center">
                    <div
                        class="text-[10px] tracking-[0.2em] text-[#A39E93] uppercase mb-2 font-medium"
                    >
                        Current System Node
                    </div>

                    <div class="mb-4 flex flex-col gap-1 sm:flex-row sm:items-baseline sm:gap-4">
                        <h3 class="font-serif text-2xl tracking-wide text-[#33312E] sm:text-3xl">
                            {{ nodeName }}
                        </h3>
                        <span class="text-xs text-[#A39E93] font-mono">ID: {{ nodeId }}</span>
                    </div>

                    <div
                        class="flex items-center gap-2 bg-[#F4F1EA] px-3 py-1.5 rounded-sm w-fit border border-[#E8E4D9]"
                    >
                        <Folder :size="14" class="text-[#B87A5B]" />
                        <span class="font-mono text-xs text-[#66635C] tracking-tight">
                            {{ nodePath }}
                        </span>
                    </div>
                </div>

                <div class="z-10 flex h-full flex-col justify-center py-1 md:items-end">
                    <div
                        class="bg-[#B87A5B] text-white text-[10px] tracking-widest px-3 py-1.5 rounded-sm shadow-sm flex items-center gap-1.5"
                    >
                        <Activity :size="12" stroke-width="2.5" />
                        ACTIVE
                    </div>
                </div>

                <div
                    class="pointer-events-none absolute -bottom-16 -right-15 hidden text-[#f4ecd9] opacity-50 md:block"
                >
                    <HardDrive :size="180" stroke-width="0.5" />
                </div>
            </div>
        </div>

        <div v-if="error" class="text-sm text-[#B95D5D] mt-4">
            {{ error }}
        </div>
    </section>
</template>
