/**
 * 插件声明的单个任务。业务身份是 `(plugin, taskType)`，[id] 仅为代理主键。
 * 
 * [userSubmittable] 表示该任务能否被用户直接从表单投递（入口任务）；
 * 非入口任务只能由上游 Executor 的返回值产生，但其表单定义仍作为
 * payload 契约参与校验。
 */
export interface PluginInfoView_TargetOf_tasks {
    readonly taskType: string;
    /**
     * 单节点并发上限；管理员可调整，覆盖升级时保留
     */
    readonly concurrency: number;
    /**
     * 能否被用户直接从表单投递
     */
    readonly userSubmittable: boolean;
    /**
     * 任务参数声明 `{schema, order}`
     */
    readonly formDefinition: any;
}
