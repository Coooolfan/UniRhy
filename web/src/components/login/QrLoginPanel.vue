<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { LoaderCircle, ScanLine, Settings, X } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import { api, saveAuthToken } from '@/ApiInstance'
import type { LoginTransferPlatform } from '@/__generated/model/enums'
import { resolveErrorMessage } from '@/i18n/errors'
import {
    CameraPermissionDeniedError,
    cancelQrScan,
    openCameraSettings,
    scanQrCode,
} from '@/runtime/barcodeScanner'
import { getClientVersion, getPlatformRuntime, persistApiBaseUrl } from '@/runtime/platform'
import { isMobilePlatform, type MobilePlatformKind } from '@/runtime/platform.shared'
import { parseLoginTransferQrPayload } from '@/services/loginTransferQr'
import { ACTIVE_LOGIN_TRANSFER_STATUSES } from '@/services/loginTransferStatus'

const emit = defineEmits<{ cancel: []; loggedIn: [] }>()
const { t } = useI18n()

type Phase = 'scanning' | 'claiming' | 'waiting' | 'authorized'

const POLL_INTERVAL_MS = 1000

const DEVICE_NAME_KEYS: Record<MobilePlatformKind, string> = {
    android: 'loginTransfer.androidDevice',
    ios: 'loginTransfer.iosDevice',
}

const PHASE_TITLE_KEYS: Record<Exclude<Phase, 'scanning'>, string> = {
    claiming: 'loginTransfer.connecting',
    waiting: 'loginTransfer.waitingForApproval',
    authorized: 'loginTransfer.signingIn',
}

const phase = ref<Phase>('scanning')
const errorMessage = ref('')
const permissionDenied = ref(false)
const targetServer = ref('')

let pollTimer: ReturnType<typeof setTimeout> | null = null
let stopped = false

const phaseTitle = computed(() =>
    phase.value === 'scanning' ? '' : t(PHASE_TITLE_KEYS[phase.value]),
)

const poll = async (transferId: string, claimToken: string) => {
    try {
        const transfer = await api.loginTransferController.get({
            id: transferId,
            authorization: `Bearer ${claimToken}`,
        })
        if (transfer.status === 'AUTHORIZED') {
            phase.value = 'authorized'
            const result = await api.loginTransferController.createToken({
                id: transferId,
                authorization: `Bearer ${claimToken}`,
            })
            saveAuthToken(result.token)
            emit('loggedIn')
            return
        }
        if (!ACTIVE_LOGIN_TRANSFER_STATUSES.has(transfer.status)) {
            errorMessage.value = t(`loginTransfer.status.${transfer.status.toLowerCase()}`)
            return
        }
        if (stopped) return
        pollTimer = setTimeout(() => void poll(transferId, claimToken), POLL_INTERVAL_MS)
    } catch (error) {
        errorMessage.value = resolveErrorMessage(error, 'loginTransfer.pollFailed')
    }
}

const claimTransfer = async (rawPayload: string) => {
    phase.value = 'claiming'
    errorMessage.value = ''
    const previousServerUrl = getPlatformRuntime().apiBaseUrl
    try {
        const payload = parseLoginTransferQrPayload(rawPayload)
        targetServer.value = payload.serverUrl
        const platform = getPlatformRuntime().platform
        if (!isMobilePlatform(platform)) {
            throw new Error('Unsupported mobile platform')
        }
        // 认领与后续轮询都必须打到二维码指向的实例，因此先切换并持久化后端地址
        await persistApiBaseUrl(payload.serverUrl)
        const clientVersion = await getClientVersion()
        const result = await api.loginTransferController.update({
            id: payload.transferId,
            body: {
                status: 'CLAIMED',
                secret: payload.secret,
                deviceName: t(DEVICE_NAME_KEYS[platform]),
                platform: platform.toUpperCase() as LoginTransferPlatform,
                ...(clientVersion ? { clientVersion } : {}),
            },
        })
        const claimToken = result.claimAccessToken
        if (!claimToken) throw new Error('Missing claim token')
        phase.value = 'waiting'
        void poll(payload.transferId, claimToken)
    } catch (error) {
        // 认领失败则退回原来的实例地址，避免把用户留在一个扫错的服务端上
        if (previousServerUrl) {
            await persistApiBaseUrl(previousServerUrl).catch(() => undefined)
        }
        targetServer.value = ''
        phase.value = 'scanning'
        errorMessage.value = resolveErrorMessage(error, 'loginTransfer.scanFailed')
    }
}

const startScanner = async () => {
    errorMessage.value = ''
    permissionDenied.value = false
    phase.value = 'scanning'
    try {
        const content = await scanQrCode()
        if (stopped) return
        if (content === null) {
            emit('cancel')
            return
        }
        await claimTransfer(content)
    } catch (error) {
        if (error instanceof CameraPermissionDeniedError) {
            permissionDenied.value = true
        }
        errorMessage.value = resolveErrorMessage(error, 'loginTransfer.cameraFailed')
    }
}

onMounted(() => void startScanner())

onUnmounted(() => {
    stopped = true
    if (pollTimer) clearTimeout(pollTimer)
    void cancelQrScan()
})
</script>

<template>
    <div class="flex flex-1 flex-col items-center justify-center text-center">
        <div v-if="phase === 'scanning'" class="flex min-h-64 flex-col items-center justify-center">
            <ScanLine :size="36" class="mb-4 text-[#8A817C]" />
            <p class="text-sm leading-6 text-[#8A817C]">
                {{ t('loginTransfer.cameraHint') }}
            </p>
        </div>

        <div v-else class="flex min-h-64 flex-col items-center justify-center">
            <LoaderCircle :size="30" class="mb-4 animate-spin text-[#D98C28]" />
            <h3 class="text-lg text-[#2C2825]">{{ phaseTitle }}</h3>
            <p v-if="targetServer" class="mt-2 break-all text-xs text-[#8A817C]">
                {{ targetServer }}
            </p>
        </div>

        <p v-if="errorMessage" class="mt-4 text-sm leading-6 text-[#B95D5D]">
            {{ errorMessage }}
        </p>
        <div class="mt-5 flex flex-wrap items-center justify-center gap-4">
            <button
                v-if="permissionDenied"
                type="button"
                class="flex items-center gap-2 px-4 py-2 text-sm text-[#8A817C]"
                @click="openCameraSettings"
            >
                <Settings :size="16" /> {{ t('loginTransfer.openCameraSettings') }}
            </button>
            <button
                v-if="errorMessage && phase === 'scanning'"
                type="button"
                class="outline-button px-5 py-2 text-sm"
                @click="startScanner"
            >
                {{ t('common.retry') }}
            </button>
            <button
                type="button"
                class="flex items-center gap-2 px-4 py-2 text-sm text-[#8A817C]"
                @click="emit('cancel')"
            >
                <X :size="16" /> {{ t('common.cancel') }}
            </button>
        </div>
    </div>
</template>
