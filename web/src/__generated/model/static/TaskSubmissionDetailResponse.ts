import type {TaskSubmissionDto} from '../dto/';
import type {TaskStatusCounts} from './';

export interface TaskSubmissionDetailResponse {
    readonly submission: TaskSubmissionDto['TaskSubmissionController/DEFAULT_SUBMISSION_FETCHER'];
    readonly taskCounts: TaskStatusCounts;
}
