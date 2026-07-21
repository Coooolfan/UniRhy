import type {TaskStatus} from '../enums/';

export type TaskSubmissionDto = {
    /**
     * 一次用户任务触发的规划资源；规划产生的 [AsyncTask] 与之为一对多关系。
     * 
     * 状态只描述规划与任务投递结果，不聚合子任务执行结果。
     */
    'TaskSubmissionController/DEFAULT_SUBMISSION_FETCHER': {
        readonly id: number;
        readonly namespace: string;
        readonly taskType: string;
        /**
         * 统一提交请求的参数，根 JSON Object
         */
        readonly params: any;
        readonly status: TaskStatus;
        readonly createdAt: string;
        readonly startedAt?: string | undefined;
        readonly completedAt?: string | undefined;
        readonly completedReason?: string | undefined;
    }
}
