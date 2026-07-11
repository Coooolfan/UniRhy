<script setup lang="ts">
import { Edit2, Mail, ShieldCheck, Trash2, User } from 'lucide-vue-next'
import type { Account } from '@/composables/useAccountSettings'

type Props = {
    account: Account
    isSaving: boolean
    isCurrent: boolean
    canManage?: boolean
}

defineProps<Props>()
const emit = defineEmits<{
    (event: 'edit'): void
    (event: 'delete'): void
}>()
</script>

<template>
    <div
        class="group relative rounded-sm border border-transparent bg-[#F7F5F0] p-0 transition-all duration-300 hover:border-white hover:shadow-[0_8px_30px_rgba(0,0,0,0.04)]"
    >
        <div class="flex h-full flex-col sm:flex-row">
            <div
                class="relative flex h-12 items-center justify-center overflow-hidden border-b border-[#D6D1C4]/30 bg-[#EAE6D9] text-[#8A8A8A] transition-colors duration-300 group-hover:text-[#C67C4E] sm:h-auto sm:w-24 sm:border-b-0 sm:border-r"
            >
                <component :is="account.admin ? ShieldCheck : User" :size="32" stroke-width="1.5" />
            </div>

            <div class="flex min-w-0 flex-1 flex-col p-4 sm:p-6">
                <div class="mb-2 flex items-start justify-between gap-4">
                    <div class="min-w-0">
                        <h3
                            class="truncate text-lg font-medium transition-colors duration-300 group-hover:text-[#C67C4E] sm:text-xl"
                            :title="account.name"
                        >
                            {{ account.name }}
                        </h3>
                        <div class="mt-1 text-[10px] uppercase tracking-widest text-[#8A8A8A]">
                            {{ account.admin ? '管理员' : '普通用户' }} / ID:
                            {{ account.id }}
                        </div>
                    </div>
                    <div
                        class="flex flex-col items-end gap-1 transition-opacity duration-300 group-hover:opacity-0"
                    >
                        <span
                            v-if="isCurrent"
                            class="border border-[#D6D1C4] px-2 py-0.5 text-[10px] uppercase text-[#8A8A8A]"
                        >
                            当前账号
                        </span>
                        <span
                            v-if="account.admin"
                            class="bg-[#C67C4E] px-2 py-0.5 text-[10px] uppercase text-white"
                        >
                            ADMIN
                        </span>
                    </div>
                </div>

                <div
                    class="mt-auto flex min-w-0 items-center gap-2 rounded-sm bg-[#EAE6D9]/50 p-1.5 font-mono text-sm text-[#7A756D] sm:p-2"
                >
                    <Mail :size="14" class="shrink-0 text-[#C67C4E]" />
                    <span class="min-w-0 truncate" :title="account.email">
                        {{ account.email }}
                    </span>
                </div>
            </div>

            <div
                v-if="canManage"
                class="absolute right-4 top-4 flex gap-2 opacity-100 transition-opacity sm:opacity-0 sm:group-hover:opacity-100"
            >
                <button
                    title="编辑账号"
                    class="p-2 transition-colors hover:text-[#C67C4E] disabled:opacity-50"
                    :disabled="isSaving"
                    @click="emit('edit')"
                >
                    <Edit2 :size="14" />
                </button>
                <button
                    title="删除账号"
                    class="p-2 transition-colors hover:text-red-500 disabled:opacity-50"
                    :disabled="isSaving || isCurrent"
                    @click="emit('delete')"
                >
                    <Trash2 :size="14" />
                </button>
            </div>
        </div>
    </div>
</template>
