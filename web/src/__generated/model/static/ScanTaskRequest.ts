import type {FileProviderType} from '../enums/';

export interface ScanTaskRequest {
    readonly providerType: FileProviderType;
    readonly providerId: number;
}
