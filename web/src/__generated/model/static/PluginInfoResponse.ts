import type {PluginTaskResponse} from './';

export interface PluginInfoResponse {
    readonly id: string;
    readonly name?: string | undefined;
    readonly version: string;
    readonly isAvailable: boolean;
    readonly enabled: boolean;
    readonly tasks: ReadonlyArray<PluginTaskResponse>;
    readonly configDefinition: any;
}
