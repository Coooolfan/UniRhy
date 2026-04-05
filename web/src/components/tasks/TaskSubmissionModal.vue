<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ArrowRight, FileAudio, FolderSearch, HardDrive, Loader2, X } from 'lucide-vue-next'
import type { CodecType } from '@/__generated/model/enums/CodecType'
import type { VectorizeMode } from '@/__generated/model/enums/VectorizeMode'
import type {
    PlaylistGenerateTaskRequest,
    TranscodeTaskRequest,
    VectorizeTaskRequest,
} from '@/__generated/model/static'
import type { TaskProviderOption } from '@/composables/useTaskManagement'
import TaskSubmissionDataCleanForm from '@/components/tasks/TaskSubmissionDataCleanForm.vue'
import TaskSubmissionMetadataParseForm from '@/components/tasks/TaskSubmissionMetadataParseForm.vue'
import TaskSubmissionPlaylistGenerateForm from '@/components/tasks/TaskSubmissionPlaylistGenerateForm.vue'
import TaskSubmissionTranscodeForm from '@/components/tasks/TaskSubmissionTranscodeForm.vue'
import TaskSubmissionVectorizeForm from '@/components/tasks/TaskSubmissionVectorizeForm.vue'
import {
    type ProviderSelectionPayload,
    type TaskAvailability,
    type TaskDefinition,
    type TaskKind,
    TASK_OPTIONS,
    optionValueOf,
    syncSelectionValue,
} from '@/components/tasks/taskSubmissionShared'

type Props = {
    open: boolean
    providerOptions: TaskProviderOption[]
    isLoadingProviders: boolean
    isSubmitting: boolean
    submitError: string
}

const props = defineProps<Props>()

const emit = defineEmits<{
    (event: 'close'): void
    (event: 'submit-metadata-parse', payload: ProviderSelectionPayload): void
    (event: 'submit-transcode', payload: TranscodeTaskRequest): void
    (event: 'submit-vectorize', payload: VectorizeTaskRequest): void
    (event: 'submit-data-clean'): void
    (event: 'submit-playlist-generate', payload: PlaylistGenerateTaskRequest): void
}>()

const activeTask = ref<TaskKind>('METADATA_PARSE')
const metadataParseProviderValue = ref('')
const transcodeSourceProviderValue = ref('')
const transcodeDestinationProviderValue = ref('')
const vectorizeMode = ref<VectorizeMode>('PENDING_ONLY')
const playlistGenerateDescription = ref('')
const targetCodec = ref<CodecType>('OPUS')

const metadataParseProviderOptions = computed(() =>
    props.providerOptions.filter((provider) => provider.type === 'FILE_SYSTEM'),
)

const transcodeSourceProviderOptions = computed(() =>
    props.providerOptions.filter((provider) => provider.type === 'FILE_SYSTEM'),
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
        vectorizeMode.value = 'PENDING_ONLY'
        playlistGenerateDescription.value = ''
        syncProviderSelections()
    },
)

const activeTaskOption = computed<TaskDefinition>(() => {
    return TASK_OPTIONS.find((option) => option.id === activeTask.value) ?? TASK_OPTIONS[0]!
})

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

const vectorizeRequest = computed<VectorizeTaskRequest>(() => ({
    mode: vectorizeMode.value,
}))

const playlistGenerateRequest = computed<PlaylistGenerateTaskRequest>(() => ({
    description: playlistGenerateDescription.value.trim(),
}))

const canSubmit = computed(() => {
    if (props.isLoadingProviders) {
        return false
    }

    if (activeTask.value === 'METADATA_PARSE') {
        return Boolean(selectedMetadataParseProvider.value)
    }

    if (activeTask.value === 'TRANSCODE') {
        return Boolean(
            selectedTranscodeSourceProvider.value && selectedTranscodeDestinationProvider.value,
        )
    }

    if (activeTask.value === 'VECTORIZE') {
        return true
    }

    if (activeTask.value === 'PLAYLIST_GENERATE') {
        return playlistGenerateRequest.value.description.length > 0
    }

    return true // DATA_CLEAN always submittable
})

const submitButtonLabel = computed(() => {
    if (activeTask.value === 'METADATA_PARSE') {
        return '提交元数据解析任务'
    }

    if (activeTask.value === 'TRANSCODE') {
        return '提交转码任务'
    }

    if (activeTask.value === 'VECTORIZE') {
        return '提交向量化任务'
    }

    if (activeTask.value === 'PLAYLIST_GENERATE') {
        return '提交歌单生成任务'
    }

    return '提交数据清洗任务'
})

const activeTaskAvailability = computed<TaskAvailability | null>(() => {
    if (
        props.providerOptions.length === 0 &&
        (activeTask.value === 'METADATA_PARSE' || activeTask.value === 'TRANSCODE')
    ) {
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

const submitHelperText = computed(() => {
    if (activeTask.value === 'METADATA_PARSE') {
        return '提交后系统会遍历所选节点，并按文件补充缺失的元数据解析任务。'
    }

    if (activeTask.value === 'TRANSCODE') {
        return '提交后会按录音拆分为多个后台转码任务，状态看板会显示排队与完成进度。'
    }

    if (activeTask.value === 'VECTORIZE') {
        return '提交后会按录音补充向量化任务，用于生成 embedding 并写入后台队列。'
    }

    if (activeTask.value === 'PLAYLIST_GENERATE') {
        return '提交后系统会根据描述在后台生成智能歌单，完成后会自动刷新侧边栏歌单列表。'
    }

    return '提交后会按录音补充数据清洗任务，用于批量调用外部模型清洗标题。'
})

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

    if (activeTask.value === 'TRANSCODE') {
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
        return
    }

    if (activeTask.value === 'VECTORIZE') {
        emit('submit-vectorize', vectorizeRequest.value)
        return
    }

    if (activeTask.value === 'PLAYLIST_GENERATE') {
        emit('submit-playlist-generate', playlistGenerateRequest.value)
        return
    }

    emit('submit-data-clean')
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
                            </div>

                            <TaskSubmissionMetadataParseForm
                                v-else-if="activeTask === 'METADATA_PARSE'"
                                :provider-options="metadataParseProviderOptions"
                                :provider-value="metadataParseProviderValue"
                                @update:provider-value="metadataParseProviderValue = $event"
                            />

                            <TaskSubmissionTranscodeForm
                                v-else-if="activeTask === 'TRANSCODE'"
                                :source-provider-options="transcodeSourceProviderOptions"
                                :destination-provider-options="transcodeDestinationProviderOptions"
                                :source-provider-value="transcodeSourceProviderValue"
                                :destination-provider-value="transcodeDestinationProviderValue"
                                :target-codec="targetCodec"
                                @update:source-provider-value="
                                    transcodeSourceProviderValue = $event
                                "
                                @update:destination-provider-value="
                                    transcodeDestinationProviderValue = $event
                                "
                                @update:target-codec="targetCodec = $event"
                            />

                            <TaskSubmissionVectorizeForm
                                v-else-if="activeTask === 'VECTORIZE'"
                                :mode="vectorizeMode"
                                @update:mode="vectorizeMode = $event"
                            />

                            <TaskSubmissionPlaylistGenerateForm
                                v-else-if="activeTask === 'PLAYLIST_GENERATE'"
                                :description="playlistGenerateDescription"
                                @update:description="playlistGenerateDescription = $event"
                            />

                            <TaskSubmissionDataCleanForm v-else />
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
