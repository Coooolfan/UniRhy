<script setup lang="ts">
import LightPillar from './components/LightPillar.vue'
</script>

<template>
  <div class="app-container">
    <LightPillar
      class="bg"
      topColor="#FFD700"
      bottomColor="#FF8C00"
      :intensity="1"
      :rotationSpeed="0.1"
      :glowAmount="0.002"
      :pillarWidth="6.3"
      :pillarHeight="0.1"
      :noiseIntensity="0.5"
      :pillarRotation="90"
      :interactive="false"
      mixBlendMode="normal"
    />
    <div class="overlay">
      <h1 class="brand">UniRhy</h1>
      <p class="subtitle">Just Listen / Cooming Soon</p>
    </div>
  </div>
</template>

<style>
/* Reset and full screen setup */
html,
body,
#app {
  margin: 0;
  padding: 0;
  width: 100%;
  height: 100%;
  background-color: #050505;
  overflow: hidden;
}

/* Mock Tailwind classes used in LightPillar.vue since Tailwind is not installed */
.w-full {
  width: 100%;
}
.h-full {
  height: 100%;
}
.absolute {
  position: absolute;
}
.top-0 {
  top: 0;
}
.left-0 {
  left: 0;
}
.flex {
  display: flex;
}
.items-center {
  align-items: center;
}
.justify-center {
  justify-content: center;
}
</style>

<style scoped>
.app-container {
  width: 100%;
  height: 100%;
  position: relative;
}

.overlay {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  text-align: center;
  color: white;
  z-index: 10;
  pointer-events: none;
  font-family: 'Barlow Condensed', sans-serif;
}

.bg {
     /*亮度降低*/
    filter: brightness(0.7)
}

.brand {
  font-size: 8rem;
  font-weight: 800;
  margin: 0;
  line-height: 1;
  letter-spacing: 0.05em;
  color: #ffffff;
  filter: drop-shadow(0 4px 8px rgba(0, 0, 0, 0.3)) drop-shadow(0 0 30px rgba(255, 215, 0, 0.5));
}

.subtitle {
  font-size: 1.5rem;
  margin: 1.5rem 0 0;
  font-weight: 300;
  letter-spacing: 0.5em;
  opacity: 1;
  text-transform: uppercase;
  text-shadow: 0 2px 4px rgba(0, 0, 0, 0.5);
  padding-bottom: 10rem;
}
</style>
