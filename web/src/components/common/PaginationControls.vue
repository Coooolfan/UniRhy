<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { ChevronLeft, ChevronRight } from 'lucide-vue-next'

const { t } = useI18n()

withDefaults(
    defineProps<{
        pageIndex: number
        totalPageCount: number
        disabled?: boolean
    }>(),
    {
        disabled: false,
    },
)

const emit = defineEmits<{
    (e: 'change', pageIndex: number): void
}>()
</script>

<template>
    <div
        v-if="totalPageCount > 1"
        class="mt-10 flex items-center justify-center gap-4 sm:mt-12 sm:gap-6"
    >
        <button
            :disabled="pageIndex === 0 || disabled"
            class="p-2 text-[#8C857B] hover:text-[#C27E46] disabled:opacity-30 disabled:hover:text-[#8C857B] transition-colors"
            @click="emit('change', pageIndex - 1)"
        >
            <ChevronLeft :size="20" />
        </button>
        <span class="font-serif text-sm text-[#5E5950]">
            {{ t('common.pageInfo', { current: pageIndex + 1, total: totalPageCount }) }}
        </span>
        <button
            :disabled="pageIndex >= totalPageCount - 1 || disabled"
            class="p-2 text-[#8C857B] hover:text-[#C27E46] disabled:opacity-30 disabled:hover:text-[#8C857B] transition-colors"
            @click="emit('change', pageIndex + 1)"
        >
            <ChevronRight :size="20" />
        </button>
    </div>
</template>
