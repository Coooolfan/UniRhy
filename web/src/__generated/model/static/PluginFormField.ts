export interface PluginFormField {
    readonly name: string;
    readonly type: string;
    readonly label: string;
    readonly description?: string | undefined;
    readonly default?: string | undefined;
    readonly min?: number | undefined;
    readonly max?: number | undefined;
}
