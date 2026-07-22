export interface PluginConfigurationUpdateRequest {
    readonly values: any;
    readonly clearedSecretFields: ReadonlyArray<string>;
}
