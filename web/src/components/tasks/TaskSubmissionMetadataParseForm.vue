<script setup lang="ts">
import { computed } from 'vue'
import { ChevronDown } from 'lucide-vue-next'
import type { TaskProviderOption } from '@/composables/useTaskManagement'
import { optionValueOf } from '@/components/tasks/taskSubmissionShared'
import TaskProviderSummary from '@/components/tasks/TaskProviderSummary.vue'

type Props = {
    providerOptions: TaskProviderOption[]
    providerValue: string
}

const props = defineProps<Props>()

const emit = defineEmits<{
    (event: 'update:providerValue', value: string): void
}>()

const providerModel = computed({
    get: () => props.providerValue,
    set: (value: string) => emit('update:providerValue', value),
})

const selectedProvider = computed(() =>
    props.providerOptions.find((provider) => optionValueOf(provider) === providerModel.value),
)
</script>

<template>
    <div class="space-y-8">
        <div class="grid gap-6">
            <label class="block">
                <span class="mb-2 block text-xs uppercase tracking-[0.24em] text-[#8A8A8A]">
                    解析存储节点
                </span>
                <div class="relative">
                    <select
                        v-model="providerModel"
                        data-test="metadata-parse-provider-select"
                        class="w-full appearance-none border-b border-[#D6D1C4] bg-[#F7F5F0] p-3 pr-10 text-sm text-[#2C2C2C] outline-none transition-colors focus:border-[#C27E46]"
                    >
                        <option
                            v-for="option in providerOptions"
                            :key="optionValueOf(option)"
                            :value="optionValueOf(option)"
                        >
                            {{ option.name }}
                        </option>
                    </select>
                    <ChevronDown
                        class="pointer-events-none absolute top-1/2 right-3 h-4 w-4 -translate-y-1/2 text-[#8A8A8A]"
                    />
                </div>
            </label>

            <div v-if="selectedProvider" class="grid gap-4 px-1 py-1 md:grid-cols-2">
                <TaskProviderSummary :provider="selectedProvider" title="节点信息" />
                <div>
                    <div class="text-[11px] uppercase tracking-[0.24em] text-[#8A8A8A]">
                        节点名称
                    </div>
                    <div class="mt-2 text-sm text-[#2C2C2C]">
                        {{ selectedProvider.name }}
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>
