<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { VueFlow, type Edge, type GraphNode, type Node, type VueFlowStore } from '@vue-flow/core'
import { Controls } from '@vue-flow/controls'
import { MiniMap } from '@vue-flow/minimap'
import { Background } from '@vue-flow/background'
import { api } from '@/ApiInstance'
import { resolveErrorMessage } from '@/i18n/errors'
import type { AsyncTaskDto } from '@/__generated/model/dto'
import type { TaskStatus } from '@/__generated/model/enums/TaskStatus'
import { ListTree, Loader2, RefreshCw } from 'lucide-vue-next'
import TaskTreeNode, { type TaskFlowNodeData } from './TaskTreeNode.vue'

import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import '@vue-flow/controls/dist/style.css'
import '@vue-flow/minimap/dist/style.css'

type TreeNode = AsyncTaskDto['TaskController/TASK_TREE_FETCHER']

const props = defineProps<{
    /** 列表中选中的任务 id；面板会解析到其所在树的根并展示整棵树 */
    taskId: number | null
    selectedId?: number | null
}>()

const { t } = useI18n()

const root = ref<TreeNode | null>(null)
const loading = ref(false)
const error = ref('')

// ── 数据 ─────────────────────────────────────────────
const STATUS_ORDER: readonly TaskStatus[] = [
    'PENDING',
    'RUNNING',
    'COMPLETED',
    'FAILED',
    'CANCELLED',
]

const statusLabelMap = computed<Record<TaskStatus, string>>(() => ({
    PENDING: t('taskDetails.pending'),
    RUNNING: t('taskDetails.running'),
    COMPLETED: t('taskDetails.completed'),
    FAILED: t('taskDetails.failed'),
    CANCELLED: t('taskDetails.cancelled'),
}))

const STATUS_DOT_CLASS: Record<TaskStatus, string> = {
    PENDING: 'bg-[#B8AFA3]',
    RUNNING: 'bg-[#B86134]',
    COMPLETED: 'bg-emerald-600',
    FAILED: 'bg-rose-500',
    CANCELLED: 'bg-[#D8CFC2]',
}

/** 递归统计整棵树的节点数与各状态数量 */
const treeStats = computed(() => {
    const counts: Record<TaskStatus, number> = {
        PENDING: 0,
        RUNNING: 0,
        COMPLETED: 0,
        FAILED: 0,
        CANCELLED: 0,
    }
    let total = 0
    const walk = (node: TreeNode) => {
        total += 1
        counts[node.status] += 1
        for (const child of node.childTasks ?? []) walk(child)
    }
    if (root.value) walk(root.value)
    return { total, counts }
})

const formatTime = (value: string | undefined | null) => {
    if (!value) return '—'
    const date = new Date(value)
    if (Number.isNaN(date.getTime())) return value
    return date.toLocaleString('zh-CN', { hour12: false })
}

// ── 布局：经典 tidy 树（叶子递增 y，父节点取子节点 y 中心）──
const NODE_WIDTH = 208 // w-52
const NODE_HEIGHT = 84
const GAP_X = 64
const GAP_Y = 16

const flowNodes = ref<Node<TaskFlowNodeData>[]>([])
const flowEdges = ref<Edge[]>([])

const buildFlow = (rootNode: TreeNode) => {
    const nodes: Node<TaskFlowNodeData>[] = []
    const edges: Edge[] = []
    let nextLeafY = 0

    const layout = (node: TreeNode, depth: number): number => {
        const children = [...(node.childTasks ?? [])].sort((a, b) => a.id - b.id)
        let centerY: number
        if (children.length === 0) {
            centerY = nextLeafY
            nextLeafY += NODE_HEIGHT + GAP_Y
        } else {
            const childYs = children.map((child) => layout(child, depth + 1))
            const firstY = childYs[0]
            const lastY = childYs.length > 1 ? (childYs.pop() as number) : firstY
            centerY = (firstY + lastY) / 2
        }

        nodes.push({
            id: String(node.id),
            type: 'task',
            position: { x: depth * (NODE_WIDTH + GAP_X), y: centerY },
            data: {
                taskId: node.id,
                action: node.action,
                status: node.status,
                startedAt: node.startedAt,
                completedAt: node.completedAt,
                childCount: (node.childTasks ?? []).length,
            },
        })
        for (const child of children) {
            edges.push({
                id: `e${node.id}-${child.id}`,
                source: String(node.id),
                target: String(child.id),
                type: 'bezier',
                style: { stroke: '#E0D5C4', strokeWidth: 1.5 },
            })
        }
        return centerY
    }

    layout(rootNode, 0)
    flowNodes.value = nodes
    flowEdges.value = edges
}

// ── Vue Flow 实例与视图控制 ──────────────────────────
const flowStore = ref<VueFlowStore | null>(null)
const activeNodeId = ref<number | null>(null)

const onFlowInit = (store: VueFlowStore) => {
    flowStore.value = store
    void nextTick().then(() => {
        store.fitView({ padding: 0.15, maxZoom: 1 })
    })
}

// 节点点击时同步高亮（点击节点卡片 → 高亮）
const onNodeClick = ({ node }: { node: GraphNode<TaskFlowNodeData> }) => {
    activeNodeId.value = node.data.taskId
}

const nodeClass = (node: GraphNode<TaskFlowNodeData>) =>
    node.data.taskId === activeNodeId.value ? 'task-flow-node--active' : ''

const fetchTree = async () => {
    if (props.taskId === null) {
        root.value = null
        flowNodes.value = []
        flowEdges.value = []
        error.value = ''
        return
    }
    loading.value = true
    error.value = ''
    try {
        // 向上回溯到根任务，再一次性取整棵树
        let cursor = props.taskId
        for (;;) {
            const detail = await api.taskController.getTask({ id: cursor })
            const parentId = detail.task.parentId
            if (parentId === undefined || parentId === null) break
            cursor = parentId
        }
        const tree = await api.taskController.getTaskTree({ id: cursor })
        root.value = tree
        buildFlow(tree)
        await nextTick()
        flowStore.value?.fitView({ padding: 0.15, maxZoom: 1 })
    } catch (err) {
        root.value = null
        flowNodes.value = []
        flowEdges.value = []
        error.value = resolveErrorMessage(err, 'errors.fallback.taskTreeLoad')
    } finally {
        loading.value = false
    }
}

watch(
    () => props.taskId,
    () => {
        activeNodeId.value = props.selectedId ?? props.taskId
        fetchTree()
    },
    { immediate: true },
)

defineExpose({ refresh: fetchTree })
</script>

<template>
    <div class="flex h-full min-w-0 flex-1 flex-col">
        <!-- 树的基本信息（仅加载完成后显示） -->
        <div v-if="root" class="border-b border-[#EAE6DE] bg-[#F8F5EE] px-4 py-3 sm:px-5">
            <div class="flex flex-wrap items-center gap-x-4 gap-y-2">
                <div class="flex items-center gap-2">
                    <span class="text-xs text-[#8A8177]">{{ t('taskTree.rootTask') }}</span>
                    <span class="font-mono text-sm font-semibold text-[#2B221B]">
                        #{{ root.id }}
                    </span>
                    <span
                        class="inline-flex items-center border px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-[0.18em]"
                        :class="
                            root.action === 'PLAN'
                                ? 'border-[#8A7F6D]/50 text-[#8A7F6D]'
                                : 'border-[#B29A84]/50 text-[#B29A84]'
                        "
                    >
                        {{ root.action === 'PLAN' ? t('taskTree.plan') : t('taskTree.run') }}
                    </span>
                </div>
                <span class="hidden text-[#E0D5C4] sm:inline">|</span>
                <span class="font-mono text-xs text-[#5A524A]"
                    >{{ root.namespace }}:{{ root.taskType }}</span
                >
                <span class="hidden text-[#E0D5C4] sm:inline">|</span>
                <span class="text-xs text-[#8A8177]">
                    {{ t('taskDetails.createdAt', { time: formatTime(root.createdAt) }) }}
                </span>
                <span class="hidden text-[#E0D5C4] sm:inline">|</span>
                <span class="text-xs text-[#8A8177]">
                    {{ t('taskTree.nodeCount', { count: treeStats.total }) }}
                </span>
                <!-- 各状态统计 -->
                <div class="ml-auto flex items-center gap-3">
                    <span
                        v-for="s in STATUS_ORDER"
                        :key="s"
                        class="flex items-center gap-1 text-[11px] text-[#5A524A]"
                    >
                        <span class="h-1.5 w-1.5 rounded-full" :class="STATUS_DOT_CLASS[s]"></span>
                        {{ statusLabelMap[s] }}
                        <span class="font-mono text-[#2B221B]">{{ treeStats.counts[s] }}</span>
                    </span>
                    <button
                        type="button"
                        class="p-1 text-[#8A8A8A] transition-colors hover:text-[#C67C4E]"
                        :title="t('taskTree.refreshTree')"
                        @click="fetchTree"
                    >
                        <RefreshCw class="h-3.5 w-3.5" :class="{ 'animate-spin': loading }" />
                    </button>
                </div>
            </div>
        </div>

        <!-- 自由画布（Vue Flow） -->
        <div class="relative min-h-0 flex-1">
            <div
                v-if="error"
                class="absolute top-4 right-4 left-4 z-10 border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700"
            >
                {{ error }}
            </div>

            <div
                v-if="taskId === null"
                class="absolute inset-0 flex flex-col items-center justify-center gap-3 text-sm text-[#8A8177]"
            >
                <ListTree class="h-10 w-10 text-[#E3D8CB]" />
                <p>{{ t('taskTree.selectHint') }}</p>
            </div>

            <div
                v-else-if="loading && !root"
                class="absolute inset-0 flex items-center justify-center text-sm text-[#6B635B]"
            >
                <Loader2 class="mr-2 h-4 w-4 animate-spin" />
                {{ t('taskDetails.loading') }}
            </div>

            <VueFlow
                v-else-if="root"
                :nodes="flowNodes"
                :edges="flowEdges"
                :node-class="nodeClass"
                :min-zoom="0.1"
                :max-zoom="2.5"
                :nodes-draggable="false"
                :nodes-connectable="false"
                :elements-selectable="true"
                fit-view-on-init
                class="task-tree-flow"
                @init="onFlowInit"
                @node-click="onNodeClick"
            >
                <Background :gap="20" :size="1" pattern-color="#EAE0D2" />
                <Controls position="bottom-right" :show-interactive="false" />
                <MiniMap
                    position="top-right"
                    :node-color="() => '#C9BCA9'"
                    mask-color="rgba(248, 245, 238, 0.7)"
                />

                <template #node-task="nodeProps">
                    <TaskTreeNode :data="nodeProps.data" :selected="nodeProps.selected" />
                </template>
            </VueFlow>
        </div>
    </div>
</template>

<style>
/* Vue Flow 主题微调以贴合应用配色（非 scoped，覆盖库默认样式） */
.task-tree-flow .vue-flow__node {
    cursor: pointer;
}
.task-tree-flow .vue-flow__edge-path {
    stroke: #e0d5c4;
}
.task-tree-flow .vue-flow__controls {
    box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
    border: 1px solid #eae6de;
}
.task-tree-flow .vue-flow__controls-button {
    background: #fffcf5;
    border-bottom: 1px solid #eae6de;
    fill: #5a524a;
}
.task-tree-flow .vue-flow__controls-button:hover {
    background: #f8f5ee;
    fill: #c67c4e;
}
.task-tree-flow .vue-flow__minimap {
    border: 1px solid #eae6de;
    background: #fffcf5;
}
</style>
