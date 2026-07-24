export type ArtistDto = {
    'ArtistController/ARTIST_DETAIL_FETCHER': {
        readonly id: number;
        readonly displayName: string;
        readonly alias: ReadonlyArray<string>;
        readonly comment: string;
        readonly avatar?: {
            readonly id: number;
            readonly objectKey: string;
            readonly mimeType: string;
            readonly size: number;
            readonly width?: number | undefined;
            readonly height?: number | undefined;
            readonly url: string;
        } | undefined;
    }, 
    'ArtistController/DEFAULT_ARTIST_FETCHER': {
        readonly id: number;
        readonly displayName: string;
        readonly alias: ReadonlyArray<string>;
        readonly comment: string;
    }
}
