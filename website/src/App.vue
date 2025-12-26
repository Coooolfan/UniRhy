<script setup lang="ts">
import { ref } from 'vue'
import LightPillar from './components/LightPillar.vue'
import BrandLogo from './components/BrandLogo.vue'
import HeroSubtitle from './components/HeroSubtitle.vue'

const isChinese = ref(false)
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
      <BrandLogo :isChinese="isChinese" />
      <HeroSubtitle :isChinese="isChinese" />
    </div>
    <div class="footer">
      <div class="lang-switch">
        <span class="lang-opt" :class="{ active: isChinese }" @click="isChinese = true">
          中文
        </span>
        <span class="separator">/</span>
        <span class="lang-opt" :class="{ active: !isChinese }" @click="isChinese = false">
          English
        </span>
      </div>
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
  filter: brightness(0.7);
}

.footer {
  position: absolute;
  bottom: 2rem;
  left: 0;
  width: 100%;
  display: flex;
  justify-content: center;
  z-index: 20;
}

.lang-switch {
  display: flex;
  align-items: center;
  gap: 1rem;
  font-family: 'Barlow Condensed', sans-serif;
  font-size: 1.1rem;
  color: rgba(255, 255, 255, 0.4);
  user-select: none;
}

.lang-opt {
  cursor: pointer;
  transition: all 0.4s cubic-bezier(0.25, 0.46, 0.45, 0.94);
  position: relative;
}

.lang-opt:hover {
  color: rgba(255, 255, 255, 0.8);
}

.lang-opt.active {
  color: #fff;
  font-weight: 700;
  text-shadow: 0 0 15px rgba(255, 255, 255, 0.6);
  transform: scale(1.1);
}

.separator {
  cursor: default;
  opacity: 0.3;
}
</style>
