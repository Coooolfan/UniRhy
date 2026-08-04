<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { Check, LoaderCircle, QrCode, X } from 'lucide-vue-next'
import QRCodeGenerator from 'qrcode'
import { useI18n } from 'vue-i18n'
import { api } from '@/ApiInstance'
import type { LoginTransferDto } from '@/__generated/model/dto'
import type { LoginTransferStatus } from '@/__generated/model/enums'
import { resolveErrorMessage } from '@/i18n/errors'
import { getPlatformRuntime } from '@/runtime/platform'
import {
    assertReachableServerUrl,
    encodeLoginTransferQr,
    LoopbackServerUrlError,
} from '@/services/loginTransferQr'
import { ACTIVE_LOGIN_TRANSFER_STATUSES } from '@/services/loginTransferStatus'

const emit = defineEmits<{ close: [] }>()
const { t } = useI18n()

type LoginTransfer = LoginTransferDto['LoginTransferController/SOURCE_TRANSFER_FETCHER']

const POLL_INTERVAL_MS = 1000
/** 交接完成后成功状态的展示时长，随后自动退回个人资料页 */
const AUTO_CLOSE_DELAY_MS = 1500
/** 已授权的交接进入新设备换票阶段，此时关闭弹窗不应再打断登录 */
const CANCELLABLE_STATUSES: ReadonlySet<LoginTransferStatus> = new Set(['WAITING', 'CLAIMED'])

const transfer = ref<LoginTransfer | null>(null)
const qrDataUrl = ref('')
const errorMessage = ref('')
const isLoading = ref(true)
const isDeciding = ref(false)
const now = ref(Date.now())

let pollTimer: ReturnType<typeof setTimeout> | null = null
let countdownTimer: ReturnType<typeof setInterval> | null = null
let closeTimer: ReturnType<typeof setTimeout> | null = null
let stopped = false

const status = computed<LoginTransferStatus>(() => transfer.value?.status ?? 'WAITING')
const isActive = computed(() => ACTIVE_LOGIN_TRANSFER_STATUSES.has(status.value))
const secondsRemaining = computed(() => {
    const expiresAt = transfer.value?.expiresAt
    if (!expiresAt) return 0
    return Math.max(0, Math.ceil((new Date(expiresAt).getTime() - now.value) / 1000))
})

/**
 * 安排下一次状态轮询并在回调末尾自我续期。
 * 组件卸载或交接进入终态后不再续期，避免留下僵尸定时器。
 */
const pollLater = () => {
    if (stopped || !isActive.value) return
    pollTimer = setTimeout(async () => {
        pollTimer = null
        const id = transfer.value?.id
        if (!id) return
        try {
            transfer.value = await api.loginTransferController.get({ id })
            errorMessage.value = ''
        } catch (error) {
            errorMessage.value = resolveErrorMessage(error, 'loginTransfer.pollFailed')
        }
        pollLater()
    }, POLL_INTERVAL_MS)
}

const decide = async (nextStatus: 'AUTHORIZED' | 'REJECTED') => {
    const id = transfer.value?.id
    if (!id || isDeciding.value) return
    if (pollTimer) {
        clearTimeout(pollTimer)
        pollTimer = null
    }
    isDeciding.value = true
    try {
        const result = await api.loginTransferController.update({
            id,
            body: { status: nextStatus },
        })
        transfer.value = result.transfer
        errorMessage.value = ''
    } catch (error) {
        errorMessage.value = resolveErrorMessage(error, 'loginTransfer.decisionFailed')
    } finally {
        isDeciding.value = false
        pollLater()
    }
}

const initialize = async () => {
    try {
        const serverUrl = assertReachableServerUrl(
            getPlatformRuntime().apiBaseUrl || window.location.origin,
        )
        const result = await api.loginTransferController.create()
        transfer.value = {
            id: result.id,
            status: result.status,
            createdAt: result.createdAt,
            expiresAt: result.expiresAt,
        }
        qrDataUrl.value = await QRCodeGenerator.toDataURL(
            encodeLoginTransferQr({ serverUrl, secret: result.secret }),
            {
                // 屏幕显示、近距离扫描，不存在印刷污损风险，用最低纠错换更少的模块数
                errorCorrectionLevel: 'L',
                width: 280,
                margin: 2,
                color: { dark: '#2C2825', light: '#FFFFFF' },
            },
        )
        countdownTimer = setInterval(() => {
            now.value = Date.now()
        }, POLL_INTERVAL_MS)
        pollLater()
    } catch (error) {
        errorMessage.value =
            error instanceof LoopbackServerUrlError
                ? t('loginTransfer.loopbackServer')
                : resolveErrorMessage(error, 'loginTransfer.createFailed')
    } finally {
        isLoading.value = false
    }
}

const stopCountdown = () => {
    if (countdownTimer) {
        clearInterval(countdownTimer)
        countdownTimer = null
    }
}

const cancelIfActive = () => {
    const id = transfer.value?.id
    if (id && CANCELLABLE_STATUSES.has(status.value)) {
        void api.loginTransferController.cancel({ id }).catch(() => undefined)
    }
}

/** 终态（过期/被拒/取消）后原地重新创建交接，不必关掉面板再走一遍入口。 */
const regenerate = () => {
    if (pollTimer) {
        clearTimeout(pollTimer)
        pollTimer = null
    }
    stopCountdown()
    transfer.value = null
    qrDataUrl.value = ''
    errorMessage.value = ''
    isLoading.value = true
    void initialize()
}

// 进入终态后倒计时不再展示，停掉定时器而不是空转到弹窗关闭
watch(isActive, (active) => {
    if (!active) stopCountdown()
})

watch(status, (next) => {
    if (next === 'COMPLETED' && !closeTimer) {
        closeTimer = setTimeout(() => emit('close'), AUTO_CLOSE_DELAY_MS)
    }
})

onMounted(() => void initialize())

onUnmounted(() => {
    stopped = true
    if (pollTimer) clearTimeout(pollTimer)
    if (closeTimer) clearTimeout(closeTimer)
    stopCountdown()
    cancelIfActive()
})
</script>

<template>
    <div class="flex w-full flex-col items-center text-center">
        <div v-if="isLoading" class="flex min-h-72 items-center justify-center text-[#8C857B]">
            <LoaderCircle :size="28" class="animate-spin" />
        </div>

        <template v-else-if="errorMessage && !transfer">
            <QrCode :size="42" class="mb-5 text-[#D6D1C4]" />
            <p class="mb-6 text-sm leading-6 text-[#B95D5D]">{{ errorMessage }}</p>
            <button type="button" class="outline-button px-6 py-2" @click="emit('close')">
                {{ t('common.close') }}
            </button>
        </template>

        <template v-else>
            <div
                v-if="qrDataUrl && status === 'WAITING'"
                class="mb-4 rounded bg-white p-3 shadow-sm"
            >
                <img :src="qrDataUrl" :alt="t('loginTransfer.qrAlt')" class="h-56 w-56" />
            </div>

            <template v-if="status === 'WAITING'">
                <h3 class="text-lg font-medium text-[#2B221B]">
                    {{ t('loginTransfer.scanTitle') }}
                </h3>
                <p class="mt-2 text-sm leading-6 text-[#8C857B]">
                    {{ t('loginTransfer.scanHint') }}
                </p>
            </template>

            <template v-else-if="status === 'CLAIMED'">
                <h3 class="text-lg font-medium text-[#2B221B]">
                    {{ t('loginTransfer.confirmTitle') }}
                </h3>
                <p class="mt-2 text-sm text-[#8C857B]">
                    {{ transfer?.deviceName }} · {{ transfer?.platform }}
                </p>
                <div class="mt-5 flex gap-3">
                    <button
                        type="button"
                        class="flex items-center gap-2 border border-[#D6D1C4] px-5 py-2 text-sm text-[#B95D5D]"
                        :disabled="isDeciding"
                        @click="decide('REJECTED')"
                    >
                        <X :size="16" /> {{ t('loginTransfer.reject') }}
                    </button>
                    <button
                        type="button"
                        class="flex items-center gap-2 bg-[#2C2825] px-5 py-2 text-sm text-white"
                        :disabled="isDeciding"
                        @click="decide('AUTHORIZED')"
                    >
                        <Check :size="16" /> {{ t('loginTransfer.authorize') }}
                    </button>
                </div>
            </template>

            <template v-else-if="status === 'AUTHORIZED'">
                <LoaderCircle :size="24" class="mb-3 animate-spin text-[#D98C28]" />
                <h3 class="text-lg font-medium text-[#2B221B]">
                    {{ t('loginTransfer.waitingForPhone') }}
                </h3>
            </template>

            <template v-else-if="status === 'COMPLETED'">
                <Check :size="36" class="mb-3 text-[#4D8B64]" />
                <h3 class="text-lg font-medium text-[#2B221B]">
                    {{ t('loginTransfer.completed') }}
                </h3>
            </template>

            <template v-else>
                <X :size="36" class="mb-3 text-[#B95D5D]" />
                <h3 class="text-lg font-medium text-[#2B221B]">
                    {{ t(`loginTransfer.status.${status.toLowerCase()}`) }}
                </h3>
                <button
                    type="button"
                    class="outline-button mt-5 px-6 py-2 text-sm"
                    @click="regenerate"
                >
                    {{ t('loginTransfer.regenerate') }}
                </button>
            </template>

            <p v-if="isActive" class="mt-4 text-xs text-[#AAA299]">
                {{ t('loginTransfer.expiresIn', { seconds: secondsRemaining }) }}
            </p>
            <p v-if="errorMessage" class="mt-3 text-sm text-[#B95D5D]">{{ errorMessage }}</p>

            <button
                v-if="status !== 'CLAIMED'"
                type="button"
                class="mt-6 text-sm text-[#8C857B] underline decoration-dotted underline-offset-4"
                @click="emit('close')"
            >
                {{ t('common.close') }}
            </button>
        </template>
    </div>
</template>
