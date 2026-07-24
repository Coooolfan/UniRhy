import type {TaskAction, TaskStatus} from '../enums/';

export type AsyncTaskDto = {
    /**
     * 统一任务资源。根任务承载用户表单参数并执行 PLAN，运行中的任务可创建 RUN 子任务。
     * [parent] 仅表示产生关系；每个节点的状态只描述该节点自身的生命周期。
     */
    'TaskController/DEFAULT_TASK_FETCHER': {
        readonly id: number;
        readonly namespace: string;
        readonly taskType: string;
        readonly action: TaskAction;
        /**
         * PLAN 使用入口表单参数，RUN 使用 Planner 或父任务生成的执行载荷。
         */
        readonly payload: any;
        readonly status: TaskStatus;
        readonly createdAt: string;
        readonly startedAt?: string | undefined;
        readonly completedAt?: string | undefined;
        readonly completedReason?: string | undefined;
        readonly parentId?: number | undefined;
    }, 
    /**
     * 统一任务资源。根任务承载用户表单参数并执行 PLAN，运行中的任务可创建 RUN 子任务。
     * [parent] 仅表示产生关系；每个节点的状态只描述该节点自身的生命周期。
     */
    'TaskController/TASK_TREE_FETCHER': {
        readonly id: number;
        readonly namespace: string;
        readonly taskType: string;
        readonly action: TaskAction;
        /**
         * PLAN 使用入口表单参数，RUN 使用 Planner 或父任务生成的执行载荷。
         */
        readonly payload: any;
        readonly status: TaskStatus;
        readonly createdAt: string;
        readonly startedAt?: string | undefined;
        readonly completedAt?: string | undefined;
        readonly completedReason?: string | undefined;
        readonly parentId?: number | undefined;
        readonly childTasks?: ReadonlyArray<AsyncTaskDto['TaskController/TASK_TREE_FETCHER']> | undefined;
    }
}
