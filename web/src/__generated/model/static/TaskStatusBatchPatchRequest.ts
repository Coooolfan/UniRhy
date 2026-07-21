import type {TaskStatus} from '../enums/';

export interface TaskStatusBatchPatchRequest {
    readonly ids: ReadonlyArray<number>;
    readonly status: TaskStatus;
}
