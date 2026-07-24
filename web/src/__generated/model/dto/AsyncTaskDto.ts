import type {TaskStatus} from '../enums/';

export type AsyncTaskDto = {
    /**
     * 统一任务资源。根任务承载用户表单参数并通过 Planner 生成子任务，非根任务执行具体载荷。
     * [parent] 既表示产生关系，也决定节点交由 Planner 还是 Handler 处理。
     */
    'TaskController/DEFAULT_TASK_FETCHER': {
        readonly id: number;
        readonly namespace: string;
        readonly taskType: string;
        /**
         * 根任务使用入口表单参数，非根任务使用 Planner 或父任务生成的执行载荷。
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
     * 统一任务资源。根任务承载用户表单参数并通过 Planner 生成子任务，非根任务执行具体载荷。
     * [parent] 既表示产生关系，也决定节点交由 Planner 还是 Handler 处理。
     */
    'TaskController/TASK_TREE_FETCHER': {
        readonly id: number;
        readonly namespace: string;
        readonly taskType: string;
        /**
         * 根任务使用入口表单参数，非根任务使用 Planner 或父任务生成的执行载荷。
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
