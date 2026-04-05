import type { Component, Ref } from 'vue'
import { Cloud, FileAudio, FolderSearch, HardDrive, Music4 } from 'lucide-vue-next'
import type { CodecType } from '@/__generated/model/enums/CodecType'
import { type FileProviderType } from '@/__generated/model/enums/FileProviderType'
import type { TaskProviderOption } from '@/composables/useTaskManagement'

export type TaskKind =
    | 'METADATA_PARSE'
    | 'TRANSCODE'
    | 'VECTORIZE'
    | 'DATA_CLEAN'
    | 'PLAYLIST_GENERATE'

export type TaskDefinition = {
    id: TaskKind
    name: string
    desc: string
    icon: Component
}

export type TaskAvailability = {
    title: string
    description: string
    icon: Component
}

export type ProviderSelectionPayload = {
    providerType: FileProviderType
    providerId: number
}

export const TASK_ACTION_LABEL_MAP: Record<TaskKind, string> = {
    METADATA_PARSE: '元数据解析',
    TRANSCODE: '媒体转码',
    VECTORIZE: '向量化',
    DATA_CLEAN: '数据清洗',
    PLAYLIST_GENERATE: '歌单生成',
}

export const TASK_OPTIONS: TaskDefinition[] = [
    {
        id: 'METADATA_PARSE',
        name: TASK_ACTION_LABEL_MAP.METADATA_PARSE,
        desc: '遍历存储节点，发现媒体文件并补充缺失的元数据解析任务',
        icon: FolderSearch,
    },
    {
        id: 'TRANSCODE',
        name: TASK_ACTION_LABEL_MAP.TRANSCODE,
        desc: '按录音拆分为后台任务，批量转码为 Opus 音频资源',
        icon: FileAudio,
    },
    {
        id: 'VECTORIZE',
        name: TASK_ACTION_LABEL_MAP.VECTORIZE,
        desc: '按录音批量补充向量化任务，为录音生成 embedding 数据',
        icon: Music4,
    },
    {
        id: 'DATA_CLEAN',
        name: TASK_ACTION_LABEL_MAP.DATA_CLEAN,
        desc: '按录音批量调用外部模型清洗标题，移除后缀和描述信息',
        icon: Music4,
    },
    {
        id: 'PLAYLIST_GENERATE',
        name: TASK_ACTION_LABEL_MAP.PLAYLIST_GENERATE,
        desc: '根据你的描述创建智能歌单，并自动关联语义匹配的候选录音',
        icon: Music4,
    },
]

export const TARGET_CODEC_OPTIONS: Array<{ value: CodecType; label: string; hint: string }> = [
    { value: 'OPUS', label: 'Opus', hint: '128kbps VBR，用于流媒体播放' },
    { value: 'MP3', label: 'MP3', hint: '16kbps 单声道，用于低带宽场景' },
]

export const PROVIDER_TYPE_LABEL_MAP: Record<FileProviderType, string> = {
    FILE_SYSTEM: '本地存储',
    OSS: '对象存储',
}

export const PROVIDER_TYPE_ICON_MAP: Record<FileProviderType, Component> = {
    FILE_SYSTEM: HardDrive,
    OSS: Cloud,
}

export const optionValueOf = (provider: TaskProviderOption) => `${provider.type}:${provider.id}`

export const getDefaultProviderValue = (
    options: readonly TaskProviderOption[],
    preferredIndex = 0,
) => {
    const provider = options[preferredIndex] ?? options[0]
    return provider ? optionValueOf(provider) : ''
}

export const syncSelectionValue = (
    currentValue: Ref<string>,
    options: readonly TaskProviderOption[],
    preferredIndex = 0,
) => {
    const validValues = new Set(options.map((option) => optionValueOf(option)))

    if (!validValues.has(currentValue.value)) {
        currentValue.value = getDefaultProviderValue(options, preferredIndex)
    }
}
