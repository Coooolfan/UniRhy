<script setup lang="ts">
import type { TaskProviderOption } from '@/composables/useTaskManagement'
import {
    PROVIDER_TYPE_ICON_MAP,
    PROVIDER_TYPE_LABEL_MAP,
} from '@/components/tasks/taskSubmissionShared'

type Props = {
    provider: TaskProviderOption
    title: string
    compact?: boolean
}

withDefaults(defineProps<Props>(), {
    compact: false,
})
</script>

<template>
    <div class="px-1 py-1">
        <div class="text-[11px] uppercase tracking-[0.24em] text-[#8A8A8A]">
            {{ title }}
        </div>
        <div
            class="mt-3 flex items-center gap-2 text-[#2B221B]"
            :class="compact ? '' : 'flex-wrap'"
        >
            <component
                :is="PROVIDER_TYPE_ICON_MAP[provider.type]"
                class="h-4 w-4 shrink-0 text-[#C27E46]"
            />
            <span class="text-sm">{{ PROVIDER_TYPE_LABEL_MAP[provider.type] }}</span>
            <span class="text-[#D6D1C4]">/</span>
            <span class="font-mono text-sm">#{{ provider.id }}</span>
        </div>
    </div>
</template>
