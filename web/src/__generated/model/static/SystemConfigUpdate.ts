import type {AiModelConfig} from './';

export interface SystemConfigUpdate {
    readonly ossProviderId?: number | undefined;
    readonly fsProviderId?: number | undefined;
    readonly completionModel?: AiModelConfig | undefined;
    readonly embeddingModel?: AiModelConfig | undefined;
}
