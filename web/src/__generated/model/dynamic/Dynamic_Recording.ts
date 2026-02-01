import type {
    Dynamic_Album, 
    Dynamic_Artist, 
    Dynamic_Asset, 
    Dynamic_MediaFile, 
    Dynamic_Work
} from './';

export interface Dynamic_Recording {
    readonly id?: number;
    readonly work?: Dynamic_Work;
    readonly artists?: ReadonlyArray<Dynamic_Artist>;
    readonly kind?: string;
    readonly label?: string | undefined;
    readonly title?: string | undefined;
    readonly comment?: string;
    readonly cover?: Dynamic_MediaFile | undefined;
    readonly assets?: ReadonlyArray<Dynamic_Asset>;
    readonly albums?: ReadonlyArray<Dynamic_Album>;
}
