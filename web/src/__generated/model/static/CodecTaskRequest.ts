import type {CodecType, FileProviderType} from '../enums/';

export interface CodecTaskRequest {
    readonly srcProviderType: FileProviderType;
    readonly srcProviderId: number;
    readonly dstProviderType: FileProviderType;
    readonly dstProviderId: number;
    readonly codecType: CodecType;
}
