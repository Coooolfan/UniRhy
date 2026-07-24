import type {TaskStatusCounts} from './';

export interface TaskStatisticsResponse {
    readonly namespace: string;
    readonly taskType: string;
    readonly tasks: TaskStatusCounts;
}
