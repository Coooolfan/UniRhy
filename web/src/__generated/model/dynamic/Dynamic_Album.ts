import type {Dynamic_MediaFile, Dynamic_Recording} from './';

export interface Dynamic_Album {
    readonly id?: number;
    readonly title?: string;
    readonly recordings?: ReadonlyArray<Dynamic_Recording>;
    readonly kind?: string;
    readonly releaseDate?: string | undefined;
    readonly comment?: string;
    readonly cover?: Dynamic_MediaFile | undefined;
}
