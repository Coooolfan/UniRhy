<script setup lang="ts">
import { computed } from 'vue'
import { RouterView, useRoute } from 'vue-router'
import AudioPlayer from '@/components/AudioPlayer.vue'
import AppModalHost from '@/components/modals/AppModalHost.vue'
import { getPlatformRuntime } from '@/runtime/platform'

const route = useRoute()
const desktopPlatform = getPlatformRuntime().platform
const isMacDesktop = desktopPlatform === 'macos'
const usesCustomTitleBar = desktopPlatform === 'macos' || desktopPlatform === 'windows'
const isAppRoute = computed(() => route.matched.some((record) => record.meta.requiresAuth))
const dragRegionClass = computed(() => {
    const heightClass = isAppRoute.value ? 'h-32' : 'h-16'
    const leftClass = isMacDesktop ? 'left-24' : 'left-0'

    return `fixed ${leftClass} top-0 z-40 ${heightClass} w-64`
})

const startWindowDrag = async (event: MouseEvent) => {
    if (!usesCustomTitleBar || event.button !== 0) {
        return
    }

    const { getCurrentWindow } = await import('@tauri-apps/api/window')
    await getCurrentWindow().startDragging()
}
</script>

<template>
    <div
        v-if="usesCustomTitleBar"
        :class="dragRegionClass"
        aria-hidden="true"
        @mousedown="startWindowDrag"
    ></div>
    <RouterView />
    <AudioPlayer />
    <AppModalHost />
</template>

<style scoped></style>
