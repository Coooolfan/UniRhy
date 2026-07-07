import type {TaskStatus, TaskType} from '../enums/';

export type AsyncTaskLogDto = {
    'TaskController/DEFAULT_TASK_LOG_FETCHER': {
        readonly id: number;
        readonly taskType: TaskType;
        readonly createdAt: string;
        readonly startedAt?: string | undefined;
        readonly completedAt?: string | undefined;
        readonly params: string;
        readonly completedReason?: string | undefined;
        readonly status: TaskStatus;
    }
}
