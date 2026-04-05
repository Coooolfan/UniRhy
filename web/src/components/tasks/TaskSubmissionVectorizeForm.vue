<script setup lang="ts">
import { computed } from 'vue'
import { Music4 } from 'lucide-vue-next'
import type { VectorizeMode } from '@/__generated/model/enums/VectorizeMode'

type Props = {
    mode: VectorizeMode
}

const props = defineProps<Props>()

const emit = defineEmits<{
    (event: 'update:mode', value: VectorizeMode): void
}>()

const modeModel = computed({
    get: () => props.mode,
    set: (value: VectorizeMode) => emit('update:mode', value),
})
</script>

<template>
    <div class="space-y-8">
        <div class="space-y-6">
            <div>
                <span class="mb-3 block text-xs uppercase tracking-[0.24em] text-[#8A8A8A]">
                    向量化模式
                </span>
                <div class="flex gap-4">
                    <label
                        class="flex cursor-pointer items-center gap-2 rounded-sm border px-4 py-3 text-sm transition-colors"
                        :class="
                            modeModel === 'PENDING_ONLY'
                                ? 'border-[#C27E46] bg-[#FBF6F0] text-[#C27E46]'
                                : 'border-[#E0DCD0] bg-[#F7F5F0] text-[#6B635B] hover:border-[#C27E46]/50'
                        "
                    >
                        <input
                            v-model="modeModel"
                            type="radio"
                            value="PENDING_ONLY"
                            class="sr-only"
                        />
                        补充未向量化
                    </label>
                    <label
                        class="flex cursor-pointer items-center gap-2 rounded-sm border px-4 py-3 text-sm transition-colors"
                        :class="
                            modeModel === 'ALL'
                                ? 'border-[#C27E46] bg-[#FBF6F0] text-[#C27E46]'
                                : 'border-[#E0DCD0] bg-[#F7F5F0] text-[#6B635B] hover:border-[#C27E46]/50'
                        "
                    >
                        <input v-model="modeModel" type="radio" value="ALL" class="sr-only" />
                        全部重新向量化
                    </label>
                </div>
            </div>

            <div class="space-y-5 rounded-sm border border-[#E6E1D8] bg-[#F8F5EE] p-5">
                <div>
                    <div class="text-[11px] uppercase tracking-[0.24em] text-[#8A8A8A]">
                        提交说明
                    </div>
                    <p class="mt-3 text-sm leading-relaxed text-[#6B635B]">
                        {{
                            modeModel === 'PENDING_ONLY'
                                ? '仅为尚未生成 embedding 的录音补充向量化任务。'
                                : '对所有有歌词的录音重新生成 embedding，包括已向量化的。'
                        }}
                    </p>
                </div>
                <div class="flex items-start gap-2 text-sm text-[#6B635B]">
                    <Music4 class="mt-0.5 h-4 w-4 shrink-0 text-[#C27E46]" />
                    <span>嵌入模型的 API 配置已移至系统设置页面。</span>
                </div>
            </div>
        </div>
    </div>
</template>
