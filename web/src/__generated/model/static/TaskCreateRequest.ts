export interface TaskCreateRequest {
    readonly namespace: string;
    readonly taskType: string;
    readonly payload: any;
}
