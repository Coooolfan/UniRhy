<script setup lang="ts">
import DashboardTopBar from '@/components/dashboard/DashboardTopBar.vue'
import DashboardHero from '@/components/dashboard/DashboardHero.vue'
import DashboardAlbumGrid from '@/components/dashboard/DashboardAlbumGrid.vue'
</script>

<template>
    <div class="pb-32">
        <DashboardTopBar />
        <div class="mt-2 px-4 sm:px-6 lg:px-8">
            <DashboardHero />
            <DashboardAlbumGrid />
        </div>
    </div>
</template>
