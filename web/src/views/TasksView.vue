<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import DashboardTopBar from '@/components/dashboard/DashboardTopBar.vue'
import DecorativeLabel from '@/components/common/DecorativeLabel.vue'
import { useModal } from '@/composables/useModal'
import TaskSubmissionModal from '@/components/tasks/TaskSubmissionModal.vue'
import SideDrawer from '@/components/SideDrawer.vue'
import TaskDrawerContent from '@/components/tasks/TaskDrawerContent.vue'
import type { TaskStatus } from '@/__generated/model/enums/TaskStatus'
import { taskKeyOf, useTaskManagement } from '@/composables/useTaskManagement'
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

const { t } = useI18n()

type SubmitFeedbackStatus = 'idle' | 'success'
type SummaryTone = 'idle' | 'working' | 'failed' | 'done'

type TaskSummaryRow = {
    taskKey: string
    namespace: string
    taskType: string
    taskName: string
    activeCount: number
    completedCount: number
    failedCount: number
    cancelledCount: number
    totalCount: number
    tone: SummaryTone
}

const SUBMIT_FEEDBACK_DURATION_MS = 2000
const TASK_AUTO_REFRESH_INTERVAL_MS = 2000

const {
    taskStatistics,
    taskDefinitions,
    providerOptions,
    isLoadingTaskStatistics,
    isSubmitting,
    taskError,
    submitError,
    fetchTaskStatistics,
    fetchProviders,
    fetchTaskDefinitions,
    submitTask,
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
    if (diffSec < 60) return t('tasks.refreshedSecondsAgo', { seconds: diffSec })
    const diffMin = Math.floor(diffSec / 60)
    if (diffMin < 60) return t('tasks.refreshedMinutesAgo', { minutes: diffMin })
    return t('tasks.refreshedHoursAgo', { hours: Math.floor(diffMin / 60) })
})

const taskActionButtonLabel = computed(() =>
    submitFeedbackStatus.value === 'success' ? t('tasks.taskSubmitted') : t('tasks.newTask'),
)

const isTaskActionButtonDisabled = computed(
    () => !userStore.isAdmin || isSubmitting.value || submitFeedbackStatus.value === 'success',
)

const sumTaskCounts = (selector: (row: (typeof taskStatistics.value)[number]) => number) =>
    taskStatistics.value.reduce((sum, row) => sum + selector(row), 0)

const activeTaskCount = computed(() => sumTaskCounts((row) => row.tasks.active))
const completedTaskCount = computed(() => sumTaskCounts((row) => row.tasks.completed))
const failedTaskCount = computed(() => sumTaskCounts((row) => row.tasks.failed))
const cancelledTaskCount = computed(() => sumTaskCounts((row) => row.tasks.cancelled))
const totalTaskCount = computed(() => sumTaskCounts((row) => row.tasks.total))

const statusOverviewItems = computed(() => [
    {
        key: 'COMPLETED',
        count: completedTaskCount.value,
        label: t('tasks.completed'),
        eyebrow: 'Completed',
        valueClass: 'text-[#2B221B]',
        eyebrowClass: 'text-[#8A8177]',
    },
    {
        key: 'ACTIVE',
        count: activeTaskCount.value,
        label: t('tasks.active'),
        eyebrow: 'Active',
        valueClass: 'text-[#2B221B]',
        eyebrowClass: 'text-[#B86134]/70',
    },
    {
        key: 'FAILED',
        count: failedTaskCount.value,
        label: t('tasks.failed'),
        eyebrow: 'Failed',
        valueClass: 'text-[#2B221B]',
        eyebrowClass: 'text-[#8A8177]',
    },
    {
        key: 'CANCELLED',
        count: cancelledTaskCount.value,
        label: t('tasks.cancelled'),
        eyebrow: 'Cancelled',
        valueClass: 'text-[#2B221B]',
        eyebrowClass: 'text-[#8A8177]',
    },
    {
        key: 'TOTAL',
        count: totalTaskCount.value,
        label: t('tasks.totalTasks'),
        eyebrow: 'Total',
        valueClass: 'text-[#B86134]',
        eyebrowClass: 'text-[#B86134]/70',
    },
])

// 显示统计中出现的所有 TaskKey，内建任务优先
const taskSummaryRows = computed<TaskSummaryRow[]>(() => {
    const builtinFirst = [...taskStatistics.value].sort((a, b) => {
        const aBuiltin = a.namespace === 'app.unirhy.built-in' ? 0 : 1
        const bBuiltin = b.namespace === 'app.unirhy.built-in' ? 0 : 1
        if (aBuiltin !== bBuiltin) return aBuiltin - bBuiltin
        return taskKeyOf(a.namespace, a.taskType).localeCompare(taskKeyOf(b.namespace, b.taskType))
    })
    return builtinFirst.map((row) => {
        const activeCount = row.tasks.active
        const completedCount = row.tasks.completed
        const failedCount = row.tasks.failed
        const cancelledCount = row.tasks.cancelled

        let tone: SummaryTone = 'idle'
        if (failedCount > 0) tone = 'failed'
        else if (activeCount > 0) tone = 'working'
        else if (completedCount > 0) tone = 'done'

        return {
            taskKey: taskKeyOf(row.namespace, row.taskType),
            namespace: row.namespace,
            taskType: row.taskType,
            taskName: resolveTaskLabel(row.namespace, row.taskType),
            activeCount,
            completedCount,
            failedCount,
            cancelledCount,
            totalCount: row.tasks.total,
            tone,
        }
    })
})

const queueSummaryText = computed(() => {
    if (activeTaskCount.value === 0) {
        return t('tasks.noTasksHint')
    }
    return t('tasks.tasksActiveCount', { count: activeTaskCount.value })
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

const refreshTaskStatistics = () => {
    if (isLoadingTaskStatistics.value) return
    void fetchTaskStatistics()
}

const ensureAutoRefresh = () => {
    if (activeTaskCount.value === 0) {
        clearAutoRefreshTimer()
        return
    }
    if (autoRefreshTimer) return
    autoRefreshTimer = setInterval(() => {
        refreshTaskStatistics()
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

watch(isLoadingTaskStatistics, (newVal, oldVal) => {
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
    await fetchTaskDefinitions()

    const submitted = await modal.open<boolean>(TaskSubmissionModal, {
        size: 'xl',
        bodyPadding: false,
        fitContent: false,
        props: {
            definitions: taskDefinitions.value,
            loadProviders: async () => {
                await fetchProviders()
                return providerOptions.value
            },
            submitTask: async (
                namespace: string,
                taskType: string,
                params: Record<string, unknown>,
            ) => {
                const submitOk = await submitTask(namespace, taskType, params)
                if (!submitOk) throw new Error(submitError.value || t('tasksView.submitFailed'))
            },
        },
    })

    clearSubmitError()
    refreshTaskStatistics()
    if (submitted) showSubmitFeedback()
}

const refreshAll = () => {
    refreshTaskStatistics()
}

const isTaskDrawerOpen = ref(false)
const drawerTaskKey = ref<string>(taskKeyOf('app.unirhy.built-in', 'METADATA_PARSE'))
const drawerStatuses = ref<TaskStatus[]>(['PENDING'])

const drawerTaskOptions = computed(() =>
    taskSummaryRows.value.map((row) => ({
        taskKey: row.taskKey,
        namespace: row.namespace,
        taskType: row.taskType,
        taskName: row.taskName,
    })),
)

const drawerTaskName = computed(
    () =>
        drawerTaskOptions.value.find((option) => option.taskKey === drawerTaskKey.value)
            ?.taskName ?? drawerTaskKey.value,
)

const openTaskDrawer = (taskKey: string, statuses: TaskStatus[]) => {
    drawerTaskKey.value = taskKey
    drawerStatuses.value = [...statuses]
    isTaskDrawerOpen.value = true
}

const drawerTitle = computed(() => drawerTaskName.value)
</script>

<template>
    <div class="pb-32 font-sans text-[#5A524A] selection:bg-[#C67C4E] selection:text-white">
        <DashboardTopBar />

        <div class="mx-auto w-full max-w-5xl px-6 pt-4 sm:pt-6 lg:px-8">
            <div
                class="flex items-start justify-between gap-3 border-b border-[#EAE6DE] pb-3 sm:mb-8 sm:items-end sm:pb-4"
            >
                <div>
                    <h2 class="mb-1 font-serif text-3xl text-[#2B221B]">
                        {{ t('tasksView.taskManagement') }}
                    </h2>
                    <p class="font-serif text-sm italic text-[#8A8A8A]">
                        {{ t('tasksView.subtitle') }}
                    </p>
                </div>
                <button
                    class="text-[#8A8A8A] transition-colors hover:text-[#C67C4E] disabled:opacity-50"
                    :disabled="isLoadingTaskStatistics"
                    :title="t('tasksView.refreshStatus')"
                    @click="refreshAll"
                >
                    <RefreshCw
                        class="h-5 w-5"
                        :class="{ 'animate-spin': isLoadingTaskStatistics }"
                    />
                </button>
            </div>
        </div>

        <div class="mx-auto mt-6 w-full max-w-5xl px-6 sm:mt-10 lg:px-8">
            <div
                v-if="taskError"
                class="mb-6 flex items-center border border-rose-200 bg-rose-50 p-4 text-sm text-rose-700"
            >
                <AlertCircle class="mr-2 h-4 w-4 shrink-0" />
                <span>{{ taskError }}</span>
            </div>

            <div class="mb-7 grid grid-cols-1 gap-3 sm:mb-10 sm:gap-8 lg:grid-cols-3">
                <div
                    class="border border-[#EAE6DE] bg-[#FFFCF5] p-4 shadow-sm sm:p-6 lg:col-span-1"
                >
                    <h3 class="font-serif text-2xl text-[#2B221B]">
                        {{ t('tasksView.asyncTask') }}
                    </h3>
                    <p class="mt-2 text-sm leading-relaxed text-[#6B635B]">
                        {{ t('tasksView.asyncTaskDesc') }}
                    </p>

                    <button
                        v-if="userStore.isAdmin"
                        type="button"
                        data-test="open-task-button"
                        class="relative mt-3 flex w-full items-center justify-center gap-2 overflow-hidden bg-[#C67C4E] px-4 py-2.5 text-sm uppercase tracking-[0.18em] text-[#F7F5F0] shadow-md transition-colors hover:bg-[#B46B3A] disabled:cursor-not-allowed sm:mt-6 sm:py-3"
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
                    class="relative min-h-28 overflow-hidden border border-[#EAE6DE] bg-gradient-to-br from-[#F8F5EE] to-white p-4 shadow-sm sm:min-h-[196px] sm:p-6 lg:col-span-2"
                >
                    <div class="absolute top-0 right-0 p-4 opacity-5 sm:p-8">
                        <FileMusic class="h-24 w-24 sm:h-32 sm:w-32" />
                    </div>

                    <div class="relative z-10 sm:pt-3">
                        <div
                            v-if="isLoadingTaskStatistics"
                            class="mb-2 flex items-center text-[#C67C4E]"
                        >
                            <Loader2 class="mr-2 h-5 w-5 animate-spin" />
                            <span class="text-lg font-medium">{{
                                t('tasks.syncingTaskStats')
                            }}</span>
                        </div>
                        <div
                            v-else-if="activeTaskCount > 0"
                            class="mb-2 flex items-center text-[#C67C4E]"
                        >
                            <RefreshCw class="mr-2 h-5 w-5" />
                            <span class="text-lg font-medium">
                                {{ t('tasks.tasksPendingOrRunning', { count: activeTaskCount }) }}
                            </span>
                        </div>
                        <div v-else class="mb-2 flex items-center text-emerald-600">
                            <ServerCog class="mr-2 h-5 w-5" />
                            <span class="text-lg font-medium">{{ t('tasks.taskQueueIdle') }}</span>
                        </div>

                        <p class="mt-2 max-w-xl text-sm leading-relaxed text-[#6B635B]">
                            {{ queueSummaryText }}
                        </p>
                    </div>
                </div>
            </div>

            <div class="mb-9 sm:mb-16">
                <div class="mb-4 flex items-center justify-between px-1 sm:mb-8">
                    <h3 class="text-lg font-medium text-[#2B221B]">
                        {{ t('tasks.statusOverview') }}
                    </h3>
                    <span v-if="lastRefreshedText" class="font-serif text-xs text-[#8A8A8A]">{{
                        lastRefreshedText
                    }}</span>
                </div>

                <div
                    v-if="isLoadingTaskStatistics && taskStatistics.length === 0"
                    class="flex items-center justify-center py-14 text-sm text-[#6B635B]"
                >
                    <Loader2 class="mr-2 h-4 w-4 animate-spin" />
                    {{ t('tasks.loadingTaskStatus') }}
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
                                <DecorativeLabel>{{ item.eyebrow }}</DecorativeLabel>
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
                    <h3 class="text-lg font-medium text-[#2B221B]">
                        {{ t('tasksView.taskTypeDistribution') }}
                    </h3>
                    <BarChart3 class="h-4 w-4 text-[#8A8A8A]" />
                </div>

                <div class="space-y-12">
                    <div v-for="(row, index) in taskSummaryRows" :key="row.taskKey" class="group">
                        <div class="flex items-end justify-between gap-3">
                            <div class="flex min-w-0 items-center gap-3 sm:gap-4">
                                <span class="shrink-0 font-mono text-sm text-[#BEB1A3]">
                                    {{ String(index + 1).padStart(2, '0') }}
                                </span>
                                <div class="min-w-0">
                                    <div class="flex flex-wrap items-center gap-3">
                                        <h4
                                            class="truncate font-serif text-xl text-[#2B221B] transition-colors duration-500 group-hover:text-[#B86134] sm:text-2xl"
                                        >
                                            {{ row.taskName }}
                                        </h4>
                                    </div>
                                    <p class="mt-1 font-serif text-xs italic text-[#9A9187]">
                                        {{ row.taskKey }}
                                    </p>
                                </div>
                            </div>

                            <button
                                type="button"
                                class="flex shrink-0 items-end gap-2 text-left sm:gap-3"
                                @click="
                                    openTaskDrawer(row.taskKey, [
                                        'PENDING',
                                        'RUNNING',
                                        'COMPLETED',
                                        'FAILED',
                                        'CANCELLED',
                                    ])
                                "
                            >
                                <span class="whitespace-nowrap font-serif text-4xl text-[#E3D8CB]">
                                    {{ row.totalCount }}
                                </span>
                                <span
                                    class="pb-1 text-[10px] uppercase tracking-[0.28em] text-[#B29A84]"
                                >
                                    <DecorativeLabel>Total</DecorativeLabel>
                                </span>
                            </button>
                        </div>

                        <div class="mt-5 h-px bg-[#E8DFD2]"></div>

                        <div class="mt-6 grid gap-5 md:grid-cols-3">
                            <button
                                type="button"
                                class="space-y-2 rounded-sm text-left"
                                @click="openTaskDrawer(row.taskKey, ['COMPLETED'])"
                            >
                                <div
                                    class="flex items-center justify-between text-xs text-[#83796D]"
                                >
                                    <span class="text-[10px] uppercase tracking-[0.24em]">
                                        <DecorativeLabel>Completed</DecorativeLabel>
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

                            <button
                                type="button"
                                class="space-y-2 rounded-sm text-left"
                                @click="openTaskDrawer(row.taskKey, ['PENDING', 'RUNNING'])"
                            >
                                <div
                                    class="flex items-center justify-between text-xs text-[#83796D]"
                                >
                                    <span class="text-[10px] uppercase tracking-[0.24em]">
                                        <DecorativeLabel>Active</DecorativeLabel>
                                    </span>
                                    <span class="font-mono text-[11px] text-[#B86134]">
                                        {{ row.activeCount }}
                                    </span>
                                </div>
                                <div
                                    class="h-1.5 w-full overflow-hidden rounded-full bg-[#E8DFD2]/60"
                                >
                                    <div
                                        class="h-full bg-[#B86134] transition-[width] duration-700"
                                        :style="{
                                            width: progressWidth(row.activeCount, row.totalCount),
                                        }"
                                    />
                                </div>
                            </button>

                            <button
                                type="button"
                                class="space-y-2 rounded-sm text-left"
                                @click="openTaskDrawer(row.taskKey, ['FAILED'])"
                            >
                                <div
                                    class="flex items-center justify-between text-xs text-[#83796D]"
                                >
                                    <span class="text-[10px] uppercase tracking-[0.24em]">
                                        <DecorativeLabel>Failed</DecorativeLabel>
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

        <SideDrawer
            v-model:open="isTaskDrawerOpen"
            :title="drawerTitle"
            width="34rem"
            max-width="calc(100vw - 2rem)"
        >
            <TaskDrawerContent
                v-if="isTaskDrawerOpen"
                v-model:task-key="drawerTaskKey"
                v-model:statuses="drawerStatuses"
                :tasks="drawerTaskOptions"
                @reset-success="refreshTaskStatistics"
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
