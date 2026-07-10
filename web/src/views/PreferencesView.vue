<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { BatteryCharging, Bell, ExternalLink, FileAudio, Radio } from 'lucide-vue-next'
import DashboardTopBar from '@/components/dashboard/DashboardTopBar.vue'
import { normalizeApiError } from '@/ApiInstance'
import {
    type AndroidPlaybackSystemStatus,
    getAndroidPlaybackSystemStatus,
    isAndroidRuntime,
    openAndroidBatterySettings,
    requestAndroidNotificationPermission,
} from '@/runtime/androidPlayback'
import { useClientPreferencesStore, type PlaybackMode } from '@/stores/clientPreferences'
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
const clientPreferencesStore = useClientPreferencesStore()

const isSubmitting = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
const isAndroid = isAndroidRuntime()
const androidSystemStatus = ref<AndroidPlaybackSystemStatus | null>(null)
const isLoadingAndroidStatus = ref(false)
const androidStatusError = ref('')

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

const refreshAndroidSystemStatus = async () => {
    if (!isAndroid || isLoadingAndroidStatus.value) {
        return
    }

    isLoadingAndroidStatus.value = true
    androidStatusError.value = ''
    try {
        androidSystemStatus.value = await getAndroidPlaybackSystemStatus()
    } catch (error) {
        console.error('Failed to load Android playback settings', error)
        androidStatusError.value = '无法读取 Android 系统设置'
    } finally {
        isLoadingAndroidStatus.value = false
    }
}

const handleVisibilityChange = () => {
    if (!document.hidden) {
        void refreshAndroidSystemStatus()
    }
}

onMounted(async () => {
    if (!userStore.user) {
        await userStore.fetchUser()
    }
    applyFromStore()

    if (isAndroid) {
        document.addEventListener('visibilitychange', handleVisibilityChange)
        await refreshAndroidSystemStatus()
    }
})

onUnmounted(() => {
    document.removeEventListener('visibilitychange', handleVisibilityChange)
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

const handlePlaybackModeChange = (event: Event) => {
    clientPreferencesStore.setPlaybackMode(
        (event.target as HTMLSelectElement).value as PlaybackMode,
    )
}

const handleRequestNotificationPermission = async () => {
    androidStatusError.value = ''
    try {
        await requestAndroidNotificationPermission()
        await refreshAndroidSystemStatus()
    } catch (error) {
        console.error('Failed to request Android notification permission', error)
        androidStatusError.value = '无法请求通知权限'
    }
}

const handleOpenBatterySettings = async () => {
    androidStatusError.value = ''
    try {
        await openAndroidBatterySettings()
    } catch (error) {
        console.error('Failed to open Android battery settings', error)
        androidStatusError.value = '无法打开电池设置'
    }
}
</script>

<template>
    <div class="pb-32 text-[#3D3D3D] font-sans selection:bg-[#C67C4E] selection:text-white">
        <DashboardTopBar />

        <div class="mx-auto max-w-5xl px-4 pt-4 sm:px-6 sm:pt-6 lg:px-8">
            <header class="mb-10 sm:mb-12">
                <h1 class="mb-2 font-serif text-3xl tracking-tight text-[#2B221B]">个人偏好</h1>
                <p class="font-serif text-sm italic text-[#8A8A8A]">客户端播放与体验偏好</p>
            </header>
        </div>

        <div class="mx-auto mt-10 max-w-5xl space-y-6 px-8">
            <section class="border border-[#EAE6DE] bg-white p-6 sm:p-8">
                <div class="mb-6 flex items-center gap-3">
                    <div
                        class="flex h-10 w-10 items-center justify-center rounded-full bg-[#F7F5F0] text-[#8C857B]"
                    >
                        <Radio :size="18" />
                    </div>
                    <div>
                        <h2 class="font-serif text-xl text-[#2B221B]">播放模式</h2>
                        <p class="text-xs text-[#8C857B]">
                            控制本设备的播放队列与状态是否和同账号下的不同设备实时同步
                        </p>
                    </div>
                </div>

                <div class="space-y-1">
                    <label class="ml-1 text-xs font-medium uppercase tracking-wider text-[#8C857B]">
                        模式
                    </label>
                    <select
                        data-test="playback-mode-select"
                        :value="clientPreferencesStore.playbackMode"
                        class="w-full border border-[#D6D1C4] bg-white px-3 py-2.5 text-[#2C2825] outline-none transition-all focus:border-[#D98C28] focus:ring-1 focus:ring-[#D98C28]"
                        @change="handlePlaybackModeChange"
                    >
                        <option value="SYNC">同步</option>
                        <option value="INDEPENDENT">独立</option>
                    </select>
                    <p class="ml-1 text-xs text-[#B8B0A3]">
                        独立模式下播放队列和控制只保存在当前客户端，不连接同步至其他设备
                    </p>
                </div>
            </section>

            <section
                v-if="isAndroid"
                class="border border-[#EAE6DE] bg-white p-6 sm:p-8"
                data-test="android-playback-settings"
            >
                <div class="mb-6 flex items-center gap-3">
                    <div
                        class="flex h-10 w-10 items-center justify-center rounded-full bg-[#F7F5F0] text-[#8C857B]"
                    >
                        <BatteryCharging :size="18" />
                    </div>
                    <div>
                        <h2 class="font-serif text-xl text-[#2B221B]">Android 后台播放</h2>
                        <p class="text-xs text-[#8C857B]">系统通知与后台用电状态</p>
                    </div>
                </div>

                <div
                    v-if="androidStatusError"
                    class="mb-4 border border-[#ffe0e0] bg-[#fff5f5] p-3 text-sm text-[#B95D5D]"
                >
                    {{ androidStatusError }}
                </div>

                <div class="divide-y divide-[#EEEAE2] border-y border-[#EEEAE2]">
                    <div class="flex flex-wrap items-center justify-between gap-4 py-4">
                        <div class="flex min-w-0 items-center gap-3">
                            <Bell :size="17" class="shrink-0 text-[#8C857B]" />
                            <div>
                                <p class="text-sm font-medium text-[#3D3833]">通知权限</p>
                                <p class="text-xs text-[#9C968B]" data-test="notification-status">
                                    {{
                                        androidSystemStatus === null
                                            ? '检查中'
                                            : androidSystemStatus.notificationPermissionGranted
                                              ? '已允许'
                                              : '未允许'
                                    }}
                                </p>
                            </div>
                        </div>
                        <button
                            v-if="!androidSystemStatus?.notificationPermissionGranted"
                            type="button"
                            data-test="request-notification-permission"
                            :disabled="isLoadingAndroidStatus"
                            class="inline-flex items-center gap-2 border border-[#D6D1C4] bg-white px-4 py-2 text-sm text-[#4C4640] transition-colors hover:bg-[#F7F5F0] disabled:cursor-not-allowed disabled:opacity-60"
                            @click="handleRequestNotificationPermission"
                        >
                            <Bell :size="15" />
                            允许通知
                        </button>
                    </div>

                    <div class="flex flex-wrap items-center justify-between gap-4 py-4">
                        <div class="flex min-w-0 items-center gap-3">
                            <BatteryCharging :size="17" class="shrink-0 text-[#8C857B]" />
                            <div>
                                <p class="text-sm font-medium text-[#3D3833]">后台用电</p>
                                <p class="text-xs text-[#9C968B]" data-test="battery-status">
                                    {{
                                        androidSystemStatus === null
                                            ? '检查中'
                                            : androidSystemStatus.batteryOptimizationEnabled
                                              ? '系统优化中'
                                              : '不受限制'
                                    }}
                                </p>
                            </div>
                        </div>
                        <button
                            type="button"
                            data-test="open-battery-settings"
                            :disabled="isLoadingAndroidStatus"
                            class="inline-flex items-center gap-2 border border-[#D6D1C4] bg-white px-4 py-2 text-sm text-[#4C4640] transition-colors hover:bg-[#F7F5F0] disabled:cursor-not-allowed disabled:opacity-60"
                            @click="handleOpenBatterySettings"
                        >
                            <ExternalLink :size="15" />
                            系统设置
                        </button>
                    </div>
                </div>
            </section>

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
                            当同一曲目存在多个资产格式时，客户端将优先选用匹配此 MIME 的版本
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
                            <option :value="CUSTOM_SENTINEL">自定义</option>
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
                            服务器不验证 MIME 合法性，请填写标准 MIME 字符串
                        </p>
                    </div>

                    <div class="flex items-center justify-end gap-3 pt-2">
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
