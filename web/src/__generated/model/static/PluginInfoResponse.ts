import type {TaskType} from '../enums/';
import type {PluginForm} from './';

export interface PluginInfoResponse {
    readonly id: string;
    readonly name?: string | undefined;
    readonly version: string;
    readonly taskType?: TaskType | undefined;
    readonly extension: string;
    readonly isAvailable: boolean;
    readonly enabled: boolean;
    readonly form: PluginForm;
}
