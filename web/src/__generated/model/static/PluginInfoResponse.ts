export interface PluginInfoResponse {
    readonly id: string;
    readonly name?: string | undefined;
    readonly version: string;
    readonly taskType: string;
    readonly concurrency: number;
    readonly isAvailable: boolean;
    readonly enabled: boolean;
    readonly formDefinition: any;
}
