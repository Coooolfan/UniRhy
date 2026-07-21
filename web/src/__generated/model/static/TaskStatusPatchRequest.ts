import type {TaskStatus} from '../enums/';

export interface TaskStatusPatchRequest {
    readonly status: TaskStatus;
}
