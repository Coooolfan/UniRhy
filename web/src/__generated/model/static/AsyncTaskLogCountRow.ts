import type {TaskStatus, TaskType} from '../enums/';

export interface AsyncTaskLogCountRow {
    readonly taskType: TaskType;
    readonly status: TaskStatus;
    readonly count: number;
}
