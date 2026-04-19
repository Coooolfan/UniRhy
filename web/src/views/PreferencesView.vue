<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { FileAudio } from 'lucide-vue-next'
import DashboardTopBar from '@/components/dashboard/DashboardTopBar.vue'
import { normalizeApiError } from '@/ApiInstance'
import { useUserStore } from '@/stores/user'

type PresetOption = {
    value: string
    label: string
}

const FORMAT_PRESETS: readonly PresetOption[] = [
    { value: 'audio/opus', label: 'Opus (audio/opus)' },
    { value: 'audio/flac', label: 'FLAC (audio/flac)' },
    { value: 'audio/mpeg', label: 'MP3 (audio/mpeg)' },
    { value: 'audio/mp4', label: 'AAC/M4A (audio/mp4)' },
    { value: 'audio/wav', label: 'WAV (audio/wav)' },
    { value: 'audio/ogg', label: 'Ogg Vorbis (audio/ogg)' },
] as const

const CUSTOM_SENTINEL = '__custom__'

const userStore = useUserStore()

const isSubmitting = ref(false)
const errorMessage = ref('')
const successMessage = ref('')

const selectedPreset = ref<string>(CUSTOM_SENTINEL)
const customValue = ref<string>('')

const currentFormat = computed(() => userStore.user?.preferences.preferredAssetFormat ?? '')

const applyFromStore = () => {
    const value = currentFormat.value
    if (!value) {
        selectedPreset.value = FORMAT_PRESETS[0].value
        customValue.value = ''
        return
    }
    const match = FORMAT_PRESETS.find((preset) => preset.value === value)
    if (match) {
        selectedPreset.value = match.value
        customValue.value = ''
    } else {
        selectedPreset.value = CUSTOM_SENTINEL
        customValue.value = value
    }
}

const pendingFormat = computed(() => {
    if (selectedPreset.value === CUSTOM_SENTINEL) {
        return customValue.value.trim()
    }
    return selectedPreset.value
})

const isDirty = computed(() => pendingFormat.value !== currentFormat.value)
const canSubmit = computed(() => pendingFormat.value.length > 0 && isDirty.value)

onMounted(async () => {
    if (!userStore.user) {
        await userStore.fetchUser()
    }
    applyFromStore()
})

watch(currentFormat, () => {
    applyFromStore()
})

const handleSubmit = async () => {
    if (!canSubmit.value || isSubmitting.value) return

    const nextFormat = pendingFormat.value
    isSubmitting.value = true
    errorMessage.value = ''
    successMessage.value = ''

    try {
        await userStore.updateUser({
            preferences: { preferredAssetFormat: nextFormat },
        })
        successMessage.value = '个人偏好已更新'
    } catch (error) {
        const normalized = normalizeApiError(error)
        errorMessage.value = normalized.message ?? '更新失败'
    } finally {
        isSubmitting.value = false
    }
}
</script>

<template>
    <div class="pb-32 text-[#3D3D3D] font-sans selection:bg-[#C67C4E] selection:text-white">
        <DashboardTopBar />

        <div class="mx-auto max-w-5xl px-4 pt-4 sm:px-6 sm:pt-6 lg:px-8">
            <header class="mb-10 sm:mb-12">
                <h1 class="mb-2 font-serif text-3xl tracking-tight text-[#2B221B]">个人偏好</h1>
                <p class="font-serif text-sm italic text-[#8A8A8A]">管理客户端播放与体验偏好</p>
            </header>
        </div>

        <div class="mx-auto mt-10 max-w-5xl px-8">
            <section class="border border-[#EAE6DE] bg-white p-6 sm:p-8">
                <div class="mb-6 flex items-center gap-3">
                    <div
                        class="flex h-10 w-10 items-center justify-center rounded-full bg-[#F7F5F0] text-[#8C857B]"
                    >
                        <FileAudio :size="18" />
                    </div>
                    <div>
                        <h2 class="font-serif text-xl text-[#2B221B]">首选资产格式</h2>
                        <p class="text-xs text-[#8C857B]">
                            当同一录音存在多个资产时，客户端将优先选用匹配此 MIME 的版本
                        </p>
                    </div>
                </div>

                <div
                    v-if="errorMessage"
                    class="mb-4 rounded border border-[#ffe0e0] bg-[#fff5f5] p-3 text-sm text-[#B95D5D]"
                >
                    {{ errorMessage }}
                </div>

                <form @submit.prevent="handleSubmit" class="space-y-5">
                    <div class="space-y-1">
                        <label
                            class="ml-1 text-xs font-medium uppercase tracking-wider text-[#8C857B]"
                        >
                            MIME 类型
                        </label>
                        <select
                            v-model="selectedPreset"
                            class="w-full border border-[#D6D1C4] bg-white px-3 py-2.5 text-[#2C2825] outline-none transition-all focus:border-[#D98C28] focus:ring-1 focus:ring-[#D98C28]"
                        >
                            <option
                                v-for="preset in FORMAT_PRESETS"
                                :key="preset.value"
                                :value="preset.value"
                            >
                                {{ preset.label }}
                            </option>
                            <option :value="CUSTOM_SENTINEL">自定义…</option>
                        </select>
                    </div>

                    <div v-if="selectedPreset === CUSTOM_SENTINEL" class="space-y-1">
                        <label
                            class="ml-1 text-xs font-medium uppercase tracking-wider text-[#8C857B]"
                        >
                            自定义 MIME
                        </label>
                        <input
                            v-model="customValue"
                            type="text"
                            required
                            placeholder="audio/..."
                            class="w-full border border-[#D6D1C4] bg-white px-3 py-2.5 text-[#2C2825] outline-none transition-all placeholder-[#E0DCD6] focus:border-[#D98C28] focus:ring-1 focus:ring-[#D98C28]"
                        />
                        <p class="ml-1 text-xs text-[#B8B0A3]">
                            服务器不做 MIME 合法性校验，请填写标准 MIME 字符串
                        </p>
                    </div>

                    <div class="flex items-center justify-between gap-3 pt-2">
                        <p class="text-xs text-[#B8B0A3]">
                            当前：<span class="text-[#5E5950]">{{
                                currentFormat || '未设置'
                            }}</span>
                        </p>
                        <button
                            type="submit"
                            :disabled="!canSubmit || isSubmitting"
                            class="bg-[#2B221B] px-6 py-2.5 text-sm uppercase tracking-wide text-[#F7F5F0] shadow-md transition-colors hover:bg-[#3E3228] disabled:cursor-not-allowed disabled:opacity-60"
                        >
                            {{ isSubmitting ? '正在保存' : '保存更改' }}
                        </button>
                    </div>
                </form>
            </section>
        </div>
    </div>
</template>
