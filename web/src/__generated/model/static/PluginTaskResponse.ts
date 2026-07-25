/**
 * 插件声明的单个任务
 */
export interface PluginTaskResponse {
    readonly taskType: string;
    readonly concurrency: number;
    /**
     * 能否被用户直接从表单投递
     */
    readonly userSubmittable: boolean;
    readonly formDefinition: any;
}
