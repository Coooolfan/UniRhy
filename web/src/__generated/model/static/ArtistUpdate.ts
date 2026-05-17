export interface ArtistUpdate {
    readonly displayName?: string | undefined;
    readonly alias?: ReadonlyArray<string> | undefined;
    readonly comment?: string | undefined;
    readonly avatarId?: number | undefined;
}
