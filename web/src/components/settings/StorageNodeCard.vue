<script setup lang="ts">
import { Edit2, FolderOpen, HardDrive, Trash2 } from 'lucide-vue-next'
import type { StorageNode } from '@/composables/useStorageSettings'

type Props = {
    node: StorageNode
    activeFsId: number | null
    isSaving: boolean
}

defineProps<Props>()
const emit = defineEmits<{
    (event: 'edit'): void
    (event: 'delete'): void
}>()
</script>

<template>
    <div
        class="group relative bg-[#F7F5F0] p-0 rounded-sm hover:shadow-[0_8px_30px_rgba(0,0,0,0.04)] transition-all duration-300 border border-transparent hover:border-white"
    >
        <div class="flex h-full">
            <div
                class="w-24 bg-[#EAE6D9] flex items-center justify-center text-[#8A8A8A] border-r border-[#D6D1C4]/30 relative overflow-hidden group-hover:text-[#C67C4E] transition-colors duration-300"
            >
                <HardDrive :size="32" stroke-width="1.5" />
            </div>

            <div class="flex-1 p-6 flex flex-col">
                <div class="flex justify-between items-start mb-2">
                    <div>
                        <h3
                            class="text-xl font-medium group-hover:text-[#C67C4E] transition-colors duration-300"
                        >
                            {{ node.name }}
                        </h3>
                        <div class="text-[10px] text-[#8A8A8A] uppercase tracking-widest mt-1">
                            ID: {{ node.id }}
                        </div>
                    </div>
                    <div
                        class="flex flex-col gap-1 items-end group-hover:opacity-0 transition-opacity duration-300"
                    >
                        <span
                            v-if="node.readonly"
                            class="px-2 py-0.5 border border-[#D6D1C4] text-[10px] text-[#8A8A8A] uppercase"
                        >
                            Read-Only
                        </span>
                        <span
                            v-if="activeFsId === node.id"
                            class="px-2 py-0.5 bg-[#C67C4E] text-[10px] text-white uppercase"
                        >
                            Active
                        </span>
                    </div>
                </div>

                <div
                    class="flex items-center gap-2 text-[#7A756D] text-sm font-mono bg-[#EAE6D9]/50 p-2 rounded-sm mt-auto"
                >
                    <FolderOpen :size="14" class="text-[#C67C4E]" />
                    <span class="truncate" :title="node.parentPath">
                        {{ node.parentPath }}
                    </span>
                </div>
            </div>

            <div
                class="absolute top-4 right-4 flex gap-2 opacity-0 group-hover:opacity-100 transition-opacity"
            >
                <button
                    title="编辑"
                    class="p-2 hover:text-[#C67C4E] transition-colors disabled:opacity-50"
                    :disabled="isSaving"
                    @click="emit('edit')"
                >
                    <Edit2 :size="14" />
                </button>
                <button
                    title="删除"
                    class="p-2 hover:text-red-500 transition-colors disabled:opacity-50"
                    :disabled="isSaving"
                    @click="emit('delete')"
                >
                    <Trash2 :size="14" />
                </button>
            </div>
        </div>
    </div>
</template>
