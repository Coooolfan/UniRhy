import type {Dynamic_MediaFile} from './';

export interface Dynamic_Artist {
    readonly id?: number;
    readonly name?: string;
    readonly comment?: string;
    readonly avatar?: Dynamic_MediaFile | undefined;
}
