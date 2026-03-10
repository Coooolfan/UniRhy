import type {CodecType, FileProviderType} from '../enums/';

export interface TranscodeTaskRequest {
    readonly srcProviderType: FileProviderType;
    readonly srcProviderId: number;
    readonly dstProviderType: FileProviderType;
    readonly dstProviderId: number;
    readonly targetCodec: CodecType;
}
