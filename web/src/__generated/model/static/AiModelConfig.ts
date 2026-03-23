import type {AiRequestFormat} from '../enums/';

export interface AiModelConfig {
    readonly endpoint: string;
    readonly model: string;
    readonly key: string;
    readonly requestFormat: AiRequestFormat;
}
