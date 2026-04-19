export interface AlbumUpdate {
    readonly title: string;
    readonly kind: string;
    readonly releaseDate?: string | undefined;
    readonly comment: string;
}
