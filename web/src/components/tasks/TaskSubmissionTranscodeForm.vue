<script setup lang="ts">
import { computed } from 'vue'
import { ArrowRight, ChevronDown, Music4 } from 'lucide-vue-next'
import type { CodecType } from '@/__generated/model/enums/CodecType'
import type { TaskProviderOption } from '@/composables/useTaskManagement'
import { TARGET_CODEC_OPTIONS, optionValueOf } from '@/components/tasks/taskSubmissionShared'
import TaskProviderSummary from '@/components/tasks/TaskProviderSummary.vue'

type Props = {
    sourceProviderOptions: TaskProviderOption[]
    destinationProviderOptions: TaskProviderOption[]
    sourceProviderValue: string
    destinationProviderValue: string
    targetCodec: CodecType
}

const props = defineProps<Props>()

const emit = defineEmits<{
    (event: 'update:sourceProviderValue', value: string): void
    (event: 'update:destinationProviderValue', value: string): void
    (event: 'update:targetCodec', value: CodecType): void
}>()

const sourceProviderModel = computed({
    get: () => props.sourceProviderValue,
    set: (value: string) => emit('update:sourceProviderValue', value),
})

const destinationProviderModel = computed({
    get: () => props.destinationProviderValue,
    set: (value: string) => emit('update:destinationProviderValue', value),
})

const targetCodecModel = computed({
    get: () => props.targetCodec,
    set: (value: CodecType) => emit('update:targetCodec', value),
})

const selectedSourceProvider = computed(() =>
    props.sourceProviderOptions.find(
        (provider) => optionValueOf(provider) === sourceProviderModel.value,
    ),
)

const selectedDestinationProvider = computed(() =>
    props.destinationProviderOptions.find(
        (provider) => optionValueOf(provider) === destinationProviderModel.value,
    ),
)

const selectedCodecHint = computed(
    () =>
        TARGET_CODEC_OPTIONS.find((option) => option.value === targetCodecModel.value)?.hint ?? '',
)
</script>

<template>
    <div class="space-y-8">
        <div class="grid gap-10 lg:grid-cols-[minmax(0,1fr)_128px_minmax(0,1fr)] lg:gap-0">
            <div class="space-y-5 lg:col-start-1">
                <div class="flex items-center gap-2">
                    <span
                        class="flex h-6 w-6 items-center justify-center bg-[#2C2C2C] text-[10px] font-bold text-white"
                    >
                        IN
                    </span>
                    <span class="text-sm font-medium text-[#2C2C2C]">源节点</span>
                </div>

                <label class="block">
                    <span class="mb-2 block text-xs uppercase tracking-[0.24em] text-[#8A8A8A]">
                        来源存储节点
                    </span>
                    <div class="relative">
                        <select
                            v-model="sourceProviderModel"
                            data-test="transcode-source-select"
                            class="w-full appearance-none border-b border-[#D6D1C4] bg-[#F7F5F0] p-3 pr-10 text-sm text-[#2C2C2C] outline-none transition-colors focus:border-[#C27E46]"
                        >
                            <option
                                v-for="option in sourceProviderOptions"
                                :key="`src-${optionValueOf(option)}`"
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

                <TaskProviderSummary
                    v-if="selectedSourceProvider"
                    :provider="selectedSourceProvider"
                    title="来源节点信息"
                    compact
                />
            </div>

            <div
                class="pointer-events-none hidden lg:relative lg:flex lg:col-start-2 lg:row-start-1 lg:flex-col lg:items-center lg:justify-center"
            >
                <div class="h-40 w-px bg-[#D6D1C4]"></div>
                <ArrowRight
                    class="absolute top-1/2 left-1/2 h-5 w-5 -translate-x-1/2 -translate-y-1/2 bg-[#F2EFE9] text-[#C27E46]"
                />
            </div>

            <div class="space-y-5 lg:col-start-3">
                <div class="flex items-center gap-2">
                    <span
                        class="flex h-6 w-6 items-center justify-center bg-[#C27E46] text-[10px] font-bold text-white"
                    >
                        OUT
                    </span>
                    <span class="text-sm font-medium text-[#C27E46]">目标节点</span>
                </div>

                <label class="block">
                    <span class="mb-2 block text-xs uppercase tracking-[0.24em] text-[#8A8A8A]">
                        输出存储节点
                    </span>
                    <div class="relative">
                        <select
                            v-model="destinationProviderModel"
                            data-test="transcode-destination-select"
                            class="w-full appearance-none border-b border-[#D6D1C4] bg-[#F7F5F0] p-3 pr-10 text-sm text-[#2C2C2C] outline-none transition-colors focus:border-[#C27E46]"
                        >
                            <option
                                v-for="option in destinationProviderOptions"
                                :key="`dst-${optionValueOf(option)}`"
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

                <TaskProviderSummary
                    v-if="selectedDestinationProvider"
                    :provider="selectedDestinationProvider"
                    title="输出节点信息"
                    compact
                />
            </div>
        </div>

        <label class="block pt-2">
            <span class="mb-2 block text-xs uppercase tracking-[0.24em] text-[#8A8A8A]">
                目标编码格式
            </span>
            <div class="relative">
                <select
                    v-model="targetCodecModel"
                    data-test="target-codec-select"
                    class="w-full appearance-none border-b border-[#D6D1C4] bg-[#F7F5F0] p-3 pr-10 text-sm text-[#2C2C2C] outline-none transition-colors focus:border-[#C27E46]"
                    :disabled="TARGET_CODEC_OPTIONS.length === 1"
                >
                    <option
                        v-for="option in TARGET_CODEC_OPTIONS"
                        :key="option.value"
                        :value="option.value"
                    >
                        {{ option.label }}
                    </option>
                </select>
                <ChevronDown
                    class="pointer-events-none absolute top-1/2 right-3 h-4 w-4 -translate-y-1/2 text-[#8A8A8A]"
                />
            </div>
            <div class="mt-3 flex items-start gap-2 text-sm text-[#6B635B]">
                <Music4 class="mt-0.5 h-4 w-4 shrink-0 text-[#C27E46]" />
                <span>{{ selectedCodecHint }}</span>
            </div>
        </label>
    </div>
</template>
