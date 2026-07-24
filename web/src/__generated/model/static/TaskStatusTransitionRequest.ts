import type {TaskStatus} from '../enums/';

export interface TaskStatusTransitionRequest {
    readonly namespace: string;
    readonly taskType: string;
    readonly sourceStatuses: ReadonlyArray<TaskStatus>;
    readonly targetStatus: TaskStatus;
}
