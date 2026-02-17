<script setup lang="ts">
import { computed } from 'vue'
import { Music, Trash2 } from 'lucide-vue-next'

const props = defineProps<{
    open: boolean
    name: string
    comment: string
    isDeleteAction: boolean
    isDeleteConfirming: boolean
    isEditing: boolean
    isDeleting: boolean
    error: string
    deleteError: string
}>()

const emit = defineEmits<{
    (e: 'close'): void
    (e: 'submit'): void
    (e: 'update:name', value: string): void
    (e: 'update:comment', value: string): void
}>()

const modelName = computed({
    get: () => props.name,
    set: (value: string) => emit('update:name', value),
})

const modelComment = computed({
    get: () => props.comment,
    set: (value: string) => emit('update:comment', value),
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
                @click.self="emit('close')"
            >
                <div
                    class="bg-[#fffcf5] p-8 w-full max-w-md shadow-[0_8px_30px_rgba(0,0,0,0.12)] border border-[#EAE6DE] relative transform transition-all"
                >
                    <div
                        class="absolute top-0 right-0 w-16 h-16 bg-linear-to-bl from-[#EAE6DE]/30 to-transparent pointer-events-none"
                    ></div>

                    <div class="mb-8 text-center">
                        <div
                            class="inline-flex items-center justify-center w-12 h-12 rounded-full bg-[#FAF9F6] mb-4 border"
                            :class="isDeleteAction ? 'border-[#F0D6D6]' : 'border-[#EAE6DE]'"
                        >
                            <Trash2 v-if="isDeleteAction" :size="22" class="text-[#B95D5D]" />
                            <Music v-else :size="24" class="text-[#C67C4E]" />
                        </div>
                        <h3 class="font-serif text-2xl text-[#2B221B]">
                            {{ isDeleteAction ? '删除歌单' : '编辑歌单' }}
                        </h3>
                        <p class="text-xs text-[#8A8A8A] mt-2 font-serif italic">
                            {{
                                isDeleteAction
                                    ? isDeleteConfirming
                                        ? 'Confirm Deletion'
                                        : 'Delete Playlist'
                                    : 'Edit Playlist'
                            }}
                        </p>
                    </div>

                    <div class="space-y-6">
                        <label class="block">
                            <span
                                class="text-xs uppercase tracking-wider text-[#8A8A8A] font-serif block mb-2"
                                >Name</span
                            >
                            <input
                                v-model="modelName"
                                type="text"
                                maxlength="100"
                                class="w-full bg-[#F7F5F0] border-b border-[#D6D1C4] p-3 text-[#3D3D3D] focus:outline-none focus:border-[#C67C4E] transition-colors font-serif placeholder:text-[#BDB9AE]"
                                placeholder="e.g. My Favorites"
                                :disabled="isEditing || isDeleting"
                            />
                            <p
                                v-if="isDeleteAction && isDeleteConfirming"
                                class="mt-2 text-sm text-[#B95D5D] font-serif italic"
                            >
                                再次点击“确认删除”后将永久删除歌单，此操作不可恢复。
                            </p>
                        </label>

                        <label v-if="!isDeleteAction" class="block">
                            <span
                                class="text-xs uppercase tracking-wider text-[#8A8A8A] font-serif block mb-2"
                            >
                                Description
                            </span>
                            <textarea
                                v-model="modelComment"
                                rows="3"
                                maxlength="500"
                                class="w-full resize-none bg-[#F7F5F0] border-b border-[#D6D1C4] p-3 text-[#3D3D3D] focus:outline-none focus:border-[#C67C4E] transition-colors font-serif placeholder:text-[#BDB9AE]"
                                placeholder="Optional short note for this playlist"
                                :disabled="isEditing || isDeleting"
                            />
                        </label>

                        <p v-if="error" class="text-sm text-[#B95D5D]">
                            {{ error }}
                        </p>
                        <p v-if="deleteError" class="text-sm text-[#B95D5D]">
                            {{ deleteError }}
                        </p>

                        <div class="flex gap-3 mt-8 pt-6 border-t border-[#EAE6DE]">
                            <button
                                type="button"
                                class="flex-1 px-4 py-2.5 border border-[#D6D1C4] text-[#8A8A8A] hover:bg-[#F7F5F0] hover:text-[#5A5A5A] transition-colors text-sm uppercase tracking-wide"
                                :disabled="isEditing || isDeleting"
                                @click="emit('close')"
                            >
                                取消
                            </button>
                            <button
                                type="button"
                                class="flex-1 px-4 py-2.5 text-[#F7F5F0] transition-colors text-sm uppercase tracking-wide shadow-md disabled:opacity-60 disabled:cursor-not-allowed"
                                :class="
                                    isDeleteAction
                                        ? isDeleteConfirming
                                            ? 'bg-[#A24E4E] hover:bg-[#8E4040]'
                                            : 'bg-[#B95D5D] hover:bg-[#A24E4E]'
                                        : 'bg-[#2B221B] hover:bg-[#C67C4E]'
                                "
                                :disabled="isEditing || isDeleting"
                                @click="emit('submit')"
                            >
                                <span v-if="isEditing">Updating...</span>
                                <span v-else-if="isDeleting">删除中...</span>
                                <span v-else-if="isDeleteAction && isDeleteConfirming"
                                    >确认删除</span
                                >
                                <span v-else-if="isDeleteAction">删除歌单</span>
                                <span v-else>保存更改</span>
                            </button>
                        </div>
                    </div>
                </div>
            </div>
        </Transition>
    </Teleport>
</template>
