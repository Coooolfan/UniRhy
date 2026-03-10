<script setup lang="ts">
import { computed, ref, watch, type Component } from 'vue'
import {
    ArrowRight,
    ChevronDown,
    Cloud,
    FileAudio,
    FolderSearch,
    HardDrive,
    Loader2,
    Music4,
    X,
} from 'lucide-vue-next'
import type { CodecType } from '@/__generated/model/enums/CodecType'
import { type FileProviderType } from '@/__generated/model/enums/FileProviderType'
import type { TaskType } from '@/__generated/model/enums/TaskType'
import type { TranscodeTaskRequest } from '@/__generated/model/static'
import { TASK_TYPE_LABEL_MAP, type TaskProviderOption } from '@/composables/useTaskManagement'

type TaskKind = TaskType

type Props = {
    open: boolean
    providerOptions: TaskProviderOption[]
    isLoadingProviders: boolean
    isSubmitting: boolean
    submitError: string
}

type TaskDefinition = {
    id: TaskKind
    name: string
    desc: string
    icon: Component
}

type ProviderSelectionPayload = {
    providerType: FileProviderType
    providerId: number
}

const props = defineProps<Props>()

const emit = defineEmits<{
    (event: 'close'): void
    (event: 'submit-scan', payload: ProviderSelectionPayload): void
    (event: 'submit-transcode', payload: TranscodeTaskRequest): void
}>()

const TASK_OPTIONS: TaskDefinition[] = [
    {
        id: 'SCAN',
        name: TASK_TYPE_LABEL_MAP.SCAN,
        desc: '发现并索引新媒体文件',
        icon: FolderSearch,
    },
    {
        id: 'TRANSCODE',
        name: TASK_TYPE_LABEL_MAP.TRANSCODE,
        desc: '跨节点转换音频编码格式',
        icon: FileAudio,
    },
]

const TARGET_CODEC_OPTIONS: Array<{ value: CodecType; label: string; hint: string }> = [
    { value: 'OPUS', label: 'Opus', hint: '高压缩率，推荐流媒体播放' },
    { value: 'MP3', label: 'MP3', hint: '兼容性最好，适合通用分发' },
    { value: 'AAC', label: 'AAC', hint: '苹果生态与移动端兼容较好' },
]

const PROVIDER_TYPE_LABEL_MAP: Record<FileProviderType, string> = {
    FILE_SYSTEM: '本地存储',
    OSS: '对象存储',
}

const PROVIDER_TYPE_ICON_MAP: Record<FileProviderType, Component> = {
    FILE_SYSTEM: HardDrive,
    OSS: Cloud,
}

const activeTask = ref<TaskKind>('SCAN')
const scanProviderValue = ref('')
const transcodeSourceProviderValue = ref('')
const transcodeDestinationProviderValue = ref('')
const targetCodec = ref<CodecType>('OPUS')

const optionValueOf = (provider: TaskProviderOption) => `${provider.type}:${provider.id}`

const getDefaultProviderValue = (options: TaskProviderOption[], preferredIndex = 0) => {
    const provider = options[preferredIndex] ?? options[0]
    return provider ? optionValueOf(provider) : ''
}

const syncProviderSelections = (options: TaskProviderOption[]) => {
    const validValues = new Set(options.map((option) => optionValueOf(option)))

    if (!validValues.has(scanProviderValue.value)) {
        scanProviderValue.value = getDefaultProviderValue(options)
    }

    if (!validValues.has(transcodeSourceProviderValue.value)) {
        transcodeSourceProviderValue.value = getDefaultProviderValue(options)
    }

    if (!validValues.has(transcodeDestinationProviderValue.value)) {
        transcodeDestinationProviderValue.value = getDefaultProviderValue(options, 1)
    }
}

watch(
    () => props.providerOptions,
    (options) => {
        syncProviderSelections(options)
    },
    { immediate: true },
)

watch(
    () => props.open,
    (open) => {
        if (!open) {
            return
        }
        activeTask.value = 'SCAN'
        targetCodec.value = 'OPUS'
        syncProviderSelections(props.providerOptions)
    },
)

const activeTaskOption = computed<TaskDefinition>(
    () => TASK_OPTIONS.find((option) => option.id === activeTask.value) ?? TASK_OPTIONS[0]!,
)

const resolveProvider = (value: string) =>
    props.providerOptions.find((provider) => optionValueOf(provider) === value)

const selectedScanProvider = computed(() => resolveProvider(scanProviderValue.value))
const selectedTranscodeSourceProvider = computed(() =>
    resolveProvider(transcodeSourceProviderValue.value),
)
const selectedTranscodeDestinationProvider = computed(() =>
    resolveProvider(transcodeDestinationProviderValue.value),
)

const canSubmit = computed(() => {
    if (props.isLoadingProviders || props.providerOptions.length === 0) {
        return false
    }

    if (activeTask.value === 'SCAN') {
        return Boolean(selectedScanProvider.value)
    }

    return Boolean(
        selectedTranscodeSourceProvider.value && selectedTranscodeDestinationProvider.value,
    )
})

const submitButtonLabel = computed(() =>
    activeTask.value === 'SCAN' ? '提交扫描任务' : '提交转码任务',
)

const submitHelperText = '提交后将在后台异步执行，可在日志中查看状态。'

const closeModal = () => {
    if (props.isSubmitting) {
        return
    }
    emit('close')
}

const submit = () => {
    if (!canSubmit.value || props.isSubmitting) {
        return
    }

    if (activeTask.value === 'SCAN') {
        const provider = selectedScanProvider.value
        if (!provider) {
            return
        }
        emit('submit-scan', {
            providerType: provider.type,
            providerId: provider.id,
        })
        return
    }

    const source = selectedTranscodeSourceProvider.value
    const destination = selectedTranscodeDestinationProvider.value
    if (!source || !destination) {
        return
    }

    emit('submit-transcode', {
        srcProviderType: source.type,
        srcProviderId: source.id,
        dstProviderType: destination.type,
        dstProviderId: destination.id,
        targetCodec: targetCodec.value,
    })
}
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
                class="fixed inset-0 z-50 flex items-center justify-center bg-[#2C2C2C]/60 p-4"
                @click.self="closeModal"
            >
                <div
                    data-test="task-modal"
                    class="relative flex max-h-[92vh] w-full max-w-6xl flex-col overflow-hidden border border-[#E6E1D8] bg-[#F2EFE9] shadow-[0_18px_60px_rgba(44,44,44,0.22)] lg:grid lg:min-h-[680px] lg:grid-cols-[280px_minmax(0,1fr)]"
                >
                    <div
                        class="pointer-events-none absolute top-0 right-0 h-24 w-24 bg-linear-to-bl from-[#ECE6DB]/45 to-transparent"
                    ></div>

                    <aside class="flex flex-col bg-[#EBE7E0] py-7 text-[#2C2C2C] lg:py-8">
                        <div class="px-6 lg:px-8">
                            <div class="text-[11px] uppercase tracking-[0.32em] text-[#9C968B]">
                                Task Center
                            </div>
                            <h3 class="mt-3 font-serif text-2xl tracking-wide">发起任务</h3>
                        </div>

                        <div class="mt-6">
                            <div
                                class="mb-3 px-6 text-[11px] uppercase tracking-[0.32em] text-[#9C968B] lg:px-8"
                            >
                                任务类型
                            </div>
                            <div>
                                <button
                                    v-for="task in TASK_OPTIONS"
                                    :key="task.id"
                                    :data-test="`task-type-${task.id.toLowerCase()}`"
                                    type="button"
                                    class="group flex w-full items-center gap-3 px-6 py-4 text-left transition-all duration-200 lg:px-8"
                                    :class="
                                        activeTask === task.id
                                            ? 'bg-[#F2EFE9] text-[#C27E46]'
                                            : 'border-transparent text-[#6B635B] hover:bg-[#F2EFE9]/80 hover:text-[#2C2C2C]'
                                    "
                                    @click="activeTask = task.id"
                                >
                                    <component
                                        :is="task.icon"
                                        class="h-4 w-4 shrink-0"
                                        :class="
                                            activeTask === task.id
                                                ? 'text-[#C27E46]'
                                                : 'text-[#9C968B] group-hover:text-[#C27E46]'
                                        "
                                    />
                                    <div class="min-w-0 text-sm tracking-wide">{{ task.name }}</div>
                                </button>
                            </div>
                        </div>
                    </aside>

                    <section class="flex min-h-0 flex-col bg-[#F2EFE9] px-6 py-7 lg:px-8 lg:py-8">
                        <header class="flex items-start justify-between">
                            <div>
                                <div class="text-[11px] uppercase tracking-[0.32em] text-[#9C968B]">
                                    Task Submission
                                </div>
                                <h2 class="mt-3 font-serif text-3xl text-[#2C2C2C]">
                                    {{ activeTaskOption.name }}
                                </h2>
                                <p class="mt-3 max-w-2xl text-sm leading-relaxed text-[#6B635B]">
                                    {{ activeTaskOption.desc }}
                                </p>
                            </div>
                            <button
                                type="button"
                                class="rounded-sm p-2 text-[#9C968B] transition-colors hover:bg-[#F5F2EB] hover:text-[#2C2C2C]"
                                :disabled="isSubmitting"
                                @click="closeModal"
                            >
                                <X class="h-5 w-5" />
                            </button>
                        </header>

                        <div class="flex-1 overflow-y-auto pt-8">
                            <div
                                v-if="submitError"
                                class="mb-6 border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700"
                            >
                                {{ submitError }}
                            </div>

                            <div
                                v-if="isLoadingProviders"
                                class="flex min-h-[280px] items-center justify-center text-sm text-[#6B635B]"
                            >
                                <Loader2 class="mr-2 h-4 w-4 animate-spin text-[#C27E46]" />
                                正在同步可用存储节点...
                            </div>

                            <div
                                v-else-if="providerOptions.length === 0"
                                class="flex min-h-[280px] flex-col items-center justify-center border border-dashed border-[#D6D1C4] px-6 text-center"
                            >
                                <HardDrive class="h-10 w-10 text-[#C27E46]" />
                                <h4 class="mt-4 font-serif text-xl text-[#2C2C2C]">
                                    暂无可用存储节点
                                </h4>
                                <p class="mt-2 max-w-md text-sm leading-relaxed text-[#6B635B]">
                                    请先在系统设置中配置本地存储或对象存储节点，再回来发起扫描或转码任务。
                                </p>
                            </div>

                            <div v-else-if="activeTask === 'SCAN'" class="space-y-8">
                                <div class="grid gap-6">
                                    <label class="block">
                                        <span
                                            class="mb-2 block text-xs uppercase tracking-[0.24em] text-[#8A8A8A]"
                                        >
                                            目标存储节点
                                        </span>
                                        <div class="relative">
                                            <select
                                                v-model="scanProviderValue"
                                                data-test="scan-provider-select"
                                                class="w-full appearance-none bg-[#F7F5F0] border-b border-[#D6D1C4] p-3 pr-10 text-sm text-[#2C2C2C] outline-none transition-colors focus:border-[#C27E46]"
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

                                    <div
                                        v-if="selectedScanProvider"
                                        class="grid gap-4 px-1 py-1 md:grid-cols-2"
                                    >
                                        <div>
                                            <div
                                                class="text-[11px] uppercase tracking-[0.24em] text-[#8A8A8A]"
                                            >
                                                节点类型
                                            </div>
                                            <div
                                                class="mt-2 flex items-center gap-2 text-[#2B221B]"
                                            >
                                                <component
                                                    :is="
                                                        PROVIDER_TYPE_ICON_MAP[
                                                            selectedScanProvider.type
                                                        ]
                                                    "
                                                    class="h-4 w-4 text-[#C27E46]"
                                                />
                                                <span class="text-sm">{{
                                                    PROVIDER_TYPE_LABEL_MAP[
                                                        selectedScanProvider.type
                                                    ]
                                                }}</span>
                                            </div>
                                        </div>
                                        <div>
                                            <div
                                                class="text-[11px] uppercase tracking-[0.24em] text-[#8A8A8A]"
                                            >
                                                节点 ID
                                            </div>
                                            <div class="mt-2 font-mono text-sm text-[#2C2C2C]">
                                                #{{ selectedScanProvider.id }}
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            </div>

                            <div v-else class="space-y-8">
                                <div
                                    class="grid gap-10 lg:grid-cols-[minmax(0,1fr)_128px_minmax(0,1fr)] lg:gap-0"
                                >
                                    <div class="space-y-5 lg:col-start-1">
                                        <div class="flex items-center gap-2">
                                            <span
                                                class="flex h-6 w-6 items-center justify-center bg-[#2C2C2C] text-[10px] font-bold text-white"
                                            >
                                                IN
                                            </span>
                                            <span class="text-sm font-medium text-[#2C2C2C]"
                                                >源节点</span
                                            >
                                        </div>

                                        <label class="block">
                                            <span
                                                class="mb-2 block text-xs uppercase tracking-[0.24em] text-[#8A8A8A]"
                                            >
                                                来源存储节点
                                            </span>
                                            <div class="relative">
                                                <select
                                                    v-model="transcodeSourceProviderValue"
                                                    data-test="transcode-source-select"
                                                    class="w-full appearance-none bg-[#F7F5F0] border-b border-[#D6D1C4] p-3 pr-10 text-sm text-[#2C2C2C] outline-none transition-colors focus:border-[#C27E46]"
                                                >
                                                    <option
                                                        v-for="option in providerOptions"
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

                                        <div
                                            v-if="selectedTranscodeSourceProvider"
                                            class="px-1 py-1"
                                        >
                                            <div
                                                class="text-[11px] uppercase tracking-[0.24em] text-[#8A8A8A]"
                                            >
                                                来源节点信息
                                            </div>
                                            <div
                                                class="mt-3 flex items-center gap-2 text-[#2B221B]"
                                            >
                                                <component
                                                    :is="
                                                        PROVIDER_TYPE_ICON_MAP[
                                                            selectedTranscodeSourceProvider.type
                                                        ]
                                                    "
                                                    class="h-4 w-4 text-[#C27E46]"
                                                />
                                                <span class="text-sm">{{
                                                    PROVIDER_TYPE_LABEL_MAP[
                                                        selectedTranscodeSourceProvider.type
                                                    ]
                                                }}</span>
                                                <span class="text-[#D6D1C4]">/</span>
                                                <span class="font-mono text-sm"
                                                    >#{{ selectedTranscodeSourceProvider.id }}</span
                                                >
                                            </div>
                                        </div>
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
                                            <span class="text-sm font-medium text-[#C27E46]"
                                                >目标节点</span
                                            >
                                        </div>

                                        <label class="block">
                                            <span
                                                class="mb-2 block text-xs uppercase tracking-[0.24em] text-[#8A8A8A]"
                                            >
                                                输出存储节点
                                            </span>
                                            <div class="relative">
                                                <select
                                                    v-model="transcodeDestinationProviderValue"
                                                    data-test="transcode-destination-select"
                                                    class="w-full appearance-none bg-[#F7F5F0] border-b border-[#D6D1C4] p-3 pr-10 text-sm text-[#2C2C2C] outline-none transition-colors focus:border-[#C27E46]"
                                                >
                                                    <option
                                                        v-for="option in providerOptions"
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

                                        <div
                                            v-if="selectedTranscodeDestinationProvider"
                                            class="px-1 py-1"
                                        >
                                            <div
                                                class="text-[11px] uppercase tracking-[0.24em] text-[#8A8A8A]"
                                            >
                                                输出节点信息
                                            </div>
                                            <div
                                                class="mt-3 flex items-center gap-2 text-[#2B221B]"
                                            >
                                                <component
                                                    :is="
                                                        PROVIDER_TYPE_ICON_MAP[
                                                            selectedTranscodeDestinationProvider
                                                                .type
                                                        ]
                                                    "
                                                    class="h-4 w-4 text-[#C27E46]"
                                                />
                                                <span class="text-sm">{{
                                                    PROVIDER_TYPE_LABEL_MAP[
                                                        selectedTranscodeDestinationProvider.type
                                                    ]
                                                }}</span>
                                                <span class="text-[#D6D1C4]">/</span>
                                                <span class="font-mono text-sm"
                                                    >#{{
                                                        selectedTranscodeDestinationProvider.id
                                                    }}</span
                                                >
                                            </div>
                                        </div>
                                    </div>
                                </div>

                                <label class="block pt-2">
                                    <span
                                        class="mb-2 block text-xs uppercase tracking-[0.24em] text-[#8A8A8A]"
                                    >
                                        目标编码格式
                                    </span>
                                    <div class="relative">
                                        <select
                                            v-model="targetCodec"
                                            data-test="target-codec-select"
                                            class="w-full appearance-none bg-[#F7F5F0] border-b border-[#D6D1C4] p-3 pr-10 text-sm text-[#2C2C2C] outline-none transition-colors focus:border-[#C27E46]"
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
                                        <span>
                                            {{
                                                TARGET_CODEC_OPTIONS.find(
                                                    (option) => option.value === targetCodec,
                                                )?.hint
                                            }}
                                        </span>
                                    </div>
                                </label>
                            </div>
                        </div>

                        <footer
                            class="flex flex-col gap-4 pt-8 lg:flex-row lg:items-center lg:justify-between"
                        >
                            <p class="text-sm text-[#6B635B]">
                                {{ submitHelperText }}
                            </p>

                            <div class="flex items-center gap-3">
                                <button
                                    type="button"
                                    class="px-4 py-2.5 text-sm uppercase tracking-wide text-[#8A8A8A] transition-colors hover:bg-[#F5F2EB] hover:text-[#2C2C2C] disabled:cursor-not-allowed disabled:opacity-60"
                                    :disabled="isSubmitting"
                                    @click="closeModal"
                                >
                                    取消
                                </button>
                                <button
                                    data-test="task-submit-button"
                                    type="button"
                                    class="flex items-center gap-2 bg-[#C27E46] px-5 py-3 text-sm uppercase tracking-[0.18em] text-[#F8F5EF] shadow-md transition-colors hover:bg-[#B36F38] disabled:cursor-not-allowed disabled:opacity-60"
                                    :disabled="!canSubmit || isSubmitting"
                                    @click="submit"
                                >
                                    <template v-if="isSubmitting">
                                        <Loader2 class="h-4 w-4 animate-spin" />
                                        <span>提交中...</span>
                                    </template>
                                    <template v-else>
                                        <span>{{ submitButtonLabel }}</span>
                                        <ArrowRight class="h-4 w-4" />
                                    </template>
                                </button>
                            </div>
                        </footer>
                    </section>
                </div>
            </div>
        </Transition>
    </Teleport>
</template>
