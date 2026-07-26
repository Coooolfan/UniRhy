/**
 * 当前可用的任务定义。内建定义来自服务端静态定义，
 * 插件定义来自已启用插件的 `plugin_task`。
 * 
 * [name] 始终是任务自身的显示名，插件未声明时回退到 [taskType]。
 * [userSubmittable] 为 true 表示可被用户从表单直接投递（入口任务）；
 * 其余任务只能由上游 Executor 的返回值产生，但仍是合法的派活目标。
 */
export interface TaskDefinitionView {
    readonly namespace: string;
    readonly taskType: string;
    readonly name: string;
    readonly userSubmittable: boolean;
    readonly formDefinition: any;
}
