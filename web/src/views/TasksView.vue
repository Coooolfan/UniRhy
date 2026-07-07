<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import DashboardTopBar from '@/components/dashboard/DashboardTopBar.vue'
import { useModal } from '@/composables/useModal'
import TaskSubmissionModal from '@/components/tasks/TaskSubmissionModal.vue'
import SideDrawer from '@/components/SideDrawer.vue'
import TaskLogDrawerContent from '@/components/tasks/TaskLogDrawerContent.vue'
import type { TaskStatus } from '@/__generated/model/enums/TaskStatus'
import type { TaskType } from '@/__generated/model/enums/TaskType'
import type { ScanTaskRequest, TranscodeTaskRequest } from '@/__generated/model/static'
import { BUILTIN_TASK_TYPE_LABEL_MAP, useTaskManagement } from '@/composables/useTaskManagement'
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
import { useUserStore } from '@/stores/user'

type SubmitFeedbackStatus = 'idle' | 'success'
type SummaryTone = 'idle' | 'working' | 'failed' | 'done'

type TaskSummaryRow = {
    taskType: string
    taskName: string
    pendingCount: number
    runningCount: number
    completedCount: number
    failedCount: number
    activeCount: number
    totalCount: number
    tone: SummaryTone
}

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
    pluginList,
    isLoadingTaskCounts,
    isLoadingProviders,
    isSubmitting,
    taskError,
    submitError,
    fetchTaskCounts,
    fetchProviders,
    fetchPlugins,
    startMetadataParseTask,
    startTranscodeTask,
    startPluginTask,
    resolveTaskLabel,
    clearSubmitError,
    init,
} = useTaskManagement()
const modal = useModal()
const userStore = useUserStore()

const submitFeedbackStatus = ref<SubmitFeedbackStatus>('idle')
let submitFeedbackTimer: ReturnType<typeof setTimeout> | null = null
let autoRefreshTimer: ReturnType<typeof setInterval> | null = null

const lastRefreshedAt = ref<Date | null>(null)
const relativeTimeNow = ref(Date.now())
let relativeTimeTimer: ReturnType<typeof setInterval> | null = null

const lastRefreshedText = computed(() => {
    if (!lastRefreshedAt.value) return ''
    const diffSec = Math.floor((relativeTimeNow.value - lastRefreshedAt.value.getTime()) / 1000)
    if (diffSec < 60) return `${diffSec} 秒前刷新`
    const diffMin = Math.floor(diffSec / 60)
    if (diffMin < 60) return `${diffMin} 分钟前刷新`
    return `${Math.floor(diffMin / 60)} 小时前刷新`
})

const taskActionButtonLabel = computed(() =>
    submitFeedbackStatus.value === 'success' ? '任务已提交' : '发起新任务',
)

const isTaskActionButtonDisabled = computed(
    () => !userStore.isAdmin || isSubmitting.value || submitFeedbackStatus.value === 'success',
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

const getTaskCount = (taskType: string, status: TaskStatus) =>
    taskCounts.value.find((row) => row.taskType === taskType && row.status === status)?.count ?? 0

// 显示 taskCounts 中出现的所有类型，顺序：内置类型优先，其余按首次出现
const taskSummaryRows = computed<TaskSummaryRow[]>(() => {
    const seen = new Set<string>()
    const builtinOrder = ['METADATA_PARSE', 'TRANSCODE']
    const allTypes = [
        ...builtinOrder,
        ...taskCounts.value.map((r) => r.taskType).filter((t) => !builtinOrder.includes(t)),
    ]
    return allTypes
        .filter((t) => {
            if (seen.has(t)) return false
            seen.add(t)
            return true
        })
        .map((taskType) => {
            const pendingCount = getTaskCount(taskType, 'PENDING')
            const runningCount = getTaskCount(taskType, 'RUNNING')
            const completedCount = getTaskCount(taskType, 'COMPLETED')
            const failedCount = getTaskCount(taskType, 'FAILED')
            const activeCount = pendingCount + runningCount
            const totalCount = activeCount + completedCount + failedCount

            let tone: SummaryTone = 'idle'
            if (failedCount > 0) tone = 'failed'
            else if (activeCount > 0) tone = 'working'
            else if (completedCount > 0) tone = 'done'

            return {
                taskType,
                taskName: resolveTaskLabel(taskType),
                pendingCount,
                runningCount,
                completedCount,
                failedCount,
                activeCount,
                totalCount,
                tone,
            }
        })
})

const queueSummaryText = computed(() => {
    if (activeTaskCount.value === 0) {
        return '当前没有待处理任务。发起元数据解析、转码或插件任务后，这里会显示最新的队列与结果统计。'
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
    if (total <= 0) return '0%'
    return `${(count / total) * 100}%`
}

const clearSubmitFeedbackTimer = () => {
    if (!submitFeedbackTimer) return
    clearTimeout(submitFeedbackTimer)
    submitFeedbackTimer = null
}

const clearAutoRefreshTimer = () => {
    if (!autoRefreshTimer) return
    clearInterval(autoRefreshTimer)
    autoRefreshTimer = null
}

const refreshTaskCounts = () => {
    if (isLoadingTaskCounts.value) return
    void fetchTaskCounts()
}

const ensureAutoRefresh = () => {
    if (activeTaskCount.value === 0) {
        clearAutoRefreshTimer()
        return
    }
    if (autoRefreshTimer) return
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

watch(isLoadingTaskCounts, (newVal, oldVal) => {
    if (oldVal === true && newVal === false) {
        lastRefreshedAt.value = new Date()
        relativeTimeNow.value = Date.now()
    }
})

onMounted(() => {
    init()
    relativeTimeTimer = setInterval(() => {
        relativeTimeNow.value = Date.now()
    }, 1000)
})

onUnmounted(() => {
    clearSubmitFeedbackTimer()
    clearAutoRefreshTimer()
    if (relativeTimeTimer) {
        clearInterval(relativeTimeTimer)
        relativeTimeTimer = null
    }
})

watch(activeTaskCount, () => {
    ensureAutoRefresh()
})

const openTaskModal = async () => {
    if (!userStore.isAdmin || isTaskActionButtonDisabled.value) return
    clearSubmitFeedbackTimer()
    submitFeedbackStatus.value = 'idle'
    clearSubmitError()
    await fetchPlugins()

    const submitted = await modal.open<boolean>(TaskSubmissionModal, {
        size: 'xl',
        bodyPadding: false,
        fitContent: false,
        props: {
            plugins: pluginList.value,
            loadProviders: async () => {
                await fetchProviders()
                return providerOptions.value
            },
            submitMetadataParse: async (payload: ScanTaskRequest) => {
                const submitOk = await startMetadataParseTask(
                    payload.providerType,
                    payload.providerId,
                )
                if (!submitOk) throw new Error(submitError.value || '提交任务失败')
            },
            submitTranscode: async (payload: TranscodeTaskRequest) => {
                const submitOk = await startTranscodeTask(payload)
                if (!submitOk) throw new Error(submitError.value || '提交任务失败')
            },
            submitPluginTask: async (taskType: string, params: Record<string, string>) => {
                const submitOk = await startPluginTask(taskType, params)
                if (!submitOk) throw new Error(submitError.value || '提交任务失败')
            },
        },
    })

    clearSubmitError()
    refreshTaskCounts()
    if (submitted) showSubmitFeedback()
}

const refreshAll = () => {
    refreshTaskCounts()
}

const isLogDrawerOpen = ref(false)
const drawerTaskType = ref<TaskType>('METADATA_PARSE')
const drawerStatuses = ref<TaskStatus[]>(['PENDING'])

const drawerTaskOptions = computed(() =>
    taskSummaryRows.value.map((row) => ({
        taskType: row.taskType as TaskType,
        taskName: row.taskName,
    })),
)

const drawerTaskName = computed(
    () =>
        drawerTaskOptions.value.find((option) => option.taskType === drawerTaskType.value)
            ?.taskName ?? drawerTaskType.value,
)

const openLogDrawer = (taskType: string, statuses: TaskStatus[]) => {
    drawerTaskType.value = taskType as TaskType
    drawerStatuses.value = [...statuses]
    isLogDrawerOpen.value = true
}

const drawerTitle = computed(() => drawerTaskName.value)
</script>

<template>
    <div class="pb-32 font-sans text-[#5A524A] selection:bg-[#C67C4E] selection:text-white">
        <DashboardTopBar />

        <div class="mx-auto w-full max-w-5xl px-4 pt-4 sm:px-6 sm:pt-6 lg:px-8">
            <div
                class="mb-8 flex flex-col gap-4 border-b border-[#EAE6DE] pb-4 sm:flex-row sm:items-end sm:justify-between"
            >
                <div>
                    <h2 class="mb-1 font-serif text-3xl text-[#2B221B]">任务管理</h2>
                    <p class="font-serif text-sm italic text-[#8A8A8A]">发起或管理后台任务队列</p>
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
        </div>

        <div class="mx-auto mt-10 w-full max-w-5xl px-4 sm:px-6 lg:px-8">
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
                        v-if="userStore.isAdmin"
                        type="button"
                        data-test="open-task-button"
                        class="relative mt-6 flex w-full items-center justify-center gap-2 overflow-hidden bg-[#C67C4E] px-4 py-3 text-sm uppercase tracking-[0.18em] text-[#F7F5F0] shadow-md transition-colors hover:bg-[#B46B3A] disabled:cursor-not-allowed"
                        :class="{
                            'opacity-60': isSubmitting && submitFeedbackStatus !== 'success',
                            'hover:bg-[#C67C4E]': submitFeedbackStatus === 'success',
                        }"
                        :disabled="isTaskActionButtonDisabled"
                        @click="openTaskModal"
                    >
                        <span
                            v-if="submitFeedbackStatus === 'success'"
                            data-test="submit-feedback-progress"
                            class="task-action-progress pointer-events-none absolute inset-y-0 left-0 bg-black/30"
                            :style="{ animationDuration: `${SUBMIT_FEEDBACK_DURATION_MS}ms` }"
                            aria-hidden="true"
                        />
                        <span class="relative z-10">{{ taskActionButtonLabel }}</span>
                        <component
                            :is="submitFeedbackStatus === 'success' ? CheckCircle2 : ArrowRight"
                            class="relative z-10 h-4 w-4"
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
                    <span v-if="lastRefreshedText" class="font-serif text-xs text-[#8A8A8A]">{{
                        lastRefreshedText
                    }}</span>
                </div>

                <div
                    v-if="isLoadingTaskCounts && taskCounts.length === 0"
                    class="flex items-center justify-center py-14 text-sm text-[#6B635B]"
                >
                    <Loader2 class="mr-2 h-4 w-4 animate-spin" />
                    加载任务状态...
                </div>

                <div v-else class="px-2 py-4 md:px-4 md:py-5">
                    <div class="grid grid-cols-2 gap-6 md:grid-cols-5">
                        <div
                            v-for="(item, index) in statusOverviewItems"
                            :key="item.key"
                            class="text-center md:px-4"
                            :class="
                                index < statusOverviewItems.length - 1
                                    ? 'md:border-r md:border-[#E8DFD2]'
                                    : 'hidden md:block'
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
                                    <p class="mt-1 font-serif text-xs italic text-[#9A9187]">
                                        {{ row.taskType }}
                                    </p>
                                </div>
                            </div>

                            <button
                                type="button"
                                class="flex items-end gap-3 text-left"
                                @click="
                                    openLogDrawer(row.taskType, [
                                        'PENDING',
                                        'RUNNING',
                                        'COMPLETED',
                                        'FAILED',
                                    ])
                                "
                            >
                                <span class="font-serif text-4xl text-[#E3D8CB]">
                                    {{ row.totalCount }}
                                </span>
                                <span
                                    class="pb-1 text-[10px] uppercase tracking-[0.28em] text-[#B29A84]"
                                >
                                    Total
                                </span>
                            </button>
                        </div>

                        <div class="mt-5 h-px bg-[#E8DFD2]"></div>

                        <div class="mt-6 grid gap-5 md:grid-cols-3">
                            <button
                                type="button"
                                class="space-y-2 rounded-sm text-left"
                                @click="openLogDrawer(row.taskType, ['COMPLETED'])"
                            >
                                <div
                                    class="flex items-center justify-between text-xs text-[#83796D]"
                                >
                                    <span class="text-[10px] uppercase tracking-[0.24em]">
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
                            </button>

                            <div class="space-y-2">
                                <div
                                    class="flex items-center justify-between gap-2 text-xs text-[#83796D]"
                                >
                                    <span class="text-[10px] uppercase tracking-[0.24em]">
                                        Active
                                    </span>
                                    <div class="flex items-center gap-2 font-mono text-[11px]">
                                        <button
                                            type="button"
                                            class="text-[#B86134]"
                                            :title="`执行中：${row.runningCount}`"
                                            @click="openLogDrawer(row.taskType, ['RUNNING'])"
                                        >
                                            {{ row.runningCount }}
                                        </button>
                                        <span class="text-[#C7BEB2]">/</span>
                                        <button
                                            type="button"
                                            class="text-[#4A4A4A]"
                                            :title="`待处理：${row.pendingCount}`"
                                            @click="openLogDrawer(row.taskType, ['PENDING'])"
                                        >
                                            {{ row.pendingCount }}
                                        </button>
                                    </div>
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

                            <button
                                type="button"
                                class="space-y-2 rounded-sm text-left"
                                @click="openLogDrawer(row.taskType, ['FAILED'])"
                            >
                                <div
                                    class="flex items-center justify-between text-xs text-[#83796D]"
                                >
                                    <span class="text-[10px] uppercase tracking-[0.24em]">
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
                            </button>
                        </div>
                    </div>
                </div>
            </div>
        </div>

        <SideDrawer v-model:open="isLogDrawerOpen" :title="drawerTitle" width="34rem">
            <TaskLogDrawerContent
                v-if="isLogDrawerOpen"
                v-model:task-type="drawerTaskType"
                v-model:statuses="drawerStatuses"
                :tasks="drawerTaskOptions"
                @reset-success="refreshTaskCounts"
            />
        </SideDrawer>
    </div>
</template>

<style scoped>
.task-action-progress {
    animation: task-action-progress-deplete linear forwards;
    will-change: width;
}

@keyframes task-action-progress-deplete {
    from {
        width: 100%;
    }
    to {
        width: 0%;
    }
}
</style>
