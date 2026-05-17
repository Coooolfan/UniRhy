export interface ArtistMergeReq {
    readonly targetId: number;
    readonly needMergeIds: ReadonlyArray<number>;
}
