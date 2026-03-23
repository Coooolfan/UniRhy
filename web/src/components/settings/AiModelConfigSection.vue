<script setup lang="ts">
import { computed } from 'vue'
import { Bot, Pencil, Save, X, ChevronDown } from 'lucide-vue-next'
import type { AiModelConfig } from '@/__generated/model/static'
import { AiRequestFormat_CONSTANTS, type AiRequestFormat } from '@/__generated/model/enums/AiRequestFormat'
import type { AiModelForm } from '@/composables/useStorageSettings'

type Props = {
    completionModel: AiModelConfig | null
    embeddingModel: AiModelConfig | null
    isEditing: 'completion' | 'embedding' | null
    isSaving: boolean
    error: string
    form: AiModelForm
}

const props = defineProps<Props>()

const emit = defineEmits<{
    (event: 'start-edit', type: 'completion' | 'embedding'): void
    (event: 'cancel-edit'): void
    (event: 'save'): void
    (event: 'update-form', patch: Partial<AiModelForm>): void
}>()

const REQUEST_FORMAT_LABELS: Record<AiRequestFormat, string> = {
    OPENAI: 'OpenAI',
    GEMINI: 'Gemini',
    CLAUDE: 'Claude',
    JINA: 'Jina',
}

type ModelCardInfo = {
    type: 'completion' | 'embedding'
    label: string
    subtitle: string
    config: AiModelConfig | null
}

const cards = computed<ModelCardInfo[]>(() => [
    {
        type: 'completion',
        label: '补全模型',
        subtitle: 'Completion Model',
        config: props.completionModel,
    },
    {
        type: 'embedding',
        label: '嵌入模型',
        subtitle: 'Embedding Model',
        config: props.embeddingModel,
    },
])
</script>

<template>
    <section class="mb-16 animate-in fade-in duration-500 font-serif">
        <h2 class="text-2xl font-serif text-[#2C2A28] mb-2">AI 模型配置</h2>
        <p class="text-sm text-[#A39E93] mb-1">AI Model Configuration</p>
        <div class="h-px w-full bg-[#E8E4D9] mb-6"></div>

        <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
            <div
                v-for="card in cards"
                :key="card.type"
                class="border border-[#E0DCD0] bg-[#FCFBF9] rounded-sm overflow-hidden"
            >
                <div class="flex items-center justify-between px-5 py-4 border-b border-[#E8E4D9] bg-[#F4F1EA]">
                    <div class="flex items-center gap-2">
                        <Bot :size="16" class="text-[#8A857B]" />
                        <div>
                            <span class="text-sm font-medium text-[#33312E]">{{ card.label }}</span>
                            <span class="text-[10px] text-[#A39E93] ml-2">{{ card.subtitle }}</span>
                        </div>
                    </div>
                    <button
                        v-if="isEditing !== card.type"
                        class="text-[#8A857B] hover:text-[#B87A5B] transition-colors"
                        @click="emit('start-edit', card.type)"
                    >
                        <Pencil :size="14" />
                    </button>
                </div>

                <!-- Display mode -->
                <div v-if="isEditing !== card.type" class="px-5 py-4 space-y-3">
                    <template v-if="card.config">
                        <div>
                            <div class="text-[10px] tracking-[0.15em] text-[#A39E93] uppercase mb-1">Endpoint</div>
                            <div class="font-mono text-xs text-[#66635C] truncate">{{ card.config.endpoint }}</div>
                        </div>
                        <div>
                            <div class="text-[10px] tracking-[0.15em] text-[#A39E93] uppercase mb-1">Model</div>
                            <div class="font-mono text-xs text-[#66635C]">{{ card.config.model }}</div>
                        </div>
                        <div>
                            <div class="text-[10px] tracking-[0.15em] text-[#A39E93] uppercase mb-1">API Key</div>
                            <div class="font-mono text-xs text-[#66635C]">••••••••</div>
                        </div>
                        <div>
                            <div class="text-[10px] tracking-[0.15em] text-[#A39E93] uppercase mb-1">Format</div>
                            <span class="inline-block bg-[#EBE6D9] text-[#66635C] text-[10px] px-2 py-0.5 rounded-sm">
                                {{ REQUEST_FORMAT_LABELS[card.config.requestFormat] }}
                            </span>
                        </div>
                    </template>
                    <div v-else class="text-sm text-[#A39E93] italic py-4 text-center">
                        尚未配置
                    </div>
                </div>

                <!-- Edit mode -->
                <div v-else class="px-5 py-4 space-y-3">
                    <div>
                        <label class="text-[10px] tracking-[0.15em] text-[#A39E93] uppercase mb-1 block">Endpoint</label>
                        <input
                            :value="form.endpoint"
                            @input="emit('update-form', { endpoint: ($event.target as HTMLInputElement).value })"
                            class="w-full border border-[#E0DCD0] bg-white rounded-sm px-3 py-1.5 text-xs font-mono text-[#33312E] focus:outline-none focus:border-[#B87A5B] transition-colors"
                            placeholder="https://api.example.com/v1/chat/completions"
                        />
                    </div>
                    <div>
                        <label class="text-[10px] tracking-[0.15em] text-[#A39E93] uppercase mb-1 block">Model</label>
                        <input
                            :value="form.model"
                            @input="emit('update-form', { model: ($event.target as HTMLInputElement).value })"
                            class="w-full border border-[#E0DCD0] bg-white rounded-sm px-3 py-1.5 text-xs font-mono text-[#33312E] focus:outline-none focus:border-[#B87A5B] transition-colors"
                            placeholder="gpt-4o"
                        />
                    </div>
                    <div>
                        <label class="text-[10px] tracking-[0.15em] text-[#A39E93] uppercase mb-1 block">API Key</label>
                        <input
                            :value="form.key"
                            @input="emit('update-form', { key: ($event.target as HTMLInputElement).value })"
                            type="password"
                            class="w-full border border-[#E0DCD0] bg-white rounded-sm px-3 py-1.5 text-xs font-mono text-[#33312E] focus:outline-none focus:border-[#B87A5B] transition-colors"
                            placeholder="sk-..."
                        />
                    </div>
                    <div>
                        <label class="text-[10px] tracking-[0.15em] text-[#A39E93] uppercase mb-1 block">Request Format</label>
                        <div class="relative">
                            <select
                                :value="form.requestFormat"
                                @change="emit('update-form', { requestFormat: ($event.target as HTMLSelectElement).value as AiRequestFormat })"
                                class="w-full border border-[#E0DCD0] bg-white rounded-sm px-3 py-1.5 text-xs text-[#33312E] focus:outline-none focus:border-[#B87A5B] transition-colors appearance-none pr-8"
                            >
                                <option v-for="fmt in AiRequestFormat_CONSTANTS" :key="fmt" :value="fmt">
                                    {{ REQUEST_FORMAT_LABELS[fmt] }}
                                </option>
                            </select>
                            <ChevronDown :size="12" class="absolute right-2.5 top-1/2 -translate-y-1/2 text-[#A39E93] pointer-events-none" />
                        </div>
                    </div>

                    <div v-if="error" class="text-xs text-[#B95D5D]">{{ error }}</div>

                    <div class="flex justify-end gap-2 pt-2">
                        <button
                            class="flex items-center gap-1 px-3 py-1.5 text-xs text-[#8A857B] border border-[#E0DCD0] rounded-sm hover:bg-[#F4F1EA] transition-colors"
                            :disabled="isSaving"
                            @click="emit('cancel-edit')"
                        >
                            <X :size="12" />
                            取消
                        </button>
                        <button
                            class="flex items-center gap-1 px-3 py-1.5 text-xs text-white bg-[#B87A5B] rounded-sm hover:bg-[#A06B4E] transition-colors disabled:opacity-50"
                            :disabled="isSaving"
                            @click="emit('save')"
                        >
                            <Save :size="12" />
                            保存
                        </button>
                    </div>
                </div>
            </div>
        </div>
    </section>
</template>
