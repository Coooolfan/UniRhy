<script setup lang="ts">
import { onMounted, onUnmounted, watch } from 'vue'
import { RouterView, useRoute } from 'vue-router'
import NoiseTexture from '@/components/NoiseTexture.vue'
import DashboardSidebar from '@/components/dashboard/DashboardSidebar.vue'
import { useMediaSession } from '@/composables/useMediaSession'
import { provideDashboardLayout } from '@/composables/useDashboardLayout'
import { useAudioStore } from '@/stores/audio'

const audioStore = useAudioStore()
const route = useRoute()
const dashboardLayout = provideDashboardLayout()

useMediaSession(audioStore)

onMounted(() => {
    audioStore.connectPlaybackSync()
})

onUnmounted(() => {
    audioStore.disconnectPlaybackSync()
})

watch(
    () => route.fullPath,
    () => {
        dashboardLayout.closeMobileSidebar()
    },
)
</script>

<template>
    <div
        class="relative flex h-[100dvh] w-full overflow-hidden bg-dashboard-main font-sans text-[#4A4A4A] selection:bg-[#D4C5B0] selection:text-white"
    >
        <!-- Noise Texture Overlay -->
        <NoiseTexture />

        <!-- Sidebar -->
        <DashboardSidebar />

        <!-- Main Content -->
        <main
            class="no-scrollbar relative z-10 flex h-full min-w-0 flex-1 flex-col overflow-x-hidden overflow-y-auto"
        >
            <RouterView />
        </main>
    </div>
</template>

<style>
/* Hide scrollbar for Chrome, Safari and Opera */
.no-scrollbar::-webkit-scrollbar {
    display: none;
}

/* Hide scrollbar for IE, Edge and Firefox */
.no-scrollbar {
    -ms-overflow-style: none; /* IE and Edge */
    scrollbar-width: none; /* Firefox */
}
</style>
