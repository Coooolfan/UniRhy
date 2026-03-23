import type {FileProviderType} from '../enums/';

export interface VectorizeTaskRequest {
    readonly srcProviderType: FileProviderType;
    readonly srcProviderId: number;
    readonly apiEndpoint: string;
    readonly apiKey: string;
    readonly modelName: string;
}
