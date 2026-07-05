<script setup lang="ts">
import { computed, onMounted, onUnmounted } from 'vue'
import { RouterView, useRoute } from 'vue-router'
import AudioPlayer from '@/components/AudioPlayer.vue'
import AppModalHost from '@/components/modals/AppModalHost.vue'
import { getPlatformRuntime } from '@/runtime/platform'

const route = useRoute()
const desktopPlatform = getPlatformRuntime().platform
const isMacDesktop = desktopPlatform === 'macos'
const usesCustomTitleBar = desktopPlatform === 'macos' || desktopPlatform === 'windows'
const isAppRoute = computed(() => route.matched.some((record) => record.meta.requiresAuth))
const dragRegionClass = 'pointer-events-none fixed z-40'
const sidebarDragRegionClass = `${dragRegionClass} left-0 top-0 hidden h-screen w-64 md:block`
const topBarDragRegionClass = `${dragRegionClass} left-64 right-0 top-0 hidden h-16 md:block`

const isInteractiveTarget = (target: EventTarget | null) =>
    target instanceof Element && Boolean(target.closest('button, input, textarea, select, a'))

const isInDashboardDragRegion = (event: MouseEvent) => {
    if (!isAppRoute.value || event.button !== 0 || isInteractiveTarget(event.target)) {
        return false
    }

    const sidebarWidth = 256
    const topBarHeight = 64

    return event.clientX < sidebarWidth || event.clientY < topBarHeight
}

const startWindowDrag = async (event: MouseEvent) => {
    if (!usesCustomTitleBar || !isInDashboardDragRegion(event)) {
        return
    }

    const { getCurrentWindow } = await import('@tauri-apps/api/window')
    await getCurrentWindow().startDragging()
}

onMounted(() => {
    window.addEventListener('mousedown', startWindowDrag)
})

onUnmounted(() => {
    window.removeEventListener('mousedown', startWindowDrag)
})
</script>

<template>
    <template v-if="usesCustomTitleBar && isAppRoute">
        <div :class="sidebarDragRegionClass" aria-hidden="true"></div>
        <div :class="topBarDragRegionClass" aria-hidden="true"></div>
    </template>
    <RouterView />
    <AudioPlayer />
    <AppModalHost />
</template>

<style scoped></style>
