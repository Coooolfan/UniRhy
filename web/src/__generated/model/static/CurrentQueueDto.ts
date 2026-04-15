import type {PlaybackStatus, PlaybackStrategy, StopStrategy} from '../enums/';
import type {CurrentQueueItemDto} from './';

export interface CurrentQueueDto {
    readonly items: ReadonlyArray<CurrentQueueItemDto>;
    readonly recordingIds: ReadonlyArray<number>;
    readonly currentIndex: number;
    readonly playbackStrategy: PlaybackStrategy;
    readonly stopStrategy: StopStrategy;
    readonly playbackStatus: PlaybackStatus;
    readonly positionMs: number;
    readonly serverTimeToExecuteMs: number;
    readonly version: number;
    readonly updatedAtMs: number;
}
