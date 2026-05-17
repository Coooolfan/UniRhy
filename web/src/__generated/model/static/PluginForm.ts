import type {PluginFormField} from './';

export interface PluginForm {
    readonly fields: ReadonlyArray<PluginFormField>;
}
