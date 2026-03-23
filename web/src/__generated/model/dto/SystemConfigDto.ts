import type {AiModelConfig} from '../static/';

export type SystemConfigDto = {
    'SystemConfigController/DEFAULT_SYSTEM_CONFIG_FETCHER': {
        readonly id: number;
        readonly completionModel?: AiModelConfig | undefined;
        readonly embeddingModel?: AiModelConfig | undefined;
        readonly ossProviderId?: number | undefined;
        readonly fsProviderId?: number | undefined;
    }
}
