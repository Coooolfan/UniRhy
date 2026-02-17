export interface RecordingMergeReq {
    readonly targetId: number;
    readonly needMergeIds: ReadonlyArray<number>;
}
