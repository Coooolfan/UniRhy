<script setup lang="ts">
import { onBeforeUnmount, onMounted, useTemplateRef } from 'vue'
import { cancelQrScan, scanQrCodeWindowed } from '@/runtime/barcodeScanner'

/**
 * 页面内扫码取景框，随所在容器内联布局。
 *
 * 插件以 windowed 模式把相机预览铺在 WebView 后方并把 WebView 设为透明，
 * 页面自身即遮罩：本组件把取景框的位置尺寸写入 CSS 变量，全局样式据此把同一块
 * 矩形从应用的合成结果中挖空（见 style/main.css），相机因此只在框内可见。
 */
defineProps<{ hint: string }>()
const emit = defineEmits<{
    scanned: [content: string]
    cancelled: []
    failed: [error: unknown]
}>()

/** 扫码期间挂在 `<html>` 上的样式类，全局样式据此挖出取景镂空。 */
const WINDOWED_SCAN_CLASS = 'qr-scan-windowed'

const viewfinder = useTemplateRef<HTMLElement>('viewfinder')

let stopped = false
let frameObserver: ResizeObserver | null = null

/** 镂空矩形的位置与尺寸，取值由取景框实测得出。 */
const CUTOUT_VARIABLES = [
    '--qr-cutout-x',
    '--qr-cutout-y',
    '--qr-cutout-width',
    '--qr-cutout-height',
] as const

/** 把取景框相对 `#app` 的位置写入 CSS 变量，使镂空始终与取景框对齐。 */
const syncCutout = () => {
    const app = document.querySelector('#app')
    if (!viewfinder.value || !app) return
    const frame = viewfinder.value.getBoundingClientRect()
    const origin = app.getBoundingClientRect()
    const { style } = document.documentElement
    style.setProperty('--qr-cutout-x', `${frame.left - origin.left}px`)
    style.setProperty('--qr-cutout-y', `${frame.top - origin.top}px`)
    style.setProperty('--qr-cutout-width', `${frame.width}px`)
    style.setProperty('--qr-cutout-height', `${frame.height}px`)
}

onMounted(async () => {
    syncCutout()
    // 卡片切换与字体加载可能微调布局，下一帧再对齐一次
    requestAnimationFrame(syncCutout)
    document.documentElement.classList.add(WINDOWED_SCAN_CLASS)
    frameObserver = new ResizeObserver(syncCutout)
    frameObserver.observe(document.documentElement)
    if (viewfinder.value) frameObserver.observe(viewfinder.value)
    window.addEventListener('scroll', syncCutout, true)

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
    frameObserver?.disconnect()
    frameObserver = null
    window.removeEventListener('scroll', syncCutout, true)
    document.documentElement.classList.remove(WINDOWED_SCAN_CLASS)
    for (const variable of CUTOUT_VARIABLES) {
        document.documentElement.style.removeProperty(variable)
    }
    void cancelQrScan()
})
</script>

<template>
    <div class="flex w-full flex-col items-center">
        <!-- 边框用 outline 画在 border-box 外侧，避免与镂空区域重叠而被一并挖掉 -->
        <div
            ref="viewfinder"
            class="aspect-square w-[min(100%,15rem)] outline-2 outline-[#d98c28]/70"
        ></div>
        <p class="mt-4 px-2 text-center text-sm leading-6 text-[#5a534d]">{{ hint }}</p>
    </div>
</template>
