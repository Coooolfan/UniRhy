/**
 * 当前可提交的任务定义。内建定义来自服务端静态定义，
 * 插件定义来自已启用插件的 `form_definition`。
 */
export interface TaskDefinitionView {
    readonly namespace: string;
    readonly taskType: string;
    readonly name?: string | undefined;
    readonly formDefinition: any;
}
