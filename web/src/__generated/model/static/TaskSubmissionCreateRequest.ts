export interface TaskSubmissionCreateRequest {
    readonly namespace: string;
    readonly taskType: string;
    readonly params: any;
}
