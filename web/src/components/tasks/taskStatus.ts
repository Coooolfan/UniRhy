import { computed, type ComputedRef } from 'vue'
import { useI18n } from 'vue-i18n'
import type { TaskStatus } from '@/__generated/model/enums/TaskStatus'

/** 状态筛选器与图例的展示顺序 */
export const STATUS_ORDER: readonly TaskStatus[] = [
    'PENDING',
    'RUNNING',
    'COMPLETED',
    'FAILED',
    'CANCELLED',
]

/** 状态圆点配色，任务树与图例共用 */
export const STATUS_DOT_CLASS: Record<TaskStatus, string> = {
    PENDING: 'bg-[#B8AFA3]',
    RUNNING: 'bg-[#B86134]',
    COMPLETED: 'bg-emerald-600',
    FAILED: 'bg-rose-500',
    CANCELLED: 'bg-[#D8CFC2]',
}

/** 当前语言下的状态文案 */
export const useTaskStatusLabels = (): ComputedRef<Record<TaskStatus, string>> => {
    const { t } = useI18n()
    return computed(() => ({
        PENDING: t('taskDetails.pending'),
        RUNNING: t('taskDetails.running'),
        COMPLETED: t('taskDetails.completed'),
        FAILED: t('taskDetails.failed'),
        CANCELLED: t('taskDetails.cancelled'),
    }))
}

/** 任务时间戳的统一展示格式；空值与非法值分别退化为占位符与原文 */
export const formatTaskTime = (value: string | undefined | null) => {
    if (!value) return '—'
    const date = new Date(value)
    if (Number.isNaN(date.getTime())) return value
    return date.toLocaleString('zh-CN', { hour12: false })
}
