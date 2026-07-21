import type {TaskStatus} from '../enums/';

export type AsyncTaskDto = {
    /**
     * 单条可排队、claim、完成、失败、取消和重新排队的执行任务资源。
     * 
     * `namespace/taskType` 为执行与索引反规范化保存，必须与父 submission 一致。
     */
    'TaskController/DEFAULT_TASK_FETCHER': {
        readonly id: number;
        readonly namespace: string;
        readonly taskType: string;
        /**
         * Planner 产生的任务载荷，允许任意合法 JSON 值
         */
        readonly payload: any;
        readonly status: TaskStatus;
        readonly createdAt: string;
        readonly startedAt?: string | undefined;
        readonly completedAt?: string | undefined;
        readonly completedReason?: string | undefined;
        readonly submissionId: number;
    }
}
