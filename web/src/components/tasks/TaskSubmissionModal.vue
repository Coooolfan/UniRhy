<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch, type Component } from 'vue'
import { useRouter } from 'vue-router'
import {
    ArrowRight,
    ChevronDown,
    ChevronLeft,
    Cloud,
    FileAudio,
    FolderSearch,
    HardDrive,
    Loader2,
    Music4,
    Puzzle,
    Settings,
} from 'lucide-vue-next'
import type { CodecType } from '@/__generated/model/enums/CodecType'
import { type FileProviderType } from '@/__generated/model/enums/FileProviderType'
import type { PluginInfoResponse } from '@/__generated/model/static/PluginInfoResponse'
import type { TranscodeTaskRequest } from '@/__generated/model/static'
import { useI18n } from 'vue-i18n'
import { resolveErrorMessage } from '@/i18n/errors'
import { useModalContext } from '@/components/modals/modalContext'
import type { TaskProviderOption } from '@/composables/useTaskManagement'

const { t } = useI18n()

type BuiltinTaskKind = 'METADATA_PARSE' | 'TRANSCODE'

type TaskDefinition = {
    id: string
    name: string
    desc: string
    icon: Component
    isPlugin: boolean
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

const props = defineProps<{
    loadProviders: () => Promise<TaskProviderOption[]>
    plugins: ReadonlyArray<PluginInfoResponse>
    submitMetadataParse: (payload: ProviderSelectionPayload) => Promise<void> | void
    submitTranscode: (payload: TranscodeTaskRequest) => Promise<void> | void
    submitPluginTask: (taskType: string, params: Record<string, string>) => Promise<void> | void
}>()

const BUILTIN_TASK_OPTIONS = computed<TaskDefinition[]>(() => [
    {
        id: 'METADATA_PARSE',
        name: t('taskSubmission.metadataParse'),
        desc: t('taskSubmission.metadataParseDesc'),
        icon: FolderSearch,
        isPlugin: false,
    },
    {
        id: 'TRANSCODE',
        name: t('taskSubmission.transcode'),
        desc: t('taskSubmission.transcodeDesc'),
        icon: FileAudio,
        isPlugin: false,
    },
])

const TARGET_CODEC_OPTIONS = computed<Array<{ value: CodecType; label: string; hint: string }>>(
    () => [{ value: 'OPUS', label: 'Opus', hint: t('taskSubmission.opusHint') }],
)

const PROVIDER_TYPE_LABEL_MAP = computed<Record<FileProviderType, string>>(() => ({
    FILE_SYSTEM: t('taskSubmission.localStorage'),
    OSS: t('taskSubmission.objectStorage'),
}))

const PROVIDER_TYPE_ICON_MAP: Record<FileProviderType, Component> = {
    FILE_SYSTEM: HardDrive,
    OSS: Cloud,
}

const modal = useModalContext<boolean>()
const router = useRouter()

const activeTaskId = ref<string>('METADATA_PARSE')
const mobileStep = ref<'select-type' | 'fill-form'>('select-type')
const isMobile = ref(false)
let mobileMediaQuery: MediaQueryList | null = null

const syncMobileViewport = (event?: MediaQueryListEvent) => {
    isMobile.value = event?.matches ?? mobileMediaQuery?.matches ?? false
}
const metadataParseProviderValue = ref('')
const transcodeSourceProviderValue = ref('')
const transcodeDestinationProviderValue = ref('')
const targetCodec = ref<CodecType>('OPUS')
const pluginFormValues = ref<Record<string, string>>({})
const providerOptions = ref<TaskProviderOption[]>([])
const isLoadingProviders = ref(true)
const isSubmitting = ref(false)
const submitError = ref('')

const pluginTaskOptions = computed<TaskDefinition[]>(() =>
    props.plugins
        .filter((p) => p.isAvailable && p.taskType !== undefined)
        .map((p) => ({
            id: p.taskType!,
            name: p.name ?? p.id,
            desc: t('taskSubmission.pluginDesc', { id: p.id, version: p.version }),
            icon: Puzzle,
            isPlugin: true,
        })),
)

const allTaskOptions = computed<TaskDefinition[]>(() => [
    ...BUILTIN_TASK_OPTIONS.value,
    ...pluginTaskOptions.value,
])

const activeTaskOption = computed<TaskDefinition>(
    () =>
        allTaskOptions.value.find((o) => o.id === activeTaskId.value) ??
        BUILTIN_TASK_OPTIONS.value[0]!,
)

const activePlugin = computed<PluginInfoResponse | null>(
    () => props.plugins.find((p) => p.taskType === activeTaskId.value) ?? null,
)

const initPluginFormValues = () => {
    const plugin = activePlugin.value
    if (!plugin) return
    const values: Record<string, string> = {}
    for (const field of plugin.form.fields) {
        values[field.name] = field.default ?? ''
    }
    pluginFormValues.value = values
}

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
    providerOptions.value.filter((provider) => !provider.isSystemNode),
)

const transcodeSourceProviderOptions = computed(() =>
    providerOptions.value.filter((provider) => !provider.isSystemNode),
)

const transcodeDestinationProviderOptions = computed(() =>
    providerOptions.value.filter((provider) => !provider.readonly),
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

watch(activeTaskId, () => {
    syncProviderSelections()
    initPluginFormValues()
})

const resolveProvider = (options: readonly TaskProviderOption[], value: string) =>
    options.find((provider) => optionValueOf(provider) === value)

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

const pluginFormValid = computed(() => {
    const plugin = activePlugin.value
    if (!plugin) return true
    for (const field of plugin.form.fields) {
        const raw = pluginFormValues.value[field.name] ?? ''
        if (field.type === 'integer') {
            const n = Number(raw)
            if (!Number.isInteger(n)) return false
            if (typeof field.min === 'number' && n < field.min) return false
            if (typeof field.max === 'number' && n > field.max) return false
        }
    }
    return true
})

const canSubmit = computed(() => {
    if (activePlugin.value) return pluginFormValid.value
    if (isLoadingProviders.value) return false
    if (activeTaskId.value === 'METADATA_PARSE') return Boolean(selectedMetadataParseProvider.value)
    return Boolean(
        selectedTranscodeSourceProvider.value && selectedTranscodeDestinationProvider.value,
    )
})

const submitButtonLabel = computed(() => {
    if (activePlugin.value)
        return t('taskSubmission.submitPluginTask', { name: activeTaskOption.value.name })
    if (activeTaskId.value === 'METADATA_PARSE') return t('taskSubmission.submitMetadataTask')
    return t('taskSubmission.submitTranscodeTask')
})

const activeTaskAvailability = computed<TaskAvailability | null>(() => {
    if (activePlugin.value) return null

    if (providerOptions.value.length === 0) {
        return {
            title: t('taskSubmission.noStorageTitle'),
            description: t('taskSubmission.noStorageDesc'),
            icon: HardDrive,
        }
    }

    if (
        activeTaskId.value === 'METADATA_PARSE' &&
        metadataParseProviderOptions.value.length === 0
    ) {
        return {
            title: t('taskSubmission.noParseNodeTitle'),
            description: t('taskSubmission.noParseNodeDesc'),
            icon: FolderSearch,
        }
    }

    if (activeTaskId.value === 'TRANSCODE' && transcodeSourceProviderOptions.value.length === 0) {
        return {
            title: t('taskSubmission.noTranscodeSourceTitle'),
            description: t('taskSubmission.noTranscodeSourceDesc'),
            icon: FileAudio,
        }
    }

    if (
        activeTaskId.value === 'TRANSCODE' &&
        transcodeDestinationProviderOptions.value.length === 0
    ) {
        return {
            title: t('taskSubmission.noWriteTargetTitle'),
            description: t('taskSubmission.noWriteTargetDesc'),
            icon: HardDrive,
        }
    }

    return null
})

const closeModal = () => {
    if (isSubmitting.value) return
    modal.close()
}

const navigateToSettings = async () => {
    modal.close()
    await router.push({ name: 'settings' })
}

const loadProviders = async () => {
    isLoadingProviders.value = true
    submitError.value = ''
    try {
        providerOptions.value = await props.loadProviders()
        syncProviderSelections()
    } finally {
        isLoadingProviders.value = false
    }
}

onMounted(() => {
    mobileMediaQuery = window.matchMedia('(max-width: 1023px)')
    syncMobileViewport()
    mobileMediaQuery.addEventListener('change', syncMobileViewport)
    void loadProviders()
})

onUnmounted(() => {
    mobileMediaQuery?.removeEventListener('change', syncMobileViewport)
})

const selectTask = (taskId: string) => {
    activeTaskId.value = taskId
    if (isMobile.value) {
        mobileStep.value = 'fill-form'
    }
}

const submit = async () => {
    if (!canSubmit.value || isSubmitting.value) return

    isSubmitting.value = true
    submitError.value = ''

    try {
        if (activePlugin.value) {
            await props.submitPluginTask(activeTaskId.value, { ...pluginFormValues.value })
        } else if (activeTaskId.value === 'METADATA_PARSE') {
            const provider = selectedMetadataParseProvider.value
            if (!provider) return
            await props.submitMetadataParse({
                providerType: provider.type,
                providerId: provider.id,
            })
        } else {
            const source = selectedTranscodeSourceProvider.value
            const destination = selectedTranscodeDestinationProvider.value
            if (!source || !destination) return
            await props.submitTranscode({
                srcProviderType: source.type,
                srcProviderId: source.id,
                dstProviderType: destination.type,
                dstProviderId: destination.id,
                targetCodec: targetCodec.value,
            })
        }

        modal.resolve(true)
    } catch (submitTaskError) {
        submitError.value = resolveErrorMessage(submitTaskError, 'errors.fallback.taskSubmit')
    } finally {
        isSubmitting.value = false
    }
}
</script>

<template>
    <div
        data-test="task-modal"
        class="flex min-h-0 w-full flex-col overflow-hidden lg:grid lg:h-auto lg:min-h-[620px] lg:grid-cols-[280px_minmax(0,1fr)]"
        :class="
            isMobile && mobileStep === 'select-type' ? 'h-auto' : 'h-[min(calc(85dvh-2px),718px)]'
        "
    >
        <aside
            v-if="!isMobile || mobileStep === 'select-type'"
            class="flex min-h-0 shrink-0 flex-col overflow-y-auto bg-[#EBE7E0] py-5 text-[#2C2C2C] lg:py-8"
        >
            <div class="px-6 lg:px-8">
                <div class="text-[11px] uppercase tracking-[0.32em] text-[#9C968B]">
                    Task Center
                </div>
                <h3 class="mt-3 font-serif text-2xl tracking-wide">
                    {{ t('taskSubmission.startTask') }}
                </h3>
            </div>

            <div class="mt-6">
                <div
                    class="mb-3 px-6 text-[11px] uppercase tracking-[0.32em] text-[#9C968B] lg:px-8"
                >
                    {{ t('taskSubmission.taskType') }}
                </div>
                <div>
                    <button
                        v-for="task in allTaskOptions"
                        :key="task.id"
                        :data-test="`task-type-${task.id.toLowerCase()}`"
                        type="button"
                        class="group flex w-full items-center gap-3 px-6 py-4 text-left transition-all duration-200 lg:px-8"
                        :class="
                            activeTaskId === task.id
                                ? 'bg-[#F2EFE9] text-[#C27E46]'
                                : 'border-transparent text-[#6B635B] hover:bg-[#F2EFE9]/80 hover:text-[#2C2C2C]'
                        "
                        :disabled="isSubmitting"
                        @click="selectTask(task.id)"
                    >
                        <component
                            :is="task.icon"
                            class="h-4 w-4 shrink-0"
                            :class="
                                activeTaskId === task.id
                                    ? 'text-[#C27E46]'
                                    : 'text-[#9C968B] group-hover:text-[#C27E46]'
                            "
                        />
                        <div class="min-w-0 text-sm tracking-wide">{{ task.name }}</div>
                    </button>
                </div>
            </div>
        </aside>

        <section
            v-if="!isMobile || mobileStep === 'fill-form'"
            class="flex min-h-0 flex-1 flex-col bg-[#F2EFE9] px-5 py-5 lg:px-8 lg:py-8"
        >
            <div class="min-w-0 shrink-0">
                <div class="flex items-center gap-3">
                    <button
                        v-if="isMobile"
                        type="button"
                        :aria-label="t('taskSubmission.backToTaskType')"
                        class="-ml-1 shrink-0 p-1 text-[#8A8A8A] transition-colors hover:text-[#C27E46] lg:hidden"
                        @click="mobileStep = 'select-type'"
                    >
                        <ChevronLeft class="h-5 w-5" />
                    </button>
                    <div class="text-[11px] uppercase tracking-[0.32em] text-[#9C968B]">
                        Task Submission
                    </div>
                </div>
                <h2 class="mt-3 font-serif text-2xl text-[#2C2C2C] lg:text-3xl">
                    {{ activeTaskOption.name }}
                </h2>
                <p class="mt-3 max-w-2xl text-sm leading-relaxed text-[#6B635B]">
                    {{ activeTaskOption.desc }}
                </p>
            </div>

            <div class="task-form-scroll flex-1 overflow-y-auto pt-8">
                <div
                    v-if="submitError"
                    class="mb-6 border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700"
                >
                    {{ submitError }}
                </div>

                <div
                    v-if="isLoadingProviders && !activePlugin"
                    class="flex min-h-[280px] items-center justify-center text-sm text-[#6B635B]"
                >
                    <Loader2 class="mr-2 h-4 w-4 animate-spin text-[#C27E46]" />
                    {{ t('taskSubmission.syncingNodes') }}
                </div>

                <div
                    v-else-if="activeTaskAvailability"
                    class="flex min-h-[280px] flex-col items-center justify-center border border-dashed border-[#D6D1C4] px-6 text-center"
                >
                    <component :is="activeTaskAvailability.icon" class="h-10 w-10 text-[#C27E46]" />
                    <h4 class="mt-4 font-serif text-xl text-[#2C2C2C]">
                        {{ activeTaskAvailability.title }}
                    </h4>
                    <p class="mt-2 max-w-md text-sm leading-relaxed text-[#6B635B]">
                        {{ activeTaskAvailability.description }}
                    </p>
                    <button
                        type="button"
                        class="mt-4 inline-flex items-center gap-2 rounded-sm border border-[#C27E46] px-8 py-3 text-sm font-medium uppercase tracking-wide text-[#C27E46] transition-all duration-500 hover:bg-[#C27E46] hover:text-white"
                        @click="navigateToSettings"
                    >
                        <Settings :size="16" />
                        <span>{{ t('taskSubmission.goToSettings') }}</span>
                    </button>
                </div>

                <!-- 插件通用表单 -->
                <div v-else-if="activePlugin" class="space-y-8">
                    <div class="grid gap-6">
                        <div
                            v-for="field in activePlugin.form.fields"
                            :key="field.name"
                            class="block"
                        >
                            <label>
                                <span
                                    class="mb-2 block text-xs uppercase tracking-[0.24em] text-[#8A8A8A]"
                                >
                                    {{ field.label }}
                                </span>
                                <input
                                    v-if="field.type === 'integer' || field.type === 'string'"
                                    v-model="pluginFormValues[field.name]"
                                    :type="field.type === 'integer' ? 'number' : 'text'"
                                    :min="field.min"
                                    :max="field.max"
                                    class="w-full border-b border-[#D6D1C4] bg-[#F7F5F0] p-3 text-sm text-[#2C2C2C] outline-none transition-colors focus:border-[#C27E46]"
                                />
                                <input
                                    v-else-if="field.type === 'boolean'"
                                    v-model="pluginFormValues[field.name]"
                                    type="checkbox"
                                    true-value="true"
                                    false-value="false"
                                    class="mt-1"
                                />
                            </label>
                            <p
                                v-if="field.description"
                                class="mt-2 text-xs leading-relaxed text-[#9C968B]"
                            >
                                {{ field.description }}
                            </p>
                        </div>

                        <div class="flex items-start gap-2 text-sm text-[#6B635B]">
                            <Puzzle class="mt-0.5 h-4 w-4 shrink-0 text-[#C27E46]" />
                            <span>
                                {{ t('taskSubmission.pluginTaskHint') }}
                            </span>
                        </div>
                    </div>
                </div>

                <!-- 元数据解析 -->
                <div v-else-if="activeTaskId === 'METADATA_PARSE'" class="space-y-8">
                    <div class="grid gap-6">
                        <label class="block">
                            <span
                                class="mb-2 block text-xs uppercase tracking-[0.24em] text-[#8A8A8A]"
                            >
                                {{ t('taskSubmission.parseStorageNode') }}
                            </span>
                            <div class="relative">
                                <select
                                    v-model="metadataParseProviderValue"
                                    data-test="metadata-parse-provider-select"
                                    class="w-full appearance-none border-b border-[#D6D1C4] bg-[#F7F5F0] p-3 pr-10 text-sm text-[#2C2C2C] outline-none transition-colors focus:border-[#C27E46]"
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
                                <div class="text-[11px] uppercase tracking-[0.24em] text-[#8A8A8A]">
                                    {{ t('taskSubmission.nodeType') }}
                                </div>
                                <div class="mt-2 flex items-center gap-2 text-[#2B221B]">
                                    <component
                                        :is="
                                            PROVIDER_TYPE_ICON_MAP[
                                                selectedMetadataParseProvider.type
                                            ]
                                        "
                                        class="h-4 w-4 text-[#C27E46]"
                                    />
                                    <span class="text-sm">
                                        {{
                                            PROVIDER_TYPE_LABEL_MAP[
                                                selectedMetadataParseProvider.type
                                            ]
                                        }}
                                    </span>
                                </div>
                            </div>
                            <div>
                                <div class="text-[11px] uppercase tracking-[0.24em] text-[#8A8A8A]">
                                    {{ t('taskSubmission.nodeId') }}
                                </div>
                                <div class="mt-2 font-mono text-sm text-[#2C2C2C]">
                                    #{{ selectedMetadataParseProvider.id }}
                                </div>
                            </div>
                        </div>
                    </div>
                </div>

                <!-- 转码 -->
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
                                <span class="text-sm font-medium text-[#2C2C2C]">{{
                                    t('taskSubmission.sourceNode')
                                }}</span>
                            </div>

                            <label class="block">
                                <span
                                    class="mb-2 block text-xs uppercase tracking-[0.24em] text-[#8A8A8A]"
                                >
                                    {{ t('taskSubmission.sourceStorageNode') }}
                                </span>
                                <div class="relative">
                                    <select
                                        v-model="transcodeSourceProviderValue"
                                        data-test="transcode-source-select"
                                        class="w-full appearance-none border-b border-[#D6D1C4] bg-[#F7F5F0] p-3 pr-10 text-sm text-[#2C2C2C] outline-none transition-colors focus:border-[#C27E46]"
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

                            <div v-if="selectedTranscodeSourceProvider" class="px-1 py-1">
                                <div class="text-[11px] uppercase tracking-[0.24em] text-[#8A8A8A]">
                                    {{ t('taskSubmission.sourceNodeInfo') }}
                                </div>
                                <div class="mt-3 flex items-center gap-2 text-[#2B221B]">
                                    <component
                                        :is="
                                            PROVIDER_TYPE_ICON_MAP[
                                                selectedTranscodeSourceProvider.type
                                            ]
                                        "
                                        class="h-4 w-4 text-[#C27E46]"
                                    />
                                    <span class="text-sm">
                                        {{
                                            PROVIDER_TYPE_LABEL_MAP[
                                                selectedTranscodeSourceProvider.type
                                            ]
                                        }}
                                    </span>
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
                                <span class="text-sm font-medium text-[#C27E46]">{{
                                    t('taskSubmission.targetNode')
                                }}</span>
                            </div>

                            <label class="block">
                                <span
                                    class="mb-2 block text-xs uppercase tracking-[0.24em] text-[#8A8A8A]"
                                >
                                    {{ t('taskSubmission.outputStorageNode') }}
                                </span>
                                <div class="relative">
                                    <select
                                        v-model="transcodeDestinationProviderValue"
                                        data-test="transcode-destination-select"
                                        class="w-full appearance-none border-b border-[#D6D1C4] bg-[#F7F5F0] p-3 pr-10 text-sm text-[#2C2C2C] outline-none transition-colors focus:border-[#C27E46]"
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

                            <div v-if="selectedTranscodeDestinationProvider" class="px-1 py-1">
                                <div class="text-[11px] uppercase tracking-[0.24em] text-[#8A8A8A]">
                                    {{ t('taskSubmission.outputNodeInfo') }}
                                </div>
                                <div class="mt-3 flex items-center gap-2 text-[#2B221B]">
                                    <component
                                        :is="
                                            PROVIDER_TYPE_ICON_MAP[
                                                selectedTranscodeDestinationProvider.type
                                            ]
                                        "
                                        class="h-4 w-4 text-[#C27E46]"
                                    />
                                    <span class="text-sm">
                                        {{
                                            PROVIDER_TYPE_LABEL_MAP[
                                                selectedTranscodeDestinationProvider.type
                                            ]
                                        }}
                                    </span>
                                    <span class="text-[#D6D1C4]">/</span>
                                    <span class="font-mono text-sm"
                                        >#{{ selectedTranscodeDestinationProvider.id }}</span
                                    >
                                </div>
                            </div>
                        </div>
                    </div>

                    <label class="block pt-2">
                        <span class="mb-2 block text-xs uppercase tracking-[0.24em] text-[#8A8A8A]">
                            {{ t('taskSubmission.targetEncodingFormat') }}
                        </span>
                        <div class="relative">
                            <select
                                v-model="targetCodec"
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

            <footer class="flex flex-row items-center justify-end gap-3 pt-6 lg:gap-4 lg:pt-8">
                <button
                    type="button"
                    class="w-20 shrink-0 px-3 py-2.5 text-sm uppercase tracking-wide text-[#8A8A8A] transition-colors hover:bg-[#F5F2EB] hover:text-[#2C2C2C] disabled:cursor-not-allowed disabled:opacity-60 lg:w-auto lg:px-4"
                    :disabled="isSubmitting"
                    @click="closeModal"
                >
                    {{ t('common.cancel') }}
                </button>
                <button
                    data-test="task-submit-button"
                    type="button"
                    class="flex min-w-0 flex-1 items-center justify-center gap-2 whitespace-nowrap bg-[#C27E46] px-4 py-3 text-sm uppercase tracking-wide text-[#F8F5EF] shadow-md transition-colors hover:bg-[#B36F38] disabled:cursor-not-allowed disabled:opacity-60 lg:flex-initial lg:px-5 lg:tracking-[0.18em]"
                    :disabled="!canSubmit || isSubmitting"
                    @click="submit"
                >
                    <template v-if="isSubmitting">
                        <Loader2 class="h-4 w-4 animate-spin" />
                        <span>{{ t('taskSubmission.submitting') }}</span>
                    </template>
                    <template v-else>
                        <span>{{ submitButtonLabel }}</span>
                        <ArrowRight class="h-4 w-4" />
                    </template>
                </button>
            </footer>
        </section>
    </div>
</template>

<style scoped>
.task-form-scroll {
    scrollbar-color: #d6d1c4 transparent;
}

.task-form-scroll::-webkit-scrollbar {
    width: 6px;
}

.task-form-scroll::-webkit-scrollbar-track {
    background: transparent;
}

.task-form-scroll::-webkit-scrollbar-thumb {
    border-radius: 3px;
    background-color: #d6d1c4;
}

.task-form-scroll::-webkit-scrollbar-thumb:hover {
    background-color: #c0bab0;
}
</style>
