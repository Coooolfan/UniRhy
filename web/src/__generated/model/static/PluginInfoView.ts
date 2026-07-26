import type {PluginInfoView_TargetOf_tasks} from './';

/**
 * 插件列表视图，含插件声明的全部任务。
 * 不含 WASM 字节码（每个插件最大 20MB）
 */
export interface PluginInfoView {
    /**
     * 插件 ID，即任务命名空间（反向域名）
     */
    readonly id: string;
    readonly name?: string | undefined;
    /**
     * 仅用于展示，无版本比较逻辑
     */
    readonly version: string;
    readonly enabled: boolean;
    /**
     * 插件级配置声明 `{schema, order}`；实际配置值保存为 [PluginData]
     */
    readonly configDefinition: any;
    /**
     * 该插件声明的全部任务
     */
    readonly tasks: ReadonlyArray<PluginInfoView_TargetOf_tasks>;
}
