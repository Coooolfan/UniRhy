import type {PlaybackStrategy, StopStrategy} from '../enums/';

export interface CurrentQueueStrategyUpdateRequest {
    readonly playbackStrategy: PlaybackStrategy;
    readonly stopStrategy: StopStrategy;
    readonly version: number;
}
