<script setup lang="ts">
import { Music4 } from 'lucide-vue-next'
</script>

<template>
    <div class="space-y-8">
        <div class="space-y-5 rounded-sm border border-[#E6E1D8] bg-[#F8F5EE] p-5">
            <div>
                <div class="text-[11px] uppercase tracking-[0.24em] text-[#8A8A8A]">提交说明</div>
                <p class="mt-3 text-sm leading-relaxed text-[#6B635B]">
                    提交后系统会为所有录音补充数据清洗任务，批量调用外部模型清洗标题。
                </p>
            </div>
            <div class="flex items-start gap-2 text-sm text-[#6B635B]">
                <Music4 class="mt-0.5 h-4 w-4 shrink-0 text-[#C27E46]" />
                <span>补全模型的 API 配置已移至系统设置页面。</span>
            </div>
        </div>
    </div>
</template>
