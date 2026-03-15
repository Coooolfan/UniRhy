<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import DashboardTopBar from '@/components/dashboard/DashboardTopBar.vue'
import TaskSubmissionModal from '@/components/tasks/TaskSubmissionModal.vue'
import type { TaskStatus } from '@/__generated/model/enums/TaskStatus'
import type { TaskType } from '@/__generated/model/enums/TaskType'
import type { ScanTaskRequest, TranscodeTaskRequest } from '@/__generated/model/static'
import { TASK_TYPE_LABEL_MAP, useTaskManagement } from '@/composables/useTaskManagement'
import {
    AlertCircle,
    ArrowRight,
    BarChart3,
    CheckCircle2,
    FileMusic,
    Loader2,
    RefreshCw,
    ServerCog,
} from 'lucide-vue-next'

type SubmitFeedbackStatus = 'idle' | 'success'
type SummaryTone = 'idle' | 'working' | 'failed' | 'done'

type TaskSummaryRow = {
    taskType: TaskType
    taskName: string
    pendingCount: number
    runningCount: number
    completedCount: number
    failedCount: number
    activeCount: number
    totalCount: number
    tone: SummaryTone
}

const TASK_TYPE_ORDER: TaskType[] = ['METADATA_PARSE', 'TRANSCODE']
const SUBMIT_FEEDBACK_DURATION_MS = 2000
const TASK_AUTO_REFRESH_INTERVAL_MS = 2000

const statusLabelMap: Record<TaskStatus, string> = {
    PENDING: '待处理',
    RUNNING: '执行中',
    COMPLETED: '已完成',
    FAILED: '失败',
}

const summaryToneClassMap: Record<SummaryTone, string> = {
    idle: 'border-[#EAE6DE] bg-[#F8F5EE] text-[#8A8A8A]',
    working: 'border-[#F0D3B8] bg-[#FFF7EE] text-[#C67C4E]',
    failed: 'border-rose-200 bg-rose-50 text-rose-600',
    done: 'border-emerald-200 bg-emerald-50 text-emerald-600',
}

const {
    taskCounts,
    providerOptions,
    isLoadingTaskCounts,
    isLoadingProviders,
    isSubmitting,
    taskError,
    submitError,
    fetchTaskCounts,
    fetchProviders,
    startMetadataParseTask,
    startTranscodeTask,
    clearSubmitError,
    init,
} = useTaskManagement()

const isTaskModalOpen = ref(false)
const submitFeedbackStatus = ref<SubmitFeedbackStatus>('idle')
let submitFeedbackTimer: ReturnType<typeof setTimeout> | null = null
let autoRefreshTimer: ReturnType<typeof setInterval> | null = null

const taskActionButtonLabel = computed(() =>
    submitFeedbackStatus.value === 'success' ? '任务已提交' : '发起新任务',
)

const isTaskActionButtonDisabled = computed(
    () => isSubmitting.value || submitFeedbackStatus.value === 'success',
)

const totalCountByStatus = (status: TaskStatus) =>
    taskCounts.value.filter((row) => row.status === status).reduce((sum, row) => sum + row.count, 0)

const pendingTaskCount = computed(() => totalCountByStatus('PENDING'))
const runningTaskCount = computed(() => totalCountByStatus('RUNNING'))
const completedTaskCount = computed(() => totalCountByStatus('COMPLETED'))
const failedTaskCount = computed(() => totalCountByStatus('FAILED'))
const activeTaskCount = computed(() => pendingTaskCount.value + runningTaskCount.value)
const totalTaskCount = computed(() => taskCounts.value.reduce((sum, row) => sum + row.count, 0))

const statusOverviewItems = computed(() => [
    {
        key: 'COMPLETED',
        count: completedTaskCount.value,
        label: statusLabelMap.COMPLETED,
        eyebrow: 'Completed',
        valueClass: 'text-[#2B221B]',
        eyebrowClass: 'text-[#8A8177]',
    },
    {
        key: 'RUNNING',
        count: runningTaskCount.value,
        label: statusLabelMap.RUNNING,
        eyebrow: 'Running',
        valueClass: 'text-[#2B221B]',
        eyebrowClass: 'text-[#B86134]/70',
    },
    {
        key: 'PENDING',
        count: pendingTaskCount.value,
        label: statusLabelMap.PENDING,
        eyebrow: 'Pending',
        valueClass: 'text-[#2B221B]',
        eyebrowClass: 'text-[#8A8177]',
    },
    {
        key: 'FAILED',
        count: failedTaskCount.value,
        label: statusLabelMap.FAILED,
        eyebrow: 'Failed',
        valueClass: 'text-[#2B221B]',
        eyebrowClass: 'text-[#8A8177]',
    },
    {
        key: 'TOTAL',
        count: totalTaskCount.value,
        label: '累计任务',
        eyebrow: 'Total',
        valueClass: 'text-[#B86134]',
        eyebrowClass: 'text-[#B86134]/70',
    },
])

const getTaskCount = (taskType: TaskType, status: TaskStatus) =>
    taskCounts.value.find((row) => row.taskType === taskType && row.status === status)?.count ?? 0

const taskSummaryRows = computed<TaskSummaryRow[]>(() =>
    TASK_TYPE_ORDER.map((taskType) => {
        const pendingCount = getTaskCount(taskType, 'PENDING')
        const runningCount = getTaskCount(taskType, 'RUNNING')
        const completedCount = getTaskCount(taskType, 'COMPLETED')
        const failedCount = getTaskCount(taskType, 'FAILED')
        const activeCount = pendingCount + runningCount
        const totalCount = activeCount + completedCount + failedCount

        let tone: SummaryTone = 'idle'

        if (failedCount > 0) {
            tone = 'failed'
        } else if (activeCount > 0) {
            tone = 'working'
        } else if (completedCount > 0) {
            tone = 'done'
        }

        return {
            taskType,
            taskName: TASK_TYPE_LABEL_MAP[taskType],
            pendingCount,
            runningCount,
            completedCount,
            failedCount,
            activeCount,
            totalCount,
            tone,
        }
    }),
)

const queueSummaryText = computed(() => {
    if (activeTaskCount.value === 0) {
        return '当前没有待处理任务。发起元数据解析或转码后，这里会显示最新的队列与结果统计。'
    }

    if (pendingTaskCount.value > 0 && runningTaskCount.value > 0) {
        return `当前共有 ${activeTaskCount.value} 个任务待处理或执行中，其中 ${pendingTaskCount.value} 个排队，${runningTaskCount.value} 个正在执行。`
    }

    if (pendingTaskCount.value > 0) {
        return `当前共有 ${pendingTaskCount.value} 个任务在队列中等待处理。`
    }

    return `当前共有 ${runningTaskCount.value} 个任务正在后台执行。`
})

const progressWidth = (count: number, total: number) => {
    if (total <= 0) {
        return '0%'
    }

    return `${(count / total) * 100}%`
}

const clearSubmitFeedbackTimer = () => {
    if (!submitFeedbackTimer) {
        return
    }
    clearTimeout(submitFeedbackTimer)
    submitFeedbackTimer = null
}

const clearAutoRefreshTimer = () => {
    if (!autoRefreshTimer) {
        return
    }
    clearInterval(autoRefreshTimer)
    autoRefreshTimer = null
}

const refreshTaskCounts = () => {
    if (isLoadingTaskCounts.value) {
        return
    }
    void fetchTaskCounts()
}

const ensureAutoRefresh = () => {
    if (activeTaskCount.value === 0) {
        clearAutoRefreshTimer()
        return
    }
    if (autoRefreshTimer) {
        return
    }
    autoRefreshTimer = setInterval(() => {
        refreshTaskCounts()
    }, TASK_AUTO_REFRESH_INTERVAL_MS)
}

const showSubmitFeedback = () => {
    clearSubmitFeedbackTimer()
    submitFeedbackStatus.value = 'success'
    submitFeedbackTimer = setTimeout(() => {
        submitFeedbackStatus.value = 'idle'
        submitFeedbackTimer = null
    }, SUBMIT_FEEDBACK_DURATION_MS)
}

onMounted(() => {
    init()
})

onUnmounted(() => {
    clearSubmitFeedbackTimer()
    clearAutoRefreshTimer()
})

watch(activeTaskCount, () => {
    ensureAutoRefresh()
})

const openTaskModal = () => {
    if (isTaskActionButtonDisabled.value) {
        return
    }
    clearSubmitFeedbackTimer()
    submitFeedbackStatus.value = 'idle'
    clearSubmitError()
    isTaskModalOpen.value = true
    void fetchProviders()
}

const closeTaskModal = () => {
    if (isSubmitting.value) {
        return
    }
    isTaskModalOpen.value = false
    clearSubmitError()
    refreshTaskCounts()
}

const handleMetadataParseSubmit = async (payload: ScanTaskRequest) => {
    const submitOk = await startMetadataParseTask(payload.providerType, payload.providerId)
    if (!submitOk) {
        return
    }
    closeTaskModal()
    showSubmitFeedback()
}

const handleTranscodeSubmit = async (payload: TranscodeTaskRequest) => {
    const submitOk = await startTranscodeTask(payload)
    if (!submitOk) {
        return
    }
    closeTaskModal()
    showSubmitFeedback()
}

const refreshAll = () => {
    refreshTaskCounts()
}
</script>

<template>
    <div
        class="min-h-screen pb-32 font-sans text-[#5A524A] selection:bg-[#C67C4E] selection:text-white"
    >
        <DashboardTopBar />

        <div class="mx-auto w-full max-w-5xl px-4 pb-12 pt-4 sm:px-6 sm:pt-6 lg:px-8">
            <div
                class="mb-8 flex flex-col gap-4 border-b border-[#EAE6DE] pb-4 sm:flex-row sm:items-end sm:justify-between"
            >
                <div>
                    <h2 class="mb-1 font-serif text-3xl text-[#2B221B]">任务管理</h2>
                    <p class="font-serif text-sm italic text-[#8A8A8A]">
                        System Tasks & Background Queue
                    </p>
                </div>
                <button
                    class="text-[#8A8A8A] transition-colors hover:text-[#C67C4E] disabled:opacity-50"
                    :disabled="isLoadingTaskCounts"
                    title="刷新任务状态"
                    @click="refreshAll"
                >
                    <RefreshCw class="h-5 w-5" :class="{ 'animate-spin': isLoadingTaskCounts }" />
                </button>
            </div>

            <div
                v-if="taskError"
                class="mb-6 flex items-center border border-rose-200 bg-rose-50 p-4 text-sm text-rose-700"
            >
                <AlertCircle class="mr-2 h-4 w-4 shrink-0" />
                <span>{{ taskError }}</span>
            </div>

            <div class="mb-10 grid grid-cols-1 gap-8 lg:grid-cols-3">
                <div class="border border-[#EAE6DE] bg-[#FFFCF5] p-6 shadow-sm lg:col-span-1">
                    <h3 class="font-serif text-2xl text-[#2B221B]">异步任务</h3>
                    <p class="mt-2 text-sm leading-relaxed text-[#6B635B]">
                        元数据解析、转码等长耗时任务
                    </p>

                    <button
                        type="button"
                        data-test="open-task-button"
                        class="mt-6 flex w-full items-center justify-center gap-2 bg-[#C67C4E] px-4 py-3 text-sm uppercase tracking-[0.18em] text-[#F7F5F0] shadow-md transition-colors hover:bg-[#B46B3A] disabled:cursor-not-allowed disabled:opacity-60"
                        :disabled="isTaskActionButtonDisabled"
                        @click="openTaskModal"
                    >
                        <span>{{ taskActionButtonLabel }}</span>
                        <component
                            :is="submitFeedbackStatus === 'success' ? CheckCircle2 : ArrowRight"
                            class="h-4 w-4"
                        />
                    </button>
                </div>

                <div
                    class="relative min-h-[196px] overflow-hidden border border-[#EAE6DE] bg-gradient-to-br from-[#F8F5EE] to-white p-6 shadow-sm lg:col-span-2"
                >
                    <div class="absolute top-0 right-0 p-8 opacity-5">
                        <FileMusic class="h-32 w-32" />
                    </div>

                    <div class="relative z-10 pt-3">
                        <div
                            v-if="isLoadingTaskCounts"
                            class="mb-2 flex items-center text-[#C67C4E]"
                        >
                            <Loader2 class="mr-2 h-5 w-5 animate-spin" />
                            <span class="text-lg font-medium">正在同步后台任务统计</span>
                        </div>
                        <div
                            v-else-if="activeTaskCount > 0"
                            class="mb-2 flex items-center text-[#C67C4E]"
                        >
                            <RefreshCw class="mr-2 h-5 w-5" />
                            <span class="text-lg font-medium"
                                >{{ activeTaskCount }} 个任务待处理或执行中</span
                            >
                        </div>
                        <div v-else class="mb-2 flex items-center text-emerald-600">
                            <ServerCog class="mr-2 h-5 w-5" />
                            <span class="text-lg font-medium">任务队列空闲</span>
                        </div>

                        <p class="mt-2 max-w-xl text-sm leading-relaxed text-[#6B635B]">
                            {{ queueSummaryText }}
                        </p>
                    </div>
                </div>
            </div>

            <div class="mb-16">
                <div class="mb-8 flex items-center justify-between px-1">
                    <h3 class="text-lg font-medium text-[#2B221B]">状态概览</h3>
                    <span class="font-serif text-xs text-[#8A8A8A]">Queue Snapshot</span>
                </div>

                <div
                    v-if="isLoadingTaskCounts && taskCounts.length === 0"
                    class="flex items-center justify-center py-14 text-sm text-[#6B635B]"
                >
                    <Loader2 class="mr-2 h-4 w-4 animate-spin" />
                    加载任务状态...
                </div>

                <div v-else class="px-2 py-4 md:px-4 md:py-5">
                    <div class="grid gap-6 md:grid-cols-5">
                        <div
                            v-for="(item, index) in statusOverviewItems"
                            :key="item.key"
                            class="text-center md:px-4"
                            :class="
                                index < statusOverviewItems.length - 1
                                    ? 'md:border-r md:border-[#E8DFD2]'
                                    : ''
                            "
                        >
                            <div class="text-4xl font-serif sm:text-5xl" :class="item.valueClass">
                                {{ item.count }}
                            </div>
                            <div
                                class="mt-3 text-[10px] uppercase tracking-[0.28em]"
                                :class="item.eyebrowClass"
                            >
                                {{ item.eyebrow }}
                            </div>
                            <div class="mt-2 text-xs text-[#958A7E]">
                                {{ item.label }}
                            </div>
                        </div>
                    </div>
                </div>
            </div>

            <div>
                <div class="mb-8 flex items-center justify-between px-1">
                    <h3 class="text-lg font-medium text-[#2B221B]">任务类型分布</h3>
                    <BarChart3 class="h-4 w-4 text-[#8A8A8A]" />
                </div>

                <div class="space-y-12">
                    <div v-for="(row, index) in taskSummaryRows" :key="row.taskType" class="group">
                        <div
                            class="flex flex-col gap-4 md:flex-row md:items-end md:justify-between"
                        >
                            <div class="flex items-center gap-4">
                                <span class="font-mono text-sm text-[#BEB1A3]">
                                    {{ String(index + 1).padStart(2, '0') }}
                                </span>
                                <div>
                                    <div class="flex flex-wrap items-center gap-3">
                                        <h4
                                            class="font-serif text-2xl text-[#2B221B] transition-colors duration-500 group-hover:text-[#B86134]"
                                        >
                                            {{ row.taskName }}
                                        </h4>
                                    </div>
                                    <p class="mt-1 text-xs font-serif italic text-[#9A9187]">
                                        {{ row.taskType }}
                                    </p>
                                </div>
                            </div>

                            <div class="flex items-end gap-3">
                                <span
                                    class="text-4xl font-serif text-[#E3D8CB] transition-colors duration-500 group-hover:text-[#2B221B]"
                                >
                                    {{ row.totalCount }}
                                </span>
                                <span
                                    class="pb-1 text-[10px] uppercase tracking-[0.28em] text-[#B29A84]"
                                >
                                    Total
                                </span>
                            </div>
                        </div>

                        <div class="mt-5 h-px bg-[#E8DFD2]"></div>

                        <div class="mt-6 grid gap-5 md:grid-cols-3">
                            <div class="space-y-2">
                                <div
                                    class="flex items-center justify-between text-xs text-[#83796D]"
                                >
                                    <span class="uppercase tracking-[0.24em] text-[10px]">
                                        Completed
                                    </span>
                                    <span class="font-mono text-[11px] text-[#4A4A4A]">
                                        {{ row.completedCount }}
                                    </span>
                                </div>
                                <div
                                    class="h-1.5 w-full overflow-hidden rounded-full bg-[#E8DFD2]/60"
                                >
                                    <div
                                        class="h-full rounded-full bg-[#4A4A4A] transition-[width] duration-700"
                                        :style="{
                                            width: progressWidth(
                                                row.completedCount,
                                                row.totalCount,
                                            ),
                                        }"
                                    />
                                </div>
                            </div>

                            <div class="space-y-2">
                                <div
                                    class="flex items-center justify-between text-xs text-[#83796D]"
                                >
                                    <span class="uppercase tracking-[0.24em] text-[10px]">
                                        Active
                                    </span>
                                    <span class="font-mono text-[11px] text-[#4A4A4A]">
                                        {{ row.activeCount }}
                                    </span>
                                </div>
                                <div
                                    class="flex h-1.5 w-full overflow-hidden rounded-full bg-[#E8DFD2]/60"
                                >
                                    <div
                                        class="h-full bg-[#B86134] transition-[width] duration-700"
                                        :style="{
                                            width: progressWidth(row.runningCount, row.totalCount),
                                        }"
                                    />
                                    <div
                                        class="h-full bg-[#D8CEC2] transition-[width] duration-700"
                                        :style="{
                                            width: progressWidth(row.pendingCount, row.totalCount),
                                        }"
                                    />
                                </div>
                            </div>

                            <div class="space-y-2">
                                <div
                                    class="flex items-center justify-between text-xs text-[#83796D]"
                                >
                                    <span class="uppercase tracking-[0.24em] text-[10px]">
                                        Failed
                                    </span>
                                    <span class="font-mono text-[11px] text-[#4A4A4A]">
                                        {{ row.failedCount }}
                                    </span>
                                </div>
                                <div
                                    class="h-1.5 w-full overflow-hidden rounded-full bg-[#E8DFD2]/60"
                                >
                                    <div
                                        class="h-full rounded-full bg-[#2B221B] transition-[width] duration-700"
                                        :style="{
                                            width: progressWidth(row.failedCount, row.totalCount),
                                        }"
                                    />
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>

        <TaskSubmissionModal
            :open="isTaskModalOpen"
            :provider-options="providerOptions"
            :is-loading-providers="isLoadingProviders"
            :is-submitting="isSubmitting"
            :submit-error="submitError"
            @close="closeTaskModal"
            @submit-metadata-parse="handleMetadataParseSubmit"
            @submit-transcode="handleTranscodeSubmit"
        />
    </div>
</template>
