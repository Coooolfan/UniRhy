export type PlaylistDto = {
    'PlaylistController/DEFAULT_PLAYLIST_FETCHER': {
        readonly id: number;
        readonly name: string;
        readonly comment: string;
    }, 
    'PlaylistController/DETAIL_PLAYLIST_FETCHER': {
        readonly id: number;
        readonly name: string;
        readonly comment: string;
        readonly recordings: ReadonlyArray<{
            readonly id: number;
            readonly kind: string;
            readonly label?: string | undefined;
            readonly title?: string | undefined;
            readonly comment: string;
            readonly defaultInWork: boolean;
            readonly assets: ReadonlyArray<{
                readonly id: number;
                readonly comment: string;
                readonly mediaFile: {
                    readonly id: number;
                    readonly sha256: string;
                    readonly objectKey: string;
                    readonly mimeType: string;
                    readonly size: number;
                    readonly width?: number | undefined;
                    readonly height?: number | undefined;
                };
            }>;
            readonly artists: ReadonlyArray<{
                readonly id: number;
                readonly name: string;
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
            } | undefined;
        }>;
    }
}
