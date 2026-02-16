export type AlbumDto = {
    'AlbumController/DEFAULT_ALBUM_FETCHER': {
        readonly id: number;
        readonly title: string;
        readonly kind: string;
        readonly releaseDate?: string | undefined;
        readonly comment: string;
        readonly recordings: ReadonlyArray<{
            readonly id: number;
            readonly label?: string | undefined;
        }>;
        readonly cover?: {
            readonly id: number;
        } | undefined;
    }, 
    'AlbumController/DETAIL_ALBUM_FETCHER': {
        readonly id: number;
        readonly title: string;
        readonly kind: string;
        readonly releaseDate?: string | undefined;
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
        readonly cover?: {
            readonly id: number;
            readonly sha256: string;
            readonly objectKey: string;
            readonly mimeType: string;
            readonly size: number;
            readonly width?: number | undefined;
            readonly height?: number | undefined;
        } | undefined;
    }
}
