import type {TaskType} from '../enums/';

export interface RunningTaskView {
    readonly type: TaskType;
    readonly startedAt: number;
}
