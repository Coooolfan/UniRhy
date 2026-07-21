<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { api } from '@/ApiInstance'
import { resolveErrorMessage } from '@/i18n/errors'
import type { AsyncTaskDto } from '@/__generated/model/dto'
import type { TaskStatus } from '@/__generated/model/enums/TaskStatus'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/stores/user'
import { useModal } from '@/composables/useModal'
import { ChevronLeft, ChevronRight, Loader2, RotateCcw, XCircle } from 'lucide-vue-next'

const { t } = useI18n()

type TaskRow = AsyncTaskDto['TaskController/DEFAULT_TASK_FETCHER']

type TaskOption = {
    readonly taskKey: string
    readonly namespace: string
    readonly taskType: string
    readonly taskName: string
}

const props = defineProps<{
    taskKey: string
    statuses: ReadonlyArray<TaskStatus>
    tasks: ReadonlyArray<TaskOption>
    pageSize?: number
}>()

const emit = defineEmits<{
    resetSuccess: []
    'update:taskKey': [value: string]
    'update:statuses': [value: TaskStatus[]]
}>()

const STATUS_ORDER: readonly TaskStatus[] = [
    'PENDING',
    'RUNNING',
    'COMPLETED',
    'FAILED',
    'CANCELLED',
]

const activeOption = computed<TaskOption | undefined>(() =>
    props.tasks.find((option) => option.taskKey === props.taskKey),
)

const onTaskKeyChange = (event: Event) => {
    const target = event.target as HTMLSelectElement
    emit('update:taskKey', target.value)
}

const isStatusSelected = (s: TaskStatus) => props.statuses.includes(s)

const toggleStatus = (target: TaskStatus) => {
    const next = props.statuses.includes(target)
        ? props.statuses.filter((s) => s !== target)
        : [...props.statuses, target]
    emit('update:statuses', next)
}

const statusLabelMap = computed<Record<TaskStatus, string>>(() => ({
    PENDING: t('taskDetails.pending'),
    RUNNING: t('taskDetails.running'),
    COMPLETED: t('taskDetails.completed'),
    FAILED: t('taskDetails.failed'),
    CANCELLED: t('taskDetails.cancelled'),
}))

const STATUS_BADGE_CLASS = 'border-[#DFD6C4] bg-[#F1EBDD] text-[#5A524A]'

const userStore = useUserStore()
const modal = useModal()

const pageIndex = ref(0)
const rows = ref<ReadonlyArray<TaskRow>>([])
const totalPageCount = ref(0)
const totalRowCount = ref(0)
const loading = ref(false)
const error = ref('')
const patchingId = ref<number | null>(null)
const expandedIds = ref<Set<number>>(new Set())

const effectivePageSize = computed(() => props.pageSize ?? 20)

const canResetRow = (row: TaskRow) => userStore.isAdmin && row.status === 'FAILED'
const canCancelRow = (row: TaskRow) => userStore.isAdmin && row.status === 'PENDING'

const fetchTasks = async () => {
    const option = activeOption.value
    if (props.statuses.length === 0 || !option) {
        rows.value = []
        totalPageCount.value = 0
        totalRowCount.value = 0
        loading.value = false
        error.value = ''
        return
    }
    loading.value = true
    error.value = ''
    try {
        const page = await api.taskController.listTasks({
            namespace: option.namespace,
            taskType: option.taskType,
            statuses: [...props.statuses],
            pageIndex: pageIndex.value,
            pageSize: effectivePageSize.value,
        })
        rows.value = page.rows
        totalPageCount.value = page.totalPageCount
        totalRowCount.value = page.totalRowCount
    } catch (err) {
        error.value = resolveErrorMessage(err, 'errors.fallback.taskDetailsLoad')
        rows.value = []
        totalPageCount.value = 0
        totalRowCount.value = 0
    } finally {
        loading.value = false
    }
}

watch(
    () => [props.taskKey, [...props.statuses].sort().join(',')] as const,
    () => {
        pageIndex.value = 0
        expandedIds.value = new Set()
        void fetchTasks()
    },
    { immediate: true },
)

watch(pageIndex, () => {
    expandedIds.value = new Set()
    void fetchTasks()
})

const goPrev = () => {
    if (pageIndex.value <= 0 || loading.value) return
    pageIndex.value -= 1
}

const goNext = () => {
    if (pageIndex.value + 1 >= totalPageCount.value || loading.value) return
    pageIndex.value += 1
}

const toggleExpand = (id: number) => {
    const next = new Set(expandedIds.value)
    if (next.has(id)) {
        next.delete(id)
    } else {
        next.add(id)
    }
    expandedIds.value = next
}

const formatTime = (value: string | undefined | null) => {
    if (!value) return '—'
    const date = new Date(value)
    if (Number.isNaN(date.getTime())) return value
    return date.toLocaleString('zh-CN', { hour12: false })
}

const formatPayload = (payload: unknown) => {
    try {
        return JSON.stringify(payload, null, 2)
    } catch {
        return String(payload)
    }
}

const patchTaskStatus = async (row: TaskRow, target: TaskStatus, fallbackKey: string) => {
    if (patchingId.value !== null) return
    patchingId.value = row.id
    error.value = ''
    try {
        await api.taskController.patchTask({ id: row.id, body: { status: target } })
        emit('resetSuccess')
        await fetchTasks()
    } catch (err) {
        error.value = resolveErrorMessage(err, fallbackKey)
    } finally {
        patchingId.value = null
    }
}

const resetTask = async (row: TaskRow) => {
    if (!canResetRow(row)) return
    const confirmed = await modal.confirm({
        title: t('taskDetails.resetTitle'),
        content: t('taskDetails.resetConfirm', {
            id: row.id,
            status: statusLabelMap.value[row.status],
        }),
        confirmText: t('taskDetails.reset'),
        cancelText: t('common.cancel'),
        tone: 'danger',
    })
    if (!confirmed) return
    await patchTaskStatus(row, 'PENDING', 'errors.fallback.taskStatusPatch')
}

const cancelTask = async (row: TaskRow) => {
    if (!canCancelRow(row)) return
    const confirmed = await modal.confirm({
        title: t('taskDetails.cancelTitle'),
        content: t('taskDetails.cancelConfirm', { id: row.id }),
        confirmText: t('taskDetails.cancel'),
        cancelText: t('common.cancel'),
        tone: 'danger',
    })
    if (!confirmed) return
    await patchTaskStatus(row, 'CANCELLED', 'errors.fallback.taskStatusPatch')
}
</script>

<template>
    <div class="flex h-full flex-col">
        <div class="space-y-3 border-b border-[#EAE6DE] px-4 py-4 sm:px-6">
            <label class="block">
                <span class="mb-1.5 block text-[10px] uppercase tracking-[0.24em] text-[#8A8177]">
                    {{ t('taskDetails.taskType') }}
                </span>
                <div class="relative">
                    <select
                        :value="taskKey"
                        class="w-full appearance-none border border-[#EAE6DE] bg-[#F8F5EE] px-3 py-2 pr-8 text-sm text-[#2B221B] outline-none transition-colors focus:border-[#B86134]"
                        @change="onTaskKeyChange"
                    >
                        <option
                            v-for="option in tasks"
                            :key="option.taskKey"
                            :value="option.taskKey"
                        >
                            {{ option.taskName }}
                        </option>
                    </select>
                    <ChevronRight
                        class="pointer-events-none absolute top-1/2 right-2 h-3 w-3 -translate-y-1/2 rotate-90 text-[#8A8177]"
                    />
                </div>
            </label>
            <div class="flex items-center gap-1">
                <button
                    v-for="s in STATUS_ORDER"
                    :key="s"
                    type="button"
                    class="flex-1 border px-2 py-1.5 text-[10px] font-semibold uppercase tracking-[0.2em]"
                    :class="
                        isStatusSelected(s)
                            ? `${STATUS_BADGE_CLASS} ring-1 ring-inset ring-[#8A7F6D]/40`
                            : 'border-[#EAE6DE] bg-white/40 font-normal text-[#B8AFA3] opacity-70'
                    "
                    @click="toggleStatus(s)"
                >
                    {{ statusLabelMap[s] }}
                </button>
            </div>
            <div class="text-xs text-[#8A8177]">
                {{ t('taskDetails.totalCount', { count: totalRowCount }) }}
            </div>
        </div>

        <div class="min-h-0 flex-1 overflow-y-auto px-4 py-4 sm:px-6">
            <div
                v-if="error"
                class="mb-4 border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700"
            >
                {{ error }}
            </div>

            <div
                v-if="loading && rows.length === 0"
                class="flex items-center justify-center py-12 text-sm text-[#6B635B]"
            >
                <Loader2 class="mr-2 h-4 w-4 animate-spin" />
                {{ t('taskDetails.loading') }}
            </div>

            <div
                v-else-if="rows.length === 0"
                class="flex items-center justify-center py-12 text-sm text-[#8A8177]"
            >
                {{ t('taskDetails.noRecords') }}
            </div>

            <ul v-else class="space-y-3">
                <li
                    v-for="row in rows"
                    :key="row.id"
                    class="border border-[#EAE6DE] bg-white/60 p-3 sm:p-4"
                >
                    <div class="flex items-start justify-between gap-2 sm:gap-3">
                        <div class="min-w-0 flex-1">
                            <div
                                class="flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-[#8A8177]"
                            >
                                <span class="font-mono text-[#2B221B]">#{{ row.id }}</span>
                                <span
                                    class="inline-flex shrink-0 items-center whitespace-nowrap border px-1.5 py-0.5 text-[10px] uppercase tracking-[0.2em]"
                                    :class="STATUS_BADGE_CLASS"
                                >
                                    {{ statusLabelMap[row.status] }}
                                </span>
                                <span class="hidden sm:inline">·</span>
                                <span class="basis-full sm:basis-auto">
                                    {{
                                        t('taskDetails.createdAt', {
                                            time: formatTime(row.createdAt),
                                        })
                                    }}
                                </span>
                            </div>
                            <div
                                class="mt-1 grid grid-cols-1 gap-x-3 text-[11px] text-[#8A8177] sm:grid-cols-2"
                            >
                                <span>{{
                                    t('taskDetails.startedAt', { time: formatTime(row.startedAt) })
                                }}</span>
                                <span>{{
                                    t('taskDetails.completedAt', {
                                        time: formatTime(row.completedAt),
                                    })
                                }}</span>
                            </div>
                            <div
                                v-if="row.completedReason"
                                class="mt-2 text-xs leading-relaxed text-[#5A524A]"
                            >
                                {{ row.completedReason }}
                            </div>
                        </div>
                        <div class="flex shrink-0 flex-col items-end gap-2">
                            <button
                                v-if="canResetRow(row)"
                                type="button"
                                class="inline-flex items-center gap-1 border border-[#C67C4E] px-3 py-1.5 text-[11px] uppercase tracking-[0.18em] text-[#C67C4E] transition-colors hover:bg-[#C67C4E] hover:text-white disabled:cursor-not-allowed disabled:opacity-60"
                                :disabled="patchingId !== null"
                                @click="resetTask(row)"
                            >
                                <Loader2
                                    v-if="patchingId === row.id"
                                    class="h-3 w-3 animate-spin"
                                />
                                <RotateCcw v-else class="h-3 w-3" />
                                <span>{{ t('taskDetails.reset') }}</span>
                            </button>
                            <button
                                v-if="canCancelRow(row)"
                                type="button"
                                class="inline-flex items-center gap-1 border border-[#B8AFA3] px-3 py-1.5 text-[11px] uppercase tracking-[0.18em] text-[#8A8177] transition-colors hover:border-rose-400 hover:text-rose-500 disabled:cursor-not-allowed disabled:opacity-60"
                                :disabled="patchingId !== null"
                                @click="cancelTask(row)"
                            >
                                <Loader2
                                    v-if="patchingId === row.id"
                                    class="h-3 w-3 animate-spin"
                                />
                                <XCircle v-else class="h-3 w-3" />
                                <span>{{ t('taskDetails.cancel') }}</span>
                            </button>
                        </div>
                    </div>

                    <button
                        type="button"
                        class="mt-3 text-[11px] uppercase tracking-[0.22em] text-[#8A8177] transition-colors hover:text-[#B86134]"
                        @click="toggleExpand(row.id)"
                    >
                        {{
                            expandedIds.has(row.id)
                                ? t('taskDetails.hideParams')
                                : t('taskDetails.viewParams')
                        }}
                    </button>
                    <pre
                        v-if="expandedIds.has(row.id)"
                        class="mt-2 max-h-64 overflow-auto border border-[#EAE6DE] bg-[#F8F5EE] p-3 font-mono text-[11px] leading-relaxed text-[#2B221B]"
                        >{{ formatPayload(row.payload) }}</pre
                    >
                </li>
            </ul>
        </div>

        <footer
            class="flex items-center justify-between border-t border-[#EAE6DE] px-4 py-3 text-xs text-[#6B635B] sm:px-6"
        >
            <span>
                {{
                    t('taskDetails.pageInfo', {
                        current: totalPageCount === 0 ? 0 : pageIndex + 1,
                        total: totalPageCount,
                    })
                }}
            </span>
            <div class="flex items-center gap-2">
                <button
                    type="button"
                    class="inline-flex items-center gap-1 border border-[#EAE6DE] px-3 py-1.5 text-[11px] uppercase tracking-[0.18em] text-[#5A524A] transition-colors hover:border-[#B86134] hover:text-[#B86134] disabled:cursor-not-allowed disabled:opacity-40"
                    :disabled="pageIndex <= 0 || loading"
                    @click="goPrev"
                >
                    <ChevronLeft class="h-3 w-3" />
                    {{ t('taskDetails.prevPage') }}
                </button>
                <button
                    type="button"
                    class="inline-flex items-center gap-1 border border-[#EAE6DE] px-3 py-1.5 text-[11px] uppercase tracking-[0.18em] text-[#5A524A] transition-colors hover:border-[#B86134] hover:text-[#B86134] disabled:cursor-not-allowed disabled:opacity-40"
                    :disabled="pageIndex + 1 >= totalPageCount || loading"
                    @click="goNext"
                >
                    {{ t('taskDetails.nextPage') }}
                    <ChevronRight class="h-3 w-3" />
                </button>
            </div>
        </footer>
    </div>
</template>
