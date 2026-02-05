<script setup lang="ts">
import DashboardTopBar from '@/components/dashboard/DashboardTopBar.vue'
import DashboardHero from '@/components/dashboard/DashboardHero.vue'
import DashboardAlbumGrid from '@/components/dashboard/DashboardAlbumGrid.vue'
</script>

<template>
    <div class="pb-32">
        <DashboardTopBar />
        <div class="px-8 mt-2">
            <DashboardHero />
            <DashboardAlbumGrid />
        </div>
    </div>
</template>
