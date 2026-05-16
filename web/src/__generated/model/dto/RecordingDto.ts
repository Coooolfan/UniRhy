export type RecordingDto = {
    'RecordingController/PLAYBACK_RECORDING_FETCHER': {
        readonly id: number;
        readonly label: ReadonlyArray<string>;
        readonly title?: string | undefined;
        readonly comment: string;
        readonly durationMs: number;
        readonly defaultInWork: boolean;
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
            readonly objectKey: string;
            readonly mimeType: string;
            readonly size: number;
            readonly width?: number | undefined;
            readonly height?: number | undefined;
            readonly url: string;
        } | undefined;
        readonly assets: ReadonlyArray<{
            readonly id: number;
            readonly comment: string;
            readonly mediaFile: {
                readonly id: number;
                readonly objectKey: string;
                readonly mimeType: string;
                readonly size: number;
                readonly width?: number | undefined;
                readonly height?: number | undefined;
                readonly url: string;
            };
        }>;
    }
}
