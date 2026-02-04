<script setup lang="ts">
import { computed } from 'vue'
import { Database } from 'lucide-vue-next'

type Props = {
    open: boolean
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
    <Teleport to="body">
        <Transition
            enter-active-class="transition duration-200 ease-out"
            enter-from-class="opacity-0"
            enter-to-class="opacity-100"
            leave-active-class="transition duration-150 ease-in"
            leave-from-class="opacity-100"
            leave-to-class="opacity-0"
        >
            <div
                v-if="open"
                class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-[#2B221B]/60"
                @click.self="emit('cancel')"
            >
                <div
                    class="bg-[#fffcf5] p-8 w-full max-w-md shadow-[0_8px_30px_rgba(0,0,0,0.12)] border border-[#EAE6DE] relative transform transition-all"
                >
                    <!-- 装饰性细节 -->
                    <div
                        class="absolute top-0 right-0 w-16 h-16 bg-linear-to-bl from-[#EAE6DE]/30 to-transparent pointer-events-none"
                    ></div>

                    <div class="mb-8 text-center">
                        <div
                            class="inline-flex items-center justify-center w-12 h-12 rounded-full bg-[#FAF9F6] mb-4 border border-[#EAE6DE]"
                        >
                            <Database :size="24" class="text-[#C67C4E]" />
                        </div>
                        <h3 class="font-serif text-2xl text-[#2B221B]">新增存储节点</h3>
                        <p class="text-xs text-[#8A8A8A] mt-2 font-serif italic">
                            Add New Storage Node
                        </p>
                    </div>

                    <div class="space-y-6">
                        <div>
                            <label
                                class="text-xs uppercase tracking-wider text-[#8A8A8A] font-serif block mb-2"
                            >
                                Name
                            </label>
                            <input
                                v-model="nameModel"
                                type="text"
                                placeholder="e.g. Local Backup"
                                class="w-full bg-[#F7F5F0] border-b border-[#D6D1C4] p-3 text-[#3D3D3D] focus:outline-none focus:border-[#C67C4E] transition-colors font-serif placeholder:text-[#BDB9AE]"
                            />
                        </div>
                        <div>
                            <label
                                class="text-xs uppercase tracking-wider text-[#8A8A8A] font-serif block mb-2"
                            >
                                Root Path
                            </label>
                            <input
                                v-model="parentPathModel"
                                type="text"
                                placeholder="/path/to/dir"
                                class="w-full bg-[#F7F5F0] border-b border-[#D6D1C4] p-3 text-[#3D3D3D] focus:outline-none focus:border-[#C67C4E] transition-colors font-serif placeholder:text-[#BDB9AE]"
                            />
                        </div>
                        <label class="flex items-center gap-3 cursor-pointer group">
                            <div class="relative flex items-center">
                                <input
                                    v-model="readOnlyModel"
                                    type="checkbox"
                                    class="peer sr-only"
                                />
                                <div
                                    class="w-9 h-5 bg-[#EAE6DE] peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-4 after:w-4 after:transition-all peer-checked:bg-[#C67C4E]"
                                ></div>
                            </div>
                            <span
                                class="text-sm text-[#5A5A5A] group-hover:text-[#2B221B] transition-colors"
                            >
                                只读模式 (Read-Only)
                            </span>
                        </label>

                        <div class="flex gap-3 mt-8 pt-6 border-t border-[#EAE6DE]">
                            <button
                                class="flex-1 px-4 py-2.5 border border-[#D6D1C4] text-[#8A8A8A] hover:bg-[#F7F5F0] hover:text-[#5A5A5A] transition-colors text-sm uppercase tracking-wide"
                                @click="emit('cancel')"
                            >
                                取消
                            </button>
                            <button
                                class="flex-1 px-4 py-2.5 bg-[#2B221B] text-[#F7F5F0] hover:bg-[#C67C4E] transition-colors text-sm uppercase tracking-wide shadow-md disabled:opacity-60 disabled:cursor-not-allowed"
                                :disabled="isSaving"
                                @click="emit('save')"
                            >
                                <span v-if="isSaving">Creating...</span>
                                <span v-else>创建节点</span>
                            </button>
                        </div>
                    </div>
                </div>
            </div>
        </Transition>
    </Teleport>
</template>
