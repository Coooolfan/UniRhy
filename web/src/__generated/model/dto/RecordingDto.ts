import type {Embedding} from '../static/';

export type RecordingDto = {
    'RecordingController/PLAYBACK_RECORDING_FETCHER': {
        readonly id: number;
        readonly kind: string;
        readonly label?: string | undefined;
        readonly title?: string | undefined;
        readonly comment: string;
        readonly durationMs: number;
        readonly defaultInWork: boolean;
        readonly lyrics: string;
        readonly embedding?: Embedding | undefined;
        readonly work: {
            readonly id: number;
            readonly title: string;
        };
        readonly artists: ReadonlyArray<{
            readonly id: number;
            readonly displayName: string;
            readonly alias: ReadonlyArray<string>;
            readonly comment: string;
        }>;
        readonly cover?: {
            readonly id: number;
            readonly sha256: string;
            readonly objectKey: string;
            readonly mimeType: string;
            readonly size: number;
            readonly width?: number | undefined;
            readonly height?: number | undefined;
            readonly url: string;
        } | undefined;
    }, 
    'RecordingController/SIMILAR_RECORDING_FETCHER': {
        readonly id: number;
        readonly title?: string | undefined;
        readonly durationMs: number;
        readonly work: {
            readonly id: number;
            readonly title: string;
        };
        readonly artists: ReadonlyArray<{
            readonly id: number;
            readonly displayName: string;
        }>;
        readonly cover?: {
            readonly id: number;
            readonly url: string;
        } | undefined;
    }
}
