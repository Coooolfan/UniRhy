<script setup lang="ts">
withDefaults(
    defineProps<{
        /**
         * dense：艺术家头像等小卡片（sm:3 列起，xl 6 列）
         * loose：专辑/作品/曲目封面大卡片（md:3 列起，xl 5 列）
         */
        variant?: 'dense' | 'loose'
    }>(),
    {
        variant: 'loose',
    },
)
</script>

<template>
    <div
        class="grid"
        :class="
            variant === 'dense'
                ? 'grid-cols-2 gap-5 sm:grid-cols-3 sm:gap-8 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6'
                : 'grid-cols-2 gap-x-5 gap-y-10 sm:gap-x-8 sm:gap-y-12 md:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 xl:gap-x-12 xl:gap-y-16'
        "
    >
        <slot />
    </div>
</template>
