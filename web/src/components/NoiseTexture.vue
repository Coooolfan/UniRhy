<script setup lang="ts">
import noiseUrl from '@/assets/noise.svg'
</script>

<template>
    <div
        class="absolute inset-0 opacity-[0.03] pointer-events-none mix-blend-multiply"
        :style="{ backgroundImage: `url(${noiseUrl})` }"
    ></div>
</template>
