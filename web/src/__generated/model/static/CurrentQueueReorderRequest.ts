export interface CurrentQueueReorderRequest {
    readonly recordingIds: ReadonlyArray<number>;
    readonly currentIndex: number;
    readonly version: number;
}
