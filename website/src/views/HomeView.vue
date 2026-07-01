<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useHead } from '@unhead/vue'
import LightPillar from '@/components/LightPillar.vue'
import BrandLogo from '@/components/BrandLogo.vue'
import HeroSubtitle from '@/components/HeroSubtitle.vue'
import { useLang } from '@/composables/useLang'

const { lang, setLang } = useLang()
const isChinese = computed(() => lang.value === 'zh')
const blogPath = computed(() => `/${lang.value}/blog`)
const docsPath = computed(() => `/${lang.value}/docs`)
const profilingEnabled = computed(() => {
  if (typeof window === 'undefined') return false
  const params = new URLSearchParams(window.location.search)
  return params.get('profileLightPillar') === '1'
})
const profilingPixelRatio = computed(() => {
  if (typeof window === 'undefined' || !profilingEnabled.value) return undefined
  const params = new URLSearchParams(window.location.search)
  const rawValue = Number(params.get('lightPillarPixelRatio'))
  if (!Number.isFinite(rawValue) || rawValue <= 0) return undefined
  return Math.min(rawValue, 2)
})
function profilingNumberParam(name: string, min: number, max: number): number | undefined {
  if (typeof window === 'undefined' || !profilingEnabled.value) return undefined
  const params = new URLSearchParams(window.location.search)
  const value = params.get(name)
  if (value === null) return undefined
  const rawValue = Number(value)
  if (!Number.isFinite(rawValue)) return undefined
  return Math.min(Math.max(rawValue, min), max)
}

const profilingRenderScaleX = computed(() => profilingNumberParam('lightPillarScaleX', 0.1, 1))
const profilingRenderScaleY = computed(() => profilingNumberParam('lightPillarScaleY', 0.1, 1))
const profilingRaySteps = computed(() => profilingNumberParam('lightPillarRaySteps', 1, 100))
const profilingMaxDepth = computed(() => profilingNumberParam('lightPillarMaxDepth', 1, 100))
const profilingRayStepScale = computed(() => profilingNumberParam('lightPillarStepScale', 0.1, 4))
const profilingWaveOctaves = computed(() => profilingNumberParam('lightPillarWaveOctaves', 1, 4))
const profilingFixedTime = computed(() => profilingNumberParam('lightPillarFixedTime', 0, 100000))
const profilerSummary = ref<LightPillarProfilingSummary | null>(null)
let profilerRefreshTimer: number | null = null

function refreshProfilerSummary() {
  profilerSummary.value = window.unirhyLightPillarProfiler?.summary ?? null
}

function resetProfilerSummary() {
  window.unirhyLightPillarProfiler?.reset()
  refreshProfilerSummary()
}

function downloadProfilerSummary() {
  window.unirhyLightPillarProfiler?.download()
}

function formatMetric(value: number | null, digits = 2): string {
  if (value === null) return 'N/A'
  return value.toFixed(digits)
}

onMounted(() => {
  if (!profilingEnabled.value) return
  refreshProfilerSummary()
  profilerRefreshTimer = window.setInterval(refreshProfilerSummary, 500)
})

onBeforeUnmount(() => {
  if (profilerRefreshTimer !== null) {
    clearInterval(profilerRefreshTimer)
  }
})

useHead(() => ({
  title: 'UniRhy · 独一律',
  htmlAttrs: { lang: lang.value === 'zh' ? 'zh-CN' : 'en' },
  meta: [
    {
      name: 'description',
      content:
        lang.value === 'zh'
          ? 'UniRhy（独一律）是一个私有化的音乐流媒体平台。'
          : 'UniRhy is a self-hosted music streaming platform.',
    },
  ],
}))
</script>

<template>
  <div class="home-view relative h-full w-full">
    <LightPillar
      className=""
      topColor="#FFD700"
      bottomColor="#FF8C00"
      :intensity="0.7"
      :rotationSpeed="0.1"
      :glowAmount="0.002"
      :pillarWidth="6.3"
      :pillarHeight="0.1"
      :noiseIntensity="0.5"
      :pillarRotation="90"
      :interactive="false"
      :profiling="profilingEnabled"
      :pixelRatioOverride="profilingPixelRatio"
      :renderScaleX="profilingRenderScaleX"
      :renderScaleY="profilingRenderScaleY"
      :maxRaySteps="profilingRaySteps"
      :maxDepth="profilingMaxDepth"
      :rayStepScale="profilingRayStepScale"
      :waveOctaves="profilingWaveOctaves"
      :fixedTime="profilingFixedTime"
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
          :to="docsPath"
        >
          {{ isChinese ? '文档' : 'Docs' }}
        </router-link>
        <span class="cursor-default opacity-30">/</span>
        <router-link
          class="text-inherit no-underline transition-all duration-400 ease-[cubic-bezier(0.25,0.46,0.45,0.94)] hover:text-white/90"
          :to="blogPath"
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
          @click="setLang('zh')"
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
          @click="setLang('en')"
        >
          English
        </span>
      </div>
    </div>
    <div
      v-if="profilingEnabled"
      class="absolute top-4 left-4 z-30 max-w-[24rem] rounded-md bg-black/65 px-4 py-3 text-[0.8rem] leading-5 text-white/88 backdrop-blur-sm"
    >
      <div class="flex items-center justify-between gap-4">
        <div class="font-brand-sans text-[0.95rem] tracking-[0.15em] uppercase">
          Light Pillar Profile
        </div>
        <div class="flex items-center gap-2 text-[0.72rem]">
          <button
            class="cursor-pointer rounded border border-white/20 px-2 py-1 text-white/85 transition hover:border-white/40 hover:text-white"
            type="button"
            @click="resetProfilerSummary"
          >
            Reset
          </button>
          <button
            class="cursor-pointer rounded border border-white/20 px-2 py-1 text-white/85 transition hover:border-white/40 hover:text-white"
            type="button"
            @click="downloadProfilerSummary"
          >
            Export
          </button>
        </div>
      </div>
      <div v-if="profilerSummary" class="mt-3 space-y-1 text-white/78">
        <div>
          target {{ profilerSummary.targetFrameRate }}fps |
          {{ formatMetric(profilerSummary.targetFrameTimeMs) }}ms budget
        </div>
        <div>
          fps {{ formatMetric(profilerSummary.fps, 1) }} | frame p95
          {{ formatMetric(profilerSummary.p95FrameIntervalMs) }}ms
        </div>
        <div>
          callback avg/p95 {{ formatMetric(profilerSummary.avgCallbackMs) }} /
          {{ formatMetric(profilerSummary.p95CallbackMs) }}ms
        </div>
        <div>
          render cpu avg/p95 {{ formatMetric(profilerSummary.avgRenderCpuMs) }} /
          {{ formatMetric(profilerSummary.p95RenderCpuMs) }}ms
        </div>
        <div>
          gpu avg/p95 {{ formatMetric(profilerSummary.avgGpuMs) }} /
          {{ formatMetric(profilerSummary.p95GpuMs) }}ms
        </div>
        <div>gpu samples {{ profilerSummary.gpuSampleCount }}</div>
        <div>
          gpu work {{ formatMetric(profilerSummary.gpuWorkMsPerSecond, 0) }}ms/s |
          {{ formatMetric(profilerSummary.gpuFrameBudgetRatio * 100, 0) }}%
        </div>
        <div>
          dropped {{ profilerSummary.droppedFrames }} |
          {{ formatMetric(profilerSummary.droppedFrameRatio * 100, 1) }}%
        </div>
        <div>
          long tasks {{ profilerSummary.longTaskCount }} | max
          {{ formatMetric(profilerSummary.maxLongTaskMs) }}ms
        </div>
        <div>
          canvas {{ profilerSummary.canvasWidth }}x{{ profilerSummary.canvasHeight }} @
          {{ formatMetric(profilerSummary.pixelRatio, 2) }}x
        </div>
        <div>render {{ profilerSummary.renderWidth }}x{{ profilerSummary.renderHeight }}</div>
        <div>
          {{ profilerSummary.webglVersion }} |
          {{ profilerSummary.gpuTimerSupported ? 'gpu timer on' : 'gpu timer off' }}
        </div>
      </div>
      <div v-else class="mt-3 text-white/60">Collecting samples...</div>
    </div>
  </div>
</template>
