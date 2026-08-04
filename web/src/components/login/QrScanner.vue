<script setup lang="ts">
import { onBeforeUnmount, onMounted } from 'vue'
import { X } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import { cancelQrScan, scanQrCodeWindowed } from '@/runtime/barcodeScanner'

/**
 * 页面内扫码取景层。
 *
 * 插件以 windowed 模式把相机预览铺在 WebView 后方并把 WebView 设为透明，
 * 本组件 Teleport 到 body：box-shadow 背板遮住全屏，中央透明镂空露出相机。
 * 扫码期间 html.qr-scan-windowed 隐藏 #app（见 style/main.css），镂空处因此只剩相机画面。
 */
defineProps<{ hint: string }>()
const emit = defineEmits<{
    scanned: [content: string]
    cancelled: []
    failed: [error: unknown]
}>()

const { t } = useI18n()

let stopped = false

onMounted(async () => {
    try {
        const content = await scanQrCodeWindowed()
        if (stopped) return
        if (content === null) {
            emit('cancelled')
        } else {
            emit('scanned', content)
        }
    } catch (error) {
        if (!stopped) emit('failed', error)
    }
})

onBeforeUnmount(() => {
    stopped = true
    void cancelQrScan()
})
</script>

<template>
    <Teleport to="body">
        <div class="fixed inset-0 z-50 flex flex-col items-center justify-center">
            <div class="relative aspect-square w-[min(72vw,300px)] shadow-[0_0_0_200vmax_#f9f7f2]">
                <span
                    class="absolute top-0 left-0 h-5 w-5 rounded-tl-xs border-t-2 border-l-2 border-[#f9f7f2] outline-1 outline-[#2c2825]/40"
                ></span>
                <span
                    class="absolute top-0 right-0 h-5 w-5 rounded-tr-xs border-t-2 border-r-2 border-[#f9f7f2] outline-1 outline-[#2c2825]/40"
                ></span>
                <span
                    class="absolute bottom-0 left-0 h-5 w-5 rounded-bl-xs border-b-2 border-l-2 border-[#f9f7f2] outline-1 outline-[#2c2825]/40"
                ></span>
                <span
                    class="absolute right-0 bottom-0 h-5 w-5 rounded-br-xs border-r-2 border-b-2 border-[#f9f7f2] outline-1 outline-[#2c2825]/40"
                ></span>
            </div>
            <p class="mt-6 px-8 text-center text-sm leading-6 text-[#5a534d]">{{ hint }}</p>
            <button
                type="button"
                class="mt-4 flex items-center gap-2 px-4 py-2 text-sm text-[#8a817c]"
                @click="cancelQrScan"
            >
                <X :size="16" /> {{ t('common.cancel') }}
            </button>
        </div>
    </Teleport>
</template>
