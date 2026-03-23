import type {FileProviderType} from '../enums/';

export interface VectorizeTaskRequest {
    readonly srcProviderType: FileProviderType;
    readonly srcProviderId: number;
}
