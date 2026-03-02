<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import DashboardTopBar from '@/components/dashboard/DashboardTopBar.vue'
import { useTaskManagement } from '@/composables/useTaskManagement'
import type { AsyncTaskLogDto } from '@/__generated/model/dto/AsyncTaskLogDto'
import {
    AlertCircle,
    CheckCircle2,
    ChevronDown,
    ChevronLeft,
    ChevronRight,
    Clock,
    FileMusic,
    Loader2,
    Play,
    RefreshCw,
    XCircle,
} from 'lucide-vue-next'

type TaskLogRecord = AsyncTaskLogDto['TaskController/DEFAULT_ASYNC_TASK_LOG_FETCHER']
type TaskDisplayStatus = 'RUNNING' | 'SUCCESS' | 'FAILED'

type TaskLogRow = TaskLogRecord & {
    taskName: string
    paramsText: string
    status: TaskDisplayStatus
}

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
    startScanTask,
    init,
} = useTaskManagement()

const selectedProviderId = ref('')
const isSubmitSuccessFlash = ref(false)
let submitSuccessTimer: ReturnType<typeof setTimeout> | null = null

const statusLabelMap: Record<TaskDisplayStatus, string> = {
    RUNNING: '运行中',
    SUCCESS: '已完成',
    FAILED: '失败',
}

const statusTextClassMap: Record<TaskDisplayStatus, string> = {
    RUNNING: 'text-[#B86134]',
    SUCCESS: 'text-emerald-600',
    FAILED: 'text-rose-600',
}

const failureReasonPattern =
    /(timeout|timed out|fail|error|exception|abort|aborted|denied|invalid|not found|超时|失败|异常|中止|终止)/i

const getTaskName = (taskType: string) => {
    switch (taskType) {
        case 'SCAN':
            return '媒体库扫描'
        default:
            return taskType
    }
}

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
        return '当前没有运行中的任务，发起扫描后可在此实时查看后台执行状态。'
    }
    const current = runningTasks.value[0]
    if (!current) {
        return '当前没有运行中的任务，发起扫描后可在此实时查看后台执行状态。'
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

watch(
    providerOptions,
    (options) => {
        const firstOption = options[0]
        if (selectedProviderId.value === '' && firstOption) {
            selectedProviderId.value = String(firstOption.id)
        }
    },
    { immediate: true },
)

onMounted(() => {
    init()
})

onUnmounted(() => {
    if (submitSuccessTimer) {
        clearTimeout(submitSuccessTimer)
        submitSuccessTimer = null
    }
})

const handleScan = async () => {
    if (!selectedProviderId.value) {
        return
    }
    const provider = providerOptions.value.find(
        (item) => String(item.id) === selectedProviderId.value,
    )
    if (!provider) {
        return
    }
    const submitOk = await startScanTask(provider.type, provider.id)
    if (!submitOk) {
        return
    }

    isSubmitSuccessFlash.value = true
    if (submitSuccessTimer) {
        clearTimeout(submitSuccessTimer)
    }
    submitSuccessTimer = setTimeout(() => {
        isSubmitSuccessFlash.value = false
        submitSuccessTimer = null
    }, 2000)
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
        class="min-h-screen text-[#4a4a4a] font-sans selection:bg-[#b86134] selection:text-white pb-32"
    >
        <DashboardTopBar />

        <div class="w-full max-w-5xl mx-auto px-8 pt-6 pb-12">
            <div class="flex items-end justify-between mb-8 pb-4 border-b border-[#e5e2db]">
                <div>
                    <h2 class="text-3xl font-serif text-[#2c2c2c] mb-1">任务管理</h2>
                    <p class="text-sm text-gray-500 font-serif italic">
                        System Tasks & Background Jobs
                    </p>
                </div>
                <button
                    class="text-gray-500 hover:text-[#b86134] transition-colors disabled:opacity-50"
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
                v-if="taskError || submitError"
                class="mb-6 p-4 bg-rose-50 text-rose-600 rounded border border-rose-100 text-sm flex items-center"
            >
                <AlertCircle class="w-4 h-4 mr-2 shrink-0" />
                <span>{{ taskError || submitError }}</span>
            </div>

            <div class="grid grid-cols-1 lg:grid-cols-3 gap-8 mb-10">
                <div class="bg-white p-6 rounded shadow-sm border border-[#e5e2db] lg:col-span-1">
                    <h3 class="text-lg font-medium text-[#2c2c2c] mb-6">发起新任务</h3>

                    <div class="mb-6">
                        <label class="block text-sm font-medium mb-1">媒体库扫描</label>
                        <p class="text-xs text-gray-400 font-serif italic mb-3">
                            Update media library index from storage
                        </p>

                        <div
                            v-if="isLoadingProviders"
                            class="text-sm text-gray-500 py-2 flex items-center"
                        >
                            <Loader2 class="w-4 h-4 mr-2 animate-spin" />
                            加载存储节点...
                        </div>

                        <p
                            v-else-if="providerOptions.length === 0"
                            class="text-sm text-gray-500 py-2 italic"
                        >
                            暂无可用存储节点
                        </p>

                        <div
                            v-else
                            class="relative border-b border-[#e5e2db] pb-2 cursor-pointer flex justify-between items-center group"
                        >
                            <select
                                v-model="selectedProviderId"
                                class="w-full appearance-none bg-transparent text-sm pr-6 outline-none cursor-pointer group-hover:text-[#b86134] transition-colors"
                            >
                                <option disabled value="">选择存储节点</option>
                                <option
                                    v-for="option in providerOptions"
                                    :key="option.id"
                                    :value="String(option.id)"
                                >
                                    {{ option.name }}
                                </option>
                            </select>
                            <ChevronDown class="w-4 h-4 text-gray-400 pointer-events-none" />
                        </div>
                    </div>

                    <button
                        class="w-full h-10 mt-2 border border-[#b86134] text-[#b86134] text-sm hover:bg-[#b86134] hover:text-white transition-colors duration-300 flex items-center justify-center rounded-sm disabled:opacity-50 disabled:cursor-not-allowed disabled:hover:bg-transparent disabled:hover:text-[#b86134]"
                        :disabled="
                            selectedProviderId === '' ||
                            isSubmitting ||
                            isLoadingProviders ||
                            isSubmitSuccessFlash
                        "
                        @click="handleScan"
                    >
                        <template v-if="isSubmitting">
                            <Loader2 class="w-4 h-4 mr-2 animate-spin" />
                            提交中...
                        </template>
                        <template v-else-if="isSubmitSuccessFlash">
                            <CheckCircle2 class="w-4 h-4 mr-2" />
                            任务已提交
                        </template>
                        <template v-else>
                            <Play class="w-4 h-4 mr-2" />
                            开始扫描
                        </template>
                    </button>
                </div>

                <div
                    class="bg-gradient-to-br from-[#f8f6f0] to-white p-6 rounded shadow-sm border border-[#e5e2db] lg:col-span-2 relative overflow-hidden min-h-[196px]"
                >
                    <div class="absolute top-0 right-0 p-8 opacity-5">
                        <FileMusic class="w-32 h-32" />
                    </div>

                    <div class="relative z-10">
                        <div v-if="isLoadingTasks" class="flex items-center text-[#b86134] mb-2">
                            <Loader2 class="w-5 h-5 mr-2 animate-spin" />
                            <span class="font-medium text-lg">正在同步后台任务状态</span>
                        </div>
                        <div
                            v-else-if="runningTaskCount > 0"
                            class="flex items-center text-[#b86134] mb-2"
                        >
                            <RefreshCw class="w-5 h-5 mr-2 animate-spin" />
                            <span class="font-medium text-lg"
                                >{{ runningTaskCount }} 个任务正在后台运行</span
                            >
                        </div>
                        <div v-else class="flex items-center text-gray-500 mb-2">
                            <Clock class="w-5 h-5 mr-2" />
                            <span class="font-medium text-lg">当前无运行中任务</span>
                        </div>
                        <p class="text-sm text-gray-500 mt-2 max-w-md leading-relaxed">
                            {{ runningSummaryText }}
                        </p>
                    </div>
                </div>
            </div>

            <div class="bg-white rounded shadow-sm border border-[#e5e2db]">
                <div
                    class="px-6 py-4 border-b border-[#e5e2db] flex justify-between items-center bg-[#faf9f7]"
                >
                    <h3 class="font-medium text-[#2c2c2c]">执行日志</h3>
                    <span class="text-xs text-gray-400 font-serif">Task History Logs</span>
                </div>

                <div
                    v-if="isLoadingTaskLogs && taskLogRows.length === 0"
                    class="py-14 text-gray-500 text-sm flex items-center justify-center"
                >
                    <Loader2 class="w-4 h-4 mr-2 animate-spin" />
                    加载任务日志...
                </div>

                <div
                    v-else-if="taskLogRows.length === 0"
                    class="py-14 text-gray-500 text-sm flex items-center justify-center"
                >
                    暂无任务日志
                </div>

                <div v-else class="divide-y divide-[#f0ece5]">
                    <div
                        v-for="row in taskLogRows"
                        :key="row.id"
                        class="p-6 hover:bg-[#faf9f7] transition-colors flex flex-col sm:flex-row sm:items-start justify-between group"
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
                                <span class="mx-3 text-gray-300">|</span>
                                <span class="text-sm font-medium text-[#2c2c2c]">{{
                                    row.taskName
                                }}</span>
                                <span
                                    class="ml-3 text-xs text-gray-400 font-mono bg-gray-100 px-1.5 py-0.5 rounded"
                                    >#{{ row.id }}</span
                                >
                            </div>

                            <div class="mt-2 space-y-1">
                                <div class="text-xs text-gray-500 flex items-start">
                                    <span class="w-12 text-gray-400 shrink-0">参数:</span>
                                    <span
                                        class="font-mono bg-[#f4f2ed] px-1 py-0.5 rounded text-gray-600 break-all"
                                    >
                                        {{ row.paramsText }}
                                    </span>
                                </div>
                                <div
                                    v-if="row.completedReason"
                                    class="text-xs flex items-start mt-1"
                                >
                                    <span class="w-12 text-gray-400 shrink-0">反馈:</span>
                                    <span
                                        :class="
                                            row.status === 'FAILED'
                                                ? 'text-rose-600'
                                                : 'text-gray-600'
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
                            <div class="text-xs text-gray-500 mb-1">
                                <span class="text-gray-400 mr-2">启动:</span>
                                <span class="font-mono">{{ formatTime(row.startedAt) }}</span>
                            </div>
                            <div class="text-xs text-gray-500">
                                <span class="text-gray-400 mr-2">结束:</span>
                                <span class="font-mono">{{ formatTime(row.completedAt) }}</span>
                            </div>
                        </div>
                    </div>
                </div>

                <div
                    class="px-6 py-4 border-t border-[#e5e2db] flex items-center justify-between bg-[#faf9f7]"
                >
                    <span class="text-xs text-gray-500">{{ displayRangeText }}</span>
                    <div v-if="paginationItems.length > 0" class="flex space-x-1">
                        <button
                            class="p-1 text-gray-400 hover:text-[#b86134] disabled:opacity-50"
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
                                    ? 'bg-[#b86134] text-white'
                                    : 'text-gray-600 hover:bg-[#e5e2db]',
                                page === '...' ? 'cursor-default hover:bg-transparent' : '',
                            ]"
                            :disabled="page === '...'"
                            @click="handlePageClick(page)"
                        >
                            {{ page }}
                        </button>
                        <button
                            class="p-1 text-gray-400 hover:text-[#b86134] disabled:opacity-50"
                            :disabled="!canGoNext"
                            @click="goNextPage"
                        >
                            <ChevronRight class="w-5 h-5" />
                        </button>
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>
