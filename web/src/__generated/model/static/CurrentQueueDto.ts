import type {PlaybackStrategy, StopStrategy} from '../enums/';
import type {CurrentQueueItemDto} from './';

export interface CurrentQueueDto {
    readonly items: ReadonlyArray<CurrentQueueItemDto>;
    readonly currentEntryId?: number | undefined;
    readonly playbackStrategy: PlaybackStrategy;
    readonly stopStrategy: StopStrategy;
    readonly version: number;
    readonly updatedAtMs: number;
}
