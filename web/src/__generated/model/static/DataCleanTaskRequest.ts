import type {FileProviderType} from '../enums/';

export interface DataCleanTaskRequest {
    readonly srcProviderType: FileProviderType;
    readonly srcProviderId: number;
    readonly apiEndpoint: string;
    readonly apiKey: string;
    readonly modelName: string;
}
