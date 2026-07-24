<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { LayoutGrid, List as ListIcon } from 'lucide-vue-next'

const { t } = useI18n()

const model = defineModel<'grid' | 'list'>({ required: true })
</script>

<template>
    <div class="flex bg-[#EFEAE2] p-1 rounded-md">
        <button
            class="p-2 rounded-sm transition-all"
            :class="
                model === 'grid'
                    ? 'bg-white shadow-sm text-[#2C2420]'
                    : 'text-[#8C857B] hover:text-[#5E5950]'
            "
            :title="t('media.gridView')"
            @click="model = 'grid'"
        >
            <LayoutGrid :size="18" />
        </button>
        <button
            class="p-2 rounded-sm transition-all"
            :class="
                model === 'list'
                    ? 'bg-white shadow-sm text-[#2C2420]'
                    : 'text-[#8C857B] hover:text-[#5E5950]'
            "
            :title="t('media.listView')"
            @click="model = 'list'"
        >
            <ListIcon :size="18" />
        </button>
    </div>
</template>
