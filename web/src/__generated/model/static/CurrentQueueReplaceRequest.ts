export interface CurrentQueueReplaceRequest {
    readonly recordingIds: ReadonlyArray<number>;
    readonly currentIndex: number;
}
