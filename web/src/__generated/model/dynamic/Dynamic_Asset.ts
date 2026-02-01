import type {Dynamic_MediaFile, Dynamic_Recording} from './';

export interface Dynamic_Asset {
    readonly id?: number;
    readonly recording?: Dynamic_Recording;
    readonly mediaFile?: Dynamic_MediaFile;
    readonly comment?: string;
}
