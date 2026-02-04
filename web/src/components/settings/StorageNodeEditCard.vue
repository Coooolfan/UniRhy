<script setup lang="ts">
import { computed } from 'vue'
import { Save } from 'lucide-vue-next'

type Props = {
    name: string
    parentPath: string
    readonly: boolean
    isSaving: boolean
}

const props = defineProps<Props>()
const emit = defineEmits<{
    (event: 'update:name', value: string): void
    (event: 'update:parentPath', value: string): void
    (event: 'update:readonly', value: boolean): void
    (event: 'cancel'): void
    (event: 'save'): void
}>()

const nameModel = computed({
    get: () => props.name,
    set: (value) => emit('update:name', value),
})
const parentPathModel = computed({
    get: () => props.parentPath,
    set: (value) => emit('update:parentPath', value),
})
const readOnlyModel = computed({
    get: () => props.readonly,
    set: (value) => emit('update:readonly', value),
})
</script>

<template>
    <div class="p-6 border border-[#C67C4E] bg-[#fffcf5]">
        <div class="space-y-4">
            <div>
                <label class="text-xs uppercase tracking-wider text-[#8A8A8A] font-serif"
                    >Name</label
                >
                <input
                    v-model="nameModel"
                    type="text"
                    class="bg-[#F2F0E9] border-b border-[#D6D1C4] p-2 text-[#3D3D3D] focus:outline-none focus:border-[#C67C4E] transition-colors font-serif w-full"
                />
            </div>
            <div>
                <label class="text-xs uppercase tracking-wider text-[#8A8A8A] font-serif">
                    Root Path
                </label>
                <input
                    v-model="parentPathModel"
                    type="text"
                    class="bg-[#F2F0E9] border-b border-[#D6D1C4] p-2 text-[#3D3D3D] focus:outline-none focus:border-[#C67C4E] transition-colors font-serif w-full"
                />
            </div>
            <label class="flex items-center gap-2 cursor-pointer mt-2">
                <input v-model="readOnlyModel" type="checkbox" class="accent-[#C67C4E] w-4 h-4" />
                <span class="text-sm text-[#5A5A5A]">只读模式 (Read-Only)</span>
            </label>
            <div class="flex gap-2 mt-4 justify-end border-t border-[#EAE6D9] pt-4">
                <button
                    class="text-xs uppercase tracking-wide px-3 py-1 hover:text-[#C67C4E]"
                    @click="emit('cancel')"
                >
                    取消
                </button>
                <button
                    class="flex items-center gap-1 text-xs uppercase tracking-wide px-3 py-1 bg-[#3D3D3D] text-[#F7F5F0] hover:bg-[#C67C4E] transition-colors disabled:opacity-60"
                    :disabled="isSaving"
                    @click="emit('save')"
                >
                    <Save :size="12" />
                    保存
                </button>
            </div>
        </div>
    </div>
</template>
