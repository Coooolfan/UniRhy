import type {Dynamic_FileProviderFileSystem, Dynamic_FileProviderOss} from './';

export interface Dynamic_MediaFile {
    readonly id?: number;
    readonly sha256?: string;
    readonly objectKey?: string;
    readonly mimeType?: string;
    readonly size?: number;
    readonly width?: number | undefined;
    readonly height?: number | undefined;
    readonly ossProvider?: Dynamic_FileProviderOss | undefined;
    readonly fsProvider?: Dynamic_FileProviderFileSystem | undefined;
}
