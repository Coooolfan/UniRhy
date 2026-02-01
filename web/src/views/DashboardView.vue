<script setup lang="ts">
import { RouterView } from 'vue-router'
import NoiseTexture from '@/components/NoiseTexture.vue'
import DashboardSidebar from '@/components/dashboard/DashboardSidebar.vue'
import DashboardPlayerBar from '@/components/dashboard/DashboardPlayerBar.vue'
</script>

<template>
    <div
        class="flex h-screen w-full bg-[#EBE7E0] font-sans text-[#4A4A4A] overflow-hidden relative selection:bg-[#D4C5B0] selection:text-white"
    >
        <!-- Noise Texture Overlay -->
        <NoiseTexture />

        <!-- Sidebar -->
        <DashboardSidebar />

        <!-- Main Content -->
        <main class="flex-1 flex flex-col h-full relative z-10 overflow-y-auto no-scrollbar">
            <RouterView />
        </main>

        <!-- Bottom Player Bar -->
        <DashboardPlayerBar />
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
