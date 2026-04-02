import type {CurrentQueueItemDto} from './';

export interface CurrentQueueDto {
    readonly items: ReadonlyArray<CurrentQueueItemDto>;
    readonly currentEntryId?: number | undefined;
    readonly version: number;
    readonly updatedAtMs: number;
}
