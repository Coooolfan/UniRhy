/**
 * 插件声明的单个任务
 */
export interface PluginConfigurationResponse {
    readonly values: any;
    readonly configuredSecretFields: ReadonlyArray<string>;
}
