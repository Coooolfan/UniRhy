<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import DashboardTopBar from '@/components/dashboard/DashboardTopBar.vue'
import TaskSubmissionModal from '@/components/tasks/TaskSubmissionModal.vue'
import type { AsyncTaskLogDto } from '@/__generated/model/dto/AsyncTaskLogDto'
import type { TaskType } from '@/__generated/model/enums/TaskType'
import type { CodecTaskRequest, ScanTaskRequest } from '@/__generated/model/static'
import { TASK_TYPE_LABEL_MAP, useTaskManagement } from '@/composables/useTaskManagement'
import {
    AlertCircle,
    ArrowRight,
    CheckCircle2,
    ChevronLeft,
    ChevronRight,
    Clock,
    FileMusic,
    Loader2,
    RefreshCw,
    XCircle,
} from 'lucide-vue-next'

type TaskLogRecord = AsyncTaskLogDto['TaskController/DEFAULT_ASYNC_TASK_LOG_FETCHER']
type TaskDisplayStatus = 'RUNNING' | 'SUCCESS' | 'FAILED'
type SubmitFeedbackStatus = 'idle' | 'success'

type TaskLogRow = TaskLogRecord & {
    taskName: string
    paramsText: string
    status: TaskDisplayStatus
}

const SUBMIT_FEEDBACK_DURATION_MS = 4000

const {
    runningTasks,
    providerOptions,
    taskLogs,
    taskLogPageIndex,
    taskLogTotalPageCount,
    taskLogTotalRowCount,
    taskLogPageSize,
    isLoadingTasks,
    isLoadingTaskLogs,
    isLoadingProviders,
    isSubmitting,
    taskError,
    submitError,
    fetchRunningTasks,
    fetchTaskLogs,
    fetchProviders,
    startScanTask,
    startCodecTask,
    clearSubmitError,
    init,
} = useTaskManagement()

const isTaskModalOpen = ref(false)
const submitFeedbackStatus = ref<SubmitFeedbackStatus>('idle')
let submitFeedbackTimer: ReturnType<typeof setTimeout> | null = null

const statusLabelMap: Record<TaskDisplayStatus, string> = {
    RUNNING: '运行中',
    SUCCESS: '已完成',
    FAILED: '失败',
}

const statusTextClassMap: Record<TaskDisplayStatus, string> = {
    RUNNING: 'text-[#C67C4E]',
    SUCCESS: 'text-emerald-600',
    FAILED: 'text-rose-600',
}

const failureReasonPattern =
    /(timeout|timed out|fail|error|exception|abort|aborted|denied|invalid|not found|超时|失败|异常|中止|终止)/i

const getTaskName = (taskType: string) => TASK_TYPE_LABEL_MAP[taskType as TaskType] ?? taskType

const parseParamsText = (params: string) => {
    try {
        const parsed: unknown = JSON.parse(params)
        if (parsed === null || parsed === undefined) {
            return '--'
        }
        if (Array.isArray(parsed)) {
            return parsed.map(String).join(', ')
        }
        if (typeof parsed === 'object') {
            const entries = Object.entries(parsed as Record<string, unknown>)
            if (entries.length === 0) {
                return '--'
            }
            return entries.map(([key, value]) => `${key}: ${String(value)}`).join(', ')
        }
        return String(parsed)
    } catch {
        return params
    }
}

const resolveTaskStatus = (row: TaskLogRecord): TaskDisplayStatus => {
    if (row.running) {
        return 'RUNNING'
    }
    if (row.completedReason && failureReasonPattern.test(row.completedReason)) {
        return 'FAILED'
    }
    return 'SUCCESS'
}

const formatTime = (isoString?: string) => {
    if (!isoString) {
        return '--'
    }
    const date = new Date(isoString)
    if (Number.isNaN(date.getTime())) {
        return '--'
    }
    return date.toLocaleString('zh-CN', {
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
    })
}

const taskLogRows = computed<TaskLogRow[]>(() => {
    return taskLogs.value.map((row) => ({
        id: row.id,
        taskType: row.taskType,
        startedAt: row.startedAt,
        completedAt: row.completedAt,
        params: row.params,
        completedReason: row.completedReason,
        running: row.running,
        taskName: getTaskName(row.taskType),
        paramsText: parseParamsText(row.params),
        status: resolveTaskStatus(row),
    }))
})

const runningTaskCount = computed(() => runningTasks.value.length)

const runningSummaryText = computed(() => {
    if (runningTaskCount.value === 0) {
        return '当前没有运行中的任务，发起扫描或转码任务后可在此实时查看后台执行状态。'
    }
    const current = runningTasks.value[0]
    if (!current) {
        return '当前没有运行中的任务，发起扫描或转码任务后可在此实时查看后台执行状态。'
    }
    return `系统正在执行「${getTaskName(current.taskType)}」，可在下方日志查看参数与反馈。`
})

const currentPage = computed(() => taskLogPageIndex.value + 1)

const paginationItems = computed<Array<number | '...'>>(() => {
    const total = taskLogTotalPageCount.value
    if (total <= 0) {
        return []
    }
    if (total <= 5) {
        return Array.from({ length: total }, (_, index) => index + 1)
    }
    if (currentPage.value <= 3) {
        return [1, 2, 3, '...', total]
    }
    if (currentPage.value >= total - 2) {
        return [1, '...', total - 2, total - 1, total]
    }
    return [1, '...', currentPage.value - 1, currentPage.value, currentPage.value + 1, '...', total]
})

const displayRangeText = computed(() => {
    if (taskLogTotalRowCount.value === 0) {
        return '暂无任务记录'
    }
    const start = taskLogPageIndex.value * taskLogPageSize + 1
    const end = Math.min((taskLogPageIndex.value + 1) * taskLogPageSize, taskLogTotalRowCount.value)
    return `显示第 ${start} 至 ${end} 项，共 ${taskLogTotalRowCount.value} 项`
})

const canGoPrev = computed(() => taskLogPageIndex.value > 0 && !isLoadingTaskLogs.value)

const canGoNext = computed(
    () => taskLogPageIndex.value < taskLogTotalPageCount.value - 1 && !isLoadingTaskLogs.value,
)

const clearSubmitFeedbackTimer = () => {
    if (!submitFeedbackTimer) {
        return
    }
    clearTimeout(submitFeedbackTimer)
    submitFeedbackTimer = null
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
})

const openTaskModal = () => {
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
}

const handleScanSubmit = async (payload: ScanTaskRequest) => {
    const submitOk = await startScanTask(payload.providerType, payload.providerId)
    if (!submitOk) {
        return
    }
    closeTaskModal()
    showSubmitFeedback()
}

const handleCodecSubmit = async (payload: CodecTaskRequest) => {
    const submitOk = await startCodecTask(payload)
    if (!submitOk) {
        return
    }
    closeTaskModal()
    showSubmitFeedback()
}

const refreshAll = () => {
    void Promise.all([fetchRunningTasks(), fetchTaskLogs(taskLogPageIndex.value)])
}

const handlePageClick = (page: number | '...') => {
    if (page === '...') {
        return
    }
    const nextPageIndex = page - 1
    if (nextPageIndex === taskLogPageIndex.value) {
        return
    }
    void fetchTaskLogs(nextPageIndex)
}

const goPrevPage = () => {
    if (!canGoPrev.value) {
        return
    }
    void fetchTaskLogs(taskLogPageIndex.value - 1)
}

const goNextPage = () => {
    if (!canGoNext.value) {
        return
    }
    void fetchTaskLogs(taskLogPageIndex.value + 1)
}
</script>

<template>
    <div
        class="min-h-screen pb-32 font-sans text-[#5A524A] selection:bg-[#C67C4E] selection:text-white"
    >
        <DashboardTopBar />

        <div class="w-full max-w-5xl mx-auto px-8 pt-6 pb-12">
            <div class="mb-8 flex items-end justify-between border-b border-[#EAE6DE] pb-4">
                <div>
                    <h2 class="mb-1 font-serif text-3xl text-[#2B221B]">任务管理</h2>
                    <p class="font-serif text-sm italic text-[#8A8A8A]">
                        System Tasks & Background Jobs
                    </p>
                </div>
                <button
                    class="text-[#8A8A8A] transition-colors hover:text-[#C67C4E] disabled:opacity-50"
                    :disabled="isLoadingTasks || isLoadingTaskLogs"
                    title="刷新任务状态"
                    @click="refreshAll"
                >
                    <RefreshCw
                        class="w-5 h-5"
                        :class="{ 'animate-spin': isLoadingTasks || isLoadingTaskLogs }"
                    />
                </button>
            </div>

            <div
                v-if="taskError"
                class="mb-6 flex items-center border border-rose-200 bg-rose-50 p-4 text-sm text-rose-700"
            >
                <AlertCircle class="w-4 h-4 mr-2 shrink-0" />
                <span>{{ taskError }}</span>
            </div>

            <div class="grid grid-cols-1 lg:grid-cols-3 gap-8 mb-10">
                <div class="border border-[#EAE6DE] bg-[#fffcf5] p-6 shadow-sm lg:col-span-1">
                    <h3 class="font-serif text-2xl text-[#2B221B]">发起新任务</h3>
                    <p class="mt-2 text-sm leading-relaxed text-[#6B635B]">
                        媒体库扫描、转码等后台任务入口。
                    </p>

                    <button
                        type="button"
                        class="mt-6 flex w-full items-center justify-center gap-2 bg-[#C67C4E] px-4 py-3 text-sm uppercase tracking-[0.18em] text-[#F7F5F0] shadow-md transition-colors hover:bg-[#B46B3A] disabled:cursor-not-allowed disabled:opacity-60"
                        :disabled="isSubmitting"
                        @click="openTaskModal"
                    >
                        <span>发起新任务</span>
                        <ArrowRight class="h-4 w-4" />
                    </button>
                </div>

                <div
                    class="relative min-h-[196px] overflow-hidden border border-[#EAE6DE] bg-gradient-to-br from-[#F8F5EE] to-white p-6 shadow-sm lg:col-span-2"
                >
                    <div class="absolute top-0 right-0 p-8 opacity-5">
                        <FileMusic class="w-32 h-32" />
                    </div>

                    <div class="relative z-10 pt-3">
                        <div v-if="isLoadingTasks" class="mb-2 flex items-center text-[#C67C4E]">
                            <Loader2 class="w-5 h-5 mr-2 animate-spin" />
                            <span class="font-medium text-lg">正在同步后台任务状态</span>
                        </div>
                        <div
                            v-else-if="runningTaskCount > 0"
                            class="mb-2 flex items-center text-[#C67C4E]"
                        >
                            <RefreshCw class="w-5 h-5 mr-2 animate-spin" />
                            <span class="font-medium text-lg"
                                >{{ runningTaskCount }} 个任务正在后台运行</span
                            >
                        </div>
                        <div v-else class="mb-2 flex items-center text-[#8A8A8A]">
                            <Clock class="w-5 h-5 mr-2" />
                            <span class="font-medium text-lg">当前无运行中任务</span>
                        </div>
                        <p class="mt-2 max-w-md text-sm leading-relaxed text-[#6B635B]">
                            {{ runningSummaryText }}
                        </p>
                    </div>
                </div>
            </div>

            <div class="border border-[#EAE6DE] bg-white shadow-sm">
                <div
                    class="flex items-center justify-between border-b border-[#EAE6DE] bg-[#FAF9F6] px-6 py-4"
                >
                    <h3 class="font-medium text-[#2B221B]">执行日志</h3>
                    <span class="font-serif text-xs text-[#8A8A8A]">Task History Logs</span>
                </div>

                <div
                    v-if="isLoadingTaskLogs && taskLogRows.length === 0"
                    class="flex items-center justify-center py-14 text-sm text-[#6B635B]"
                >
                    <Loader2 class="w-4 h-4 mr-2 animate-spin" />
                    加载任务日志...
                </div>

                <div
                    v-else-if="taskLogRows.length === 0"
                    class="flex items-center justify-center py-14 text-sm text-[#6B635B]"
                >
                    暂无任务日志
                </div>

                <div v-else class="divide-y divide-[#F2EEE6]">
                    <div
                        v-for="row in taskLogRows"
                        :key="row.id"
                        class="group flex flex-col justify-between p-6 transition-colors hover:bg-[#FAF9F6] sm:flex-row sm:items-start"
                    >
                        <div class="flex-1 pr-6">
                            <div class="flex items-center flex-wrap mb-2">
                                <span
                                    class="flex items-center text-sm"
                                    :class="statusTextClassMap[row.status]"
                                >
                                    <RefreshCw
                                        v-if="row.status === 'RUNNING'"
                                        class="w-4 h-4 mr-1.5 animate-spin"
                                    />
                                    <CheckCircle2
                                        v-else-if="row.status === 'SUCCESS'"
                                        class="w-4 h-4 mr-1.5"
                                    />
                                    <XCircle v-else class="w-4 h-4 mr-1.5" />
                                    {{ statusLabelMap[row.status] }}
                                </span>
                                <span class="mx-3 text-[#D6D1C4]">|</span>
                                <span class="text-sm font-medium text-[#2B221B]">{{
                                    row.taskName
                                }}</span>
                                <span
                                    class="ml-3 rounded bg-[#F2F0E9] px-1.5 py-0.5 font-mono text-xs text-[#8A8A8A]"
                                    >#{{ row.id }}</span
                                >
                            </div>

                            <div class="mt-2 space-y-1">
                                <div class="flex items-start text-xs text-[#6B635B]">
                                    <span class="w-12 shrink-0 text-[#8A8A8A]">参数:</span>
                                    <span
                                        class="break-all rounded bg-[#F4F2ED] px-1 py-0.5 font-mono text-[#5A524A]"
                                    >
                                        {{ row.paramsText }}
                                    </span>
                                </div>
                                <div
                                    v-if="row.completedReason"
                                    class="mt-1 flex items-start text-xs"
                                >
                                    <span class="w-12 shrink-0 text-[#8A8A8A]">反馈:</span>
                                    <span
                                        :class="
                                            row.status === 'FAILED'
                                                ? 'text-rose-600'
                                                : 'text-[#5A524A]'
                                        "
                                    >
                                        <AlertCircle
                                            v-if="row.status === 'FAILED'"
                                            class="w-3 h-3 inline mr-1 mb-0.5"
                                        />
                                        {{ row.completedReason }}
                                    </span>
                                </div>
                            </div>
                        </div>

                        <div class="mt-4 sm:mt-0 text-right shrink-0">
                            <div class="mb-1 text-xs text-[#6B635B]">
                                <span class="mr-2 text-[#8A8A8A]">启动:</span>
                                <span class="font-mono">{{ formatTime(row.startedAt) }}</span>
                            </div>
                            <div class="text-xs text-[#6B635B]">
                                <span class="mr-2 text-[#8A8A8A]">结束:</span>
                                <span class="font-mono">{{ formatTime(row.completedAt) }}</span>
                            </div>
                        </div>
                    </div>
                </div>

                <div
                    class="flex items-center justify-between border-t border-[#EAE6DE] bg-[#FAF9F6] px-6 py-4"
                >
                    <span class="text-xs text-[#6B635B]">{{ displayRangeText }}</span>
                    <div v-if="paginationItems.length > 0" class="flex space-x-1">
                        <button
                            class="p-1 text-[#8A8A8A] hover:text-[#C67C4E] disabled:opacity-50"
                            :disabled="!canGoPrev"
                            @click="goPrevPage"
                        >
                            <ChevronLeft class="w-5 h-5" />
                        </button>
                        <button
                            v-for="(page, index) in paginationItems"
                            :key="`page-${index}-${page}`"
                            class="w-7 h-7 flex items-center justify-center text-xs rounded transition-colors"
                            :class="[
                                page === currentPage
                                    ? 'bg-[#C67C4E] text-white'
                                    : 'text-[#5A524A] hover:bg-[#EAE6DE]',
                                page === '...' ? 'cursor-default hover:bg-transparent' : '',
                            ]"
                            :disabled="page === '...'"
                            @click="handlePageClick(page)"
                        >
                            {{ page }}
                        </button>
                        <button
                            class="p-1 text-[#8A8A8A] hover:text-[#C67C4E] disabled:opacity-50"
                            :disabled="!canGoNext"
                            @click="goNextPage"
                        >
                            <ChevronRight class="w-5 h-5" />
                        </button>
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
            @submit-scan="handleScanSubmit"
            @submit-codec="handleCodecSubmit"
        />
    </div>
</template>
