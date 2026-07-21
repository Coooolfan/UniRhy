import type {TaskStatusCounts} from './';

export interface TaskStatisticsResponse {
    readonly namespace: string;
    readonly taskType: string;
    readonly submissions: TaskStatusCounts;
    readonly tasks: TaskStatusCounts;
}
