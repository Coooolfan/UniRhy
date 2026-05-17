export interface ArtistCreate {
    readonly displayName: string;
    readonly alias: ReadonlyArray<string>;
    readonly comment: string;
    readonly avatarId?: number | undefined;
}
