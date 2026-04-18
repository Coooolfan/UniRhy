<script setup lang="ts">
import { computed } from 'vue'
import { RouterView, useRoute } from 'vue-router'
import AudioPlayer from '@/components/AudioPlayer.vue'
import AppModalHost from '@/components/modals/AppModalHost.vue'
import { getPlatformRuntime } from '@/runtime/platform'

const route = useRoute()
const isMacDesktop = getPlatformRuntime().platform === 'macos'
const dragRegionClass = computed(() =>
    route.path.startsWith('/dashboard')
        ? 'fixed left-0 top-0 z-40 h-32 w-64'
        : 'fixed left-0 top-0 z-40 h-16 w-64',
)

const startMacWindowDrag = async (event: MouseEvent) => {
    if (!isMacDesktop || event.button !== 0) {
        return
    }

    const { getCurrentWindow } = await import('@tauri-apps/api/window')
    await getCurrentWindow().startDragging()
}
</script>

<template>
    <div
        v-if="isMacDesktop"
        :class="dragRegionClass"
        aria-hidden="true"
        @mousedown="startMacWindowDrag"
    ></div>
    <RouterView />
    <AudioPlayer />
    <AppModalHost />
</template>

<style scoped></style>
