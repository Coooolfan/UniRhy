<script setup lang="ts">
import { computed } from 'vue'
import { Music4 } from 'lucide-vue-next'

type Props = {
    description: string
}

const props = defineProps<Props>()

const emit = defineEmits<{
    (event: 'update:description', value: string): void
}>()

const descriptionModel = computed({
    get: () => props.description,
    set: (value: string) => emit('update:description', value),
})
</script>

<template>
    <div class="space-y-8">
        <label class="block">
            <span class="mb-2 block text-xs uppercase tracking-[0.24em] text-[#8A8A8A]">
                歌单描述
            </span>
            <textarea
                v-model="descriptionModel"
                data-test="playlist-generate-description-input"
                rows="6"
                class="w-full resize-none border border-[#D6D1C4] bg-[#F7F5F0] p-4 text-sm leading-relaxed text-[#2C2C2C] outline-none transition-colors focus:border-[#C27E46]"
                placeholder="例如：通勤路上适合傍晚城市灯光、节奏平稳但有一点电子感的音乐"
            />
        </label>

        <div class="space-y-5 rounded-sm border border-[#E6E1D8] bg-[#F8F5EE] p-5">
            <div>
                <div class="text-[11px] uppercase tracking-[0.24em] text-[#8A8A8A]">提交说明</div>
                <p class="mt-3 text-sm leading-relaxed text-[#6B635B]">
                    输入你想听的氛围、场景或风格，系统会在后台生成一个新的智能歌单。
                </p>
            </div>
            <div class="flex items-start gap-2 text-sm text-[#6B635B]">
                <Music4 class="mt-0.5 h-4 w-4 shrink-0 text-[#C27E46]" />
                <span>嵌入模型和补全模型的 API 配置仍在系统设置页面维护。</span>
            </div>
        </div>
    </div>
</template>
