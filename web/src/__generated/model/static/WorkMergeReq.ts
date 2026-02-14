export interface WorkMergeReq {
    readonly targetId: number;
    readonly needMergeIds: ReadonlyArray<number>;
}
