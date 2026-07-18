<script setup lang="ts">
import {
  computed,
  defineAsyncComponent,
  onBeforeUnmount,
  onMounted,
  ref,
  type CSSProperties,
} from 'vue'
import LightPillarWebGL from '@/components/LightPillarWebGL.vue'
import { DEFAULT_HDR_TONE_CURVE, type HdrToneCurve } from '@/components/hdrToneCurve'

interface LightPillarProps {
  topColor?: string
  bottomColor?: string
  intensity?: number
  rotationSpeed?: number
  interactive?: boolean
  className?: string
  glowAmount?: number
  pillarWidth?: number
  pillarHeight?: number
  noiseIntensity?: number
  mixBlendMode?: CSSProperties['mixBlendMode']
  pillarRotation?: number
  toneCurve?: HdrToneCurve
}

type HdrPreference = 'auto' | 'on' | 'off'
type RenderMode = 'sdr-webgl' | 'hdr-pending' | 'hdr-webgpu'

const props = withDefaults(defineProps<LightPillarProps>(), {
  topColor: '#48FF28',
  bottomColor: '#9EF19E',
  intensity: 1.0,
  rotationSpeed: 0.3,
  interactive: false,
  className: '',
  glowAmount: 0.005,
  pillarWidth: 3.0,
  pillarHeight: 0.4,
  noiseIntensity: 0.5,
  mixBlendMode: 'screen',
  pillarRotation: 0,
  toneCurve: () => ({ ...DEFAULT_HDR_TONE_CURVE }),
})

const LightPillarWebGPU = defineAsyncComponent(() => import('@/components/LightPillarWebGPU.vue'))

const renderMode = ref<RenderMode>('sdr-webgl')
const hdrPreference = ref<HdrPreference>('auto')
const useHdrComponent = computed(() => renderMode.value !== 'sdr-webgl')
const rendererProps = computed(() => {
  const { toneCurve: _toneCurve, ...rendererOptions } = props
  return rendererOptions
})

let hdrMediaQuery: MediaQueryList | null = null

function readHdrPreference(): HdrPreference {
  const value = new URLSearchParams(window.location.search).get('hdr')
  return value === 'on' || value === 'off' ? value : 'auto'
}

function canAttemptHdr() {
  if (hdrPreference.value === 'off' || !('gpu' in navigator)) return false
  return hdrPreference.value === 'on' || hdrMediaQuery?.matches === true
}

function selectRenderer() {
  if (canAttemptHdr()) {
    if (renderMode.value === 'sdr-webgl') renderMode.value = 'hdr-pending'
    return
  }

  renderMode.value = 'sdr-webgl'
}

function handleHdrReady() {
  renderMode.value = 'hdr-webgpu'
}

function handleHdrUnavailable() {
  renderMode.value = 'sdr-webgl'
}

function handleDynamicRangeChange() {
  if (hdrPreference.value === 'auto') selectRenderer()
}

onMounted(() => {
  hdrPreference.value = readHdrPreference()
  hdrMediaQuery = window.matchMedia('(dynamic-range: high)')
  hdrMediaQuery.addEventListener('change', handleDynamicRangeChange)
  selectRenderer()
})

onBeforeUnmount(() => {
  hdrMediaQuery?.removeEventListener('change', handleDynamicRangeChange)
})
</script>

<template>
  <div
    class="absolute top-0 left-0 h-full w-full"
    :class="className"
    :data-hdr-preference="hdrPreference"
    :data-render-mode="renderMode"
    :style="{ mixBlendMode }"
  >
    <LightPillarWebGL v-if="!useHdrComponent" v-bind="rendererProps" mixBlendMode="normal" />
    <LightPillarWebGPU
      v-else
      v-bind="rendererProps"
      :toneCurve="toneCurve"
      @ready="handleHdrReady"
      @unavailable="handleHdrUnavailable"
    />
  </div>
</template>
