import type {TaskStatus} from '../enums/';

export type AsyncTaskDto = {
    /**
     * 统一任务资源。所有任务节点同构：由 `(namespace, taskType)` 对应的 Executor 执行，
     * Executor 返回的后继被入队为子任务，返回空序列即叶子。
     * [parent] 只表示产生关系，不参与执行角色判定。
     */
    'TaskController/DEFAULT_TASK_FETCHER': {
        readonly id: number;
        readonly namespace: string;
        readonly taskType: string;
        /**
         * 入口任务使用用户表单参数，其余任务使用上游 Executor 生成的执行载荷。
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
     * 统一任务资源。所有任务节点同构：由 `(namespace, taskType)` 对应的 Executor 执行，
     * Executor 返回的后继被入队为子任务，返回空序列即叶子。
     * [parent] 只表示产生关系，不参与执行角色判定。
     */
    'TaskController/TASK_TREE_FETCHER': {
        readonly id: number;
        readonly namespace: string;
        readonly taskType: string;
        /**
         * 入口任务使用用户表单参数，其余任务使用上游 Executor 生成的执行载荷。
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
