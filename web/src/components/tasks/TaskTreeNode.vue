<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { Handle, Position } from '@vue-flow/core'
import { STATUS_DOT_CLASS, useTaskStatusLabels } from './taskStatus'
import type { TaskStatus } from '@/__generated/model/enums/TaskStatus'

/** Vue Flow 自定义任务节点的 data 载荷 */
export type TaskFlowNodeData = {
    taskId: number
    status: TaskStatus
    startedAt?: string
    completedAt?: string
    childCount: number
}

const props = defineProps<{
    data: TaskFlowNodeData
    selected?: boolean
}>()

const { t } = useI18n()
const statusLabelMap = useTaskStatusLabels()

const STATUS_CARD_CLASS: Record<TaskStatus, string> = {
    PENDING: 'border-[#DFD6C4] bg-[#FFFCF5]',
    RUNNING: 'border-[#C67C4E]/60 bg-[#C67C4E]/5',
    COMPLETED: 'border-[#EAE6DE] bg-white/80',
    FAILED: 'border-rose-300 bg-rose-50/60',
    CANCELLED: 'border-[#EAE6DE] bg-[#F8F5EE]/70',
}

const durationText = computed(() => {
    if (!props.data.startedAt) return ''
    const start = new Date(props.data.startedAt).getTime()
    const end = props.data.completedAt ? new Date(props.data.completedAt).getTime() : Date.now()
    if (Number.isNaN(start) || Number.isNaN(end) || end < start) return ''
    const sec = Math.round((end - start) / 1000)
    if (sec < 60) return `${sec}s`
    const min = Math.floor(sec / 60)
    if (min < 60) return `${min}m ${sec % 60}s`
    return `${Math.floor(min / 60)}h ${min % 60}m`
})
</script>

<template>
    <div
        class="w-52 border px-3 py-2 text-left shadow-sm transition-colors"
        :class="[
            STATUS_CARD_CLASS[data.status],
            selected ? 'border-[#C67C4E] ring-2 ring-[#C67C4E]/60' : '',
        ]"
    >
        <Handle type="target" :position="Position.Left" class="!opacity-0" />
        <Handle type="source" :position="Position.Right" class="!opacity-0" />

        <div class="flex items-center">
            <span class="font-mono text-xs font-semibold text-[#2B221B]">#{{ data.taskId }}</span>
        </div>
        <div class="mt-1.5 flex items-center gap-1.5">
            <span
                class="h-1.5 w-1.5 shrink-0 rounded-full"
                :class="STATUS_DOT_CLASS[data.status]"
            ></span>
            <span class="text-[11px] text-[#5A524A]">{{ statusLabelMap[data.status] }}</span>
            <span v-if="durationText" class="ml-auto font-mono text-[10px] text-[#B29A84]">
                {{ durationText }}
            </span>
        </div>
        <div
            v-if="data.childCount > 0"
            class="mt-1 text-[10px] uppercase tracking-[0.14em] text-[#B29A84]"
        >
            {{ t('taskTree.childCount', { count: data.childCount }) }}
        </div>
    </div>
</template>
