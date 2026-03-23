import type {FileProviderType} from '../enums/';

export interface DataCleanTaskRequest {
    readonly srcProviderType: FileProviderType;
    readonly srcProviderId: number;
}
