<script setup lang="ts">
import { ref } from 'vue'
import LightPillar from '@/components/LightPillar.vue'
import BrandLogo from '@/components/BrandLogo.vue'
import HeroSubtitle from '@/components/HeroSubtitle.vue'

const isChinese = ref(false)
</script>

<template>
  <div class="home-view relative h-full w-full">
    <LightPillar
      class="brightness-[0.7]"
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
    <div
      class="pointer-events-none absolute top-1/2 left-1/2 z-10 -translate-x-1/2 -translate-y-1/2 text-center text-white font-brand-sans pb-24"
    >
      <BrandLogo :isChinese="isChinese" />
      <HeroSubtitle :isChinese="isChinese" />
      <div
        class="pointer-events-auto flex items-center justify-center gap-4 text-[1.1rem] text-white/60"
      >
        <router-link
          class="text-inherit no-underline transition-all duration-400 ease-[cubic-bezier(0.25,0.46,0.45,0.94)] hover:text-white/90"
          to="/blog"
        >
          {{ isChinese ? '博客' : 'Blog' }}
        </router-link>
        <span class="cursor-default opacity-30">/</span>
        <a
          class="text-inherit no-underline transition-all duration-400 ease-[cubic-bezier(0.25,0.46,0.45,0.94)] hover:text-white/90"
          href="https://github.com/Coooolfan/UniRhy"
          target="_blank"
          rel="noopener noreferrer"
        >
          GitHub
        </a>
      </div>
    </div>
    <div class="absolute bottom-8 left-0 z-20 flex w-full justify-center">
      <div class="flex items-center gap-4 text-[1.1rem] text-white/40 select-none font-brand-sans">
        <span
          class="relative cursor-pointer transition-all duration-400 ease-[cubic-bezier(0.25,0.46,0.45,0.94)] hover:text-white/80"
          :class="
            isChinese
              ? 'scale-110 font-bold text-white [text-shadow:0_0_15px_rgba(255,255,255,0.6)]'
              : ''
          "
          @click="isChinese = true"
        >
          中文
        </span>
        <span class="cursor-default opacity-30">/</span>
        <span
          class="relative cursor-pointer transition-all duration-400 ease-[cubic-bezier(0.25,0.46,0.45,0.94)] hover:text-white/80"
          :class="
            !isChinese
              ? 'scale-110 font-bold text-white [text-shadow:0_0_15px_rgba(255,255,255,0.6)]'
              : ''
          "
          @click="isChinese = false"
        >
          English
        </span>
      </div>
    </div>
  </div>
</template>
