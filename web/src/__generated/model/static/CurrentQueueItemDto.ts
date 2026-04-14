export interface CurrentQueueItemDto {
    readonly entryId: number;
    readonly recordingId: number;
    readonly title: string;
    readonly artistLabel: string;
    readonly coverUrl?: string | undefined;
    readonly durationMs: number;
}
