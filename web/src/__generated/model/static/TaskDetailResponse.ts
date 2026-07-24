import type {AsyncTaskDto} from '../dto/';
import type {TaskStatusCounts} from './';

export interface TaskDetailResponse {
    readonly task: AsyncTaskDto['TaskController/DEFAULT_TASK_FETCHER'];
    readonly childTaskCounts: TaskStatusCounts;
}
