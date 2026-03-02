import type {TaskType} from '../enums/';

export type AsyncTaskLogDto = {
    'TaskController/DEFAULT_ASYNC_TASK_LOG_FETCHER': {
        readonly id: number;
        readonly taskType: TaskType;
        readonly startedAt: string;
        readonly completedAt?: string | undefined;
        readonly params: string;
        readonly completedReason?: string | undefined;
        readonly running: boolean;
    }
}
