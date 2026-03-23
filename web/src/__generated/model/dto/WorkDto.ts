import type {Embedding} from '../static/';

export type WorkDto = {
    'WorkController/DEFAULT_WORK_FETCHER': {
        readonly id: number;
        readonly title: string;
        readonly recordings: ReadonlyArray<{
            readonly id: number;
            readonly kind: string;
            readonly label?: string | undefined;
            readonly title?: string | undefined;
            readonly comment: string;
            readonly durationMs: number;
            readonly defaultInWork: boolean;
            readonly lyrics: string;
            readonly embedding?: Embedding | undefined;
            readonly assets: ReadonlyArray<{
                readonly id: number;
                readonly comment: string;
                readonly mediaFile: {
                    readonly id: number;
                    readonly sha256: string;
                    readonly mimeType: string;
                    readonly size: number;
                    readonly width?: number | undefined;
                    readonly height?: number | undefined;
                    readonly ossProvider?: {
                        readonly id: number;
                    } | undefined;
                    readonly fsProvider?: {
                        readonly id: number;
                    } | undefined;
                    readonly objectKey: string;
                    readonly url: string;
                };
            }>;
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
        }>;
    }
}
