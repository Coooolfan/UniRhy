<script setup lang="ts">
import { computed, ref, watch, type Component } from 'vue'
import { useRouter } from 'vue-router'
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
    Settings,
} from 'lucide-vue-next'
import type { CodecType } from '@/__generated/model/enums/CodecType'
import { type FileProviderType } from '@/__generated/model/enums/FileProviderType'
import type { TranscodeTaskRequest } from '@/__generated/model/static'
import type { TaskProviderOption } from '@/composables/useTaskManagement'

type TaskKind = 'METADATA_PARSE' | 'TRANSCODE'

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

type TaskAvailability = {
    title: string
    description: string
    icon: Component
}

type ProviderSelectionPayload = {
    providerType: FileProviderType
    providerId: number
}

const props = defineProps<Props>()

const emit = defineEmits<{
    (event: 'close'): void
    (event: 'submit-metadata-parse', payload: ProviderSelectionPayload): void
    (event: 'submit-transcode', payload: TranscodeTaskRequest): void
}>()

const TASK_ACTION_LABEL_MAP: Record<TaskKind, string> = {
    METADATA_PARSE: '元数据解析',
    TRANSCODE: '媒体转码',
}

const TASK_OPTIONS: TaskDefinition[] = [
    {
        id: 'METADATA_PARSE',
        name: TASK_ACTION_LABEL_MAP.METADATA_PARSE,
        desc: '遍历存储节点，发现媒体文件并补充缺失的元数据解析',
        icon: FolderSearch,
    },
    {
        id: 'TRANSCODE',
        name: TASK_ACTION_LABEL_MAP.TRANSCODE,
        desc: '遍历源存储节点中的所有已导入文件，转码为指定格式并保存到目标存储节点',
        icon: FileAudio,
    },
]

const TARGET_CODEC_OPTIONS: Array<{ value: CodecType; label: string; hint: string }> = [
    { value: 'OPUS', label: 'Opus', hint: '现代音频编码。转码目标为128Kbps可变码率。' },
]

const PROVIDER_TYPE_LABEL_MAP: Record<FileProviderType, string> = {
    FILE_SYSTEM: '本地存储',
    OSS: '对象存储',
}

const PROVIDER_TYPE_ICON_MAP: Record<FileProviderType, Component> = {
    FILE_SYSTEM: HardDrive,
    OSS: Cloud,
}

const activeTask = ref<TaskKind>('METADATA_PARSE')
const metadataParseProviderValue = ref('')
const transcodeSourceProviderValue = ref('')
const transcodeDestinationProviderValue = ref('')
const targetCodec = ref<CodecType>('OPUS')

const optionValueOf = (provider: TaskProviderOption) => `${provider.type}:${provider.id}`

const getDefaultProviderValue = (options: readonly TaskProviderOption[], preferredIndex = 0) => {
    const provider = options[preferredIndex] ?? options[0]
    return provider ? optionValueOf(provider) : ''
}

const syncSelectionValue = (
    currentValue: typeof metadataParseProviderValue,
    options: readonly TaskProviderOption[],
    preferredIndex = 0,
) => {
    const validValues = new Set(options.map((option) => optionValueOf(option)))

    if (!validValues.has(currentValue.value)) {
        currentValue.value = getDefaultProviderValue(options, preferredIndex)
    }
}

const metadataParseProviderOptions = computed(() =>
    props.providerOptions.filter(
        (provider) => provider.type === 'FILE_SYSTEM' && !provider.isSystemNode,
    ),
)

const transcodeSourceProviderOptions = computed(() =>
    props.providerOptions.filter(
        (provider) => provider.type === 'FILE_SYSTEM' && !provider.isSystemNode,
    ),
)

const transcodeDestinationProviderOptions = computed(() =>
    props.providerOptions.filter(
        (provider) => provider.type === 'FILE_SYSTEM' && !provider.readonly,
    ),
)

const syncProviderSelections = () => {
    syncSelectionValue(metadataParseProviderValue, metadataParseProviderOptions.value)
    syncSelectionValue(transcodeSourceProviderValue, transcodeSourceProviderOptions.value)
    syncSelectionValue(
        transcodeDestinationProviderValue,
        transcodeDestinationProviderOptions.value,
        1,
    )
}

const resolveProvider = (options: readonly TaskProviderOption[], value: string) =>
    options.find((provider) => optionValueOf(provider) === value)

watch(
    () => props.providerOptions,
    () => {
        syncProviderSelections()
    },
    { immediate: true },
)

watch(activeTask, () => {
    syncProviderSelections()
})

watch(
    () => props.open,
    (open) => {
        if (!open) {
            return
        }
        activeTask.value = 'METADATA_PARSE'
        targetCodec.value = 'OPUS'
        syncProviderSelections()
    },
)

const activeTaskOption = computed<TaskDefinition>(
    () => TASK_OPTIONS.find((option) => option.id === activeTask.value) ?? TASK_OPTIONS[0]!,
)

const selectedMetadataParseProvider = computed(() =>
    resolveProvider(metadataParseProviderOptions.value, metadataParseProviderValue.value),
)
const selectedTranscodeSourceProvider = computed(() =>
    resolveProvider(transcodeSourceProviderOptions.value, transcodeSourceProviderValue.value),
)
const selectedTranscodeDestinationProvider = computed(() =>
    resolveProvider(
        transcodeDestinationProviderOptions.value,
        transcodeDestinationProviderValue.value,
    ),
)

const canSubmit = computed(() => {
    if (props.isLoadingProviders) {
        return false
    }

    if (activeTask.value === 'METADATA_PARSE') {
        return Boolean(selectedMetadataParseProvider.value)
    }

    return Boolean(
        selectedTranscodeSourceProvider.value && selectedTranscodeDestinationProvider.value,
    )
})

const submitButtonLabel = computed(() =>
    activeTask.value === 'METADATA_PARSE' ? '提交元数据解析任务' : '提交转码任务',
)

const activeTaskAvailability = computed<TaskAvailability | null>(() => {
    if (props.providerOptions.length === 0) {
        return {
            title: '暂无可用存储节点',
            description: '请先在系统设置中配置本地存储节点，再回来发起元数据解析或转码任务。',
            icon: HardDrive,
        }
    }

    if (activeTask.value === 'METADATA_PARSE' && metadataParseProviderOptions.value.length === 0) {
        return {
            title: '暂无可解析节点',
            description: '元数据解析任务目前只支持本地存储节点。',
            icon: FolderSearch,
        }
    }

    if (activeTask.value === 'TRANSCODE' && transcodeSourceProviderOptions.value.length === 0) {
        return {
            title: '暂无可转码源节点',
            description: '转码任务目前只支持本地存储节点作为来源。',
            icon: FileAudio,
        }
    }

    if (
        activeTask.value === 'TRANSCODE' &&
        transcodeDestinationProviderOptions.value.length === 0
    ) {
        return {
            title: '暂无可写目标节点',
            description: '需要至少一个可写的本地存储节点作为转码输出。',
            icon: HardDrive,
        }
    }

    return null
})

const router = useRouter()

const closeModal = () => {
    if (props.isSubmitting) {
        return
    }
    emit('close')
}

const navigateToSettings = () => {
    emit('close')
    router.push({ name: 'settings' })
}

const submit = () => {
    if (!canSubmit.value || props.isSubmitting) {
        return
    }

    if (activeTask.value === 'METADATA_PARSE') {
        const provider = selectedMetadataParseProvider.value
        if (!provider) {
            return
        }
        emit('submit-metadata-parse', {
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
                                v-else-if="activeTaskAvailability"
                                class="flex min-h-[280px] flex-col items-center justify-center border border-dashed border-[#D6D1C4] px-6 text-center"
                            >
                                <component
                                    :is="activeTaskAvailability.icon"
                                    class="h-10 w-10 text-[#C27E46]"
                                />
                                <h4 class="mt-4 font-serif text-xl text-[#2C2C2C]">
                                    {{ activeTaskAvailability.title }}
                                </h4>
                                <p class="mt-2 max-w-md text-sm leading-relaxed text-[#6B635B]">
                                    {{ activeTaskAvailability.description }}
                                </p>
                                <button
                                    type="button"
                                    class="mt-4 px-8 py-3 border border-[#C27E46] text-[#C27E46] text-sm hover:bg-[#C27E46] hover:text-white transition-all duration-500 rounded-sm font-medium tracking-wide uppercase inline-flex items-center gap-2"
                                    @click="navigateToSettings"
                                >
                                    <Settings :size="16" />
                                    <span>前往设置</span>
                                </button>
                            </div>

                            <div v-else-if="activeTask === 'METADATA_PARSE'" class="space-y-8">
                                <div class="grid gap-6">
                                    <label class="block">
                                        <span
                                            class="mb-2 block text-xs uppercase tracking-[0.24em] text-[#8A8A8A]"
                                        >
                                            解析存储节点
                                        </span>
                                        <div class="relative">
                                            <select
                                                v-model="metadataParseProviderValue"
                                                data-test="metadata-parse-provider-select"
                                                class="w-full appearance-none bg-[#F7F5F0] border-b border-[#D6D1C4] p-3 pr-10 text-sm text-[#2C2C2C] outline-none transition-colors focus:border-[#C27E46]"
                                            >
                                                <option
                                                    v-for="option in metadataParseProviderOptions"
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
                                        v-if="selectedMetadataParseProvider"
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
                                                            selectedMetadataParseProvider.type
                                                        ]
                                                    "
                                                    class="h-4 w-4 text-[#C27E46]"
                                                />
                                                <span class="text-sm">{{
                                                    PROVIDER_TYPE_LABEL_MAP[
                                                        selectedMetadataParseProvider.type
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
                                                #{{ selectedMetadataParseProvider.id }}
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
                                                        v-for="option in transcodeSourceProviderOptions"
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
                                                        v-for="option in transcodeDestinationProviderOptions"
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
                            class="flex flex-col gap-4 pt-8 lg:flex-row lg:items-center lg:justify-end"
                        >
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
                        </footer>
                    </section>
                </div>
            </div>
        </Transition>
    </Teleport>
</template>
