export interface CurrentQueueAppendRequest {
    readonly recordingIds: ReadonlyArray<number>;
    readonly version: number;
}
