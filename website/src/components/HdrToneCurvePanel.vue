<script setup lang="ts">
import { computed } from 'vue'
import { DEFAULT_HDR_TONE_CURVE, type HdrToneCurve } from '@/components/hdrToneCurve'

const props = defineProps<{
  modelValue: HdrToneCurve
}>()

const emit = defineEmits<{
  'update:modelValue': [value: HdrToneCurve]
}>()

const chartWidth = 288
const chartHeight = 144
const chartPadding = 14
const maxInput = 6
const maxOutput = 3.2
const highlightTransitionWidth = 1.5

function smoothstep(edge0: number, edge1: number, value: number) {
  const normalized = Math.min(1, Math.max(0, (value - edge0) / (edge1 - edge0)))
  return normalized * normalized * (3 - 2 * normalized)
}

function mapTone(input: number, curve: HdrToneCurve) {
  const reference = Math.tanh(input) ** curve.gamma * curve.base
  const highlightMask = smoothstep(
    curve.highlightStart,
    curve.highlightStart + highlightTransitionWidth,
    input,
  )
  const highlight =
    Math.min(Math.max(input - curve.highlightStart, 0) * curve.highlightGain, 3.1) * highlightMask
  return Math.min(maxOutput, reference + highlight)
}

const curvePath = computed(() => {
  const points = 96
  const plotWidth = chartWidth - chartPadding * 2
  const plotHeight = chartHeight - chartPadding * 2

  return Array.from({ length: points + 1 }, (_, index) => {
    const input = (index / points) * maxInput
    const output = mapTone(input, props.modelValue)
    const x = chartPadding + (input / maxInput) * plotWidth
    const y = chartHeight - chartPadding - (output / maxOutput) * plotHeight
    return `${index === 0 ? 'M' : 'L'} ${x.toFixed(2)} ${y.toFixed(2)}`
  }).join(' ')
})

const sdrLineY = computed(
  () => chartHeight - chartPadding - (1 / maxOutput) * (chartHeight - chartPadding * 2),
)

function updateValue(key: keyof HdrToneCurve, event: Event) {
  const value = Number((event.target as HTMLInputElement).value)
  emit('update:modelValue', { ...props.modelValue, [key]: value })
}

function reset() {
  emit('update:modelValue', { ...DEFAULT_HDR_TONE_CURVE })
}

function formatBias(value: number) {
  if (Math.abs(value) < 0.01) return 'Center'
  return `${value > 0 ? 'R' : 'L'} ${Math.round(Math.abs(value) * 100)}%`
}
</script>

<template>
  <aside
    class="hdr-tone-panel"
    data-hdr-tone-controls
    :data-tone-base="modelValue.base"
    :data-tone-gamma="modelValue.gamma"
    :data-tone-highlight-start="modelValue.highlightStart"
    :data-tone-highlight-gain="modelValue.highlightGain"
    :data-tone-highlight-bias="modelValue.highlightBias"
    aria-label="HDR tone curve controls"
  >
    <header class="hdr-tone-header">
      <div>
        <strong>HDR Tone Curve</strong>
        <small>MacBook Pro XDR · 3.2×</small>
      </div>
      <button type="button" @click="reset">Reset</button>
    </header>

    <svg
      class="hdr-tone-chart"
      :viewBox="`0 0 ${chartWidth} ${chartHeight}`"
      role="img"
      aria-label="Live HDR tone curve"
    >
      <line
        :x1="chartPadding"
        :x2="chartWidth - chartPadding"
        :y1="sdrLineY"
        :y2="sdrLineY"
        class="hdr-tone-sdr-line"
      />
      <path :d="curvePath" class="hdr-tone-curve" />
    </svg>

    <label>
      <span
        >Base <output>{{ modelValue.base.toFixed(2) }}</output></span
      >
      <input
        type="range"
        min="0.1"
        max="0.4"
        step="0.01"
        :value="modelValue.base"
        @input="updateValue('base', $event)"
      />
    </label>

    <label>
      <span
        >Gamma <output>{{ modelValue.gamma.toFixed(2) }}</output></span
      >
      <input
        type="range"
        min="0.6"
        max="1.2"
        step="0.01"
        :value="modelValue.gamma"
        @input="updateValue('gamma', $event)"
      />
    </label>

    <label>
      <span
        >HDR Start <output>{{ modelValue.highlightStart.toFixed(2) }}×</output></span
      >
      <input
        type="range"
        min="0.25"
        max="4"
        step="0.05"
        :value="modelValue.highlightStart"
        @input="updateValue('highlightStart', $event)"
      />
    </label>

    <label>
      <span
        >Highlight Gain <output>{{ modelValue.highlightGain.toFixed(2) }}</output></span
      >
      <input
        type="range"
        min="0.5"
        max="2.2"
        step="0.05"
        :value="modelValue.highlightGain"
        @input="updateValue('highlightGain', $event)"
      />
    </label>

    <label>
      <span
        >HDR Position <output>{{ formatBias(modelValue.highlightBias) }}</output></span
      >
      <input
        type="range"
        min="-0.75"
        max="0.75"
        step="0.05"
        :value="modelValue.highlightBias"
        @input="updateValue('highlightBias', $event)"
      />
    </label>
  </aside>
</template>

<style scoped>
.hdr-tone-panel {
  position: absolute;
  z-index: 50;
  top: 1rem;
  right: 1rem;
  width: min(20rem, calc(100% - 2rem));
  padding: 0.9rem;
  border: 1px solid rgb(255 255 255 / 14%);
  border-radius: 0.8rem;
  color: rgb(255 255 255 / 86%);
  background: rgb(5 5 5 / 74%);
  box-shadow: 0 1rem 3rem rgb(0 0 0 / 32%);
  backdrop-filter: blur(18px);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  pointer-events: auto;
}

.hdr-tone-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 0.6rem;
}

.hdr-tone-header strong,
.hdr-tone-header small {
  display: block;
}

.hdr-tone-header strong {
  font-size: 0.78rem;
  font-weight: 600;
}

.hdr-tone-header small {
  margin-top: 0.12rem;
  color: rgb(255 255 255 / 44%);
  font-size: 0.66rem;
}

.hdr-tone-header button {
  border: 0;
  padding: 0;
  color: rgb(255 255 255 / 50%);
  background: transparent;
  font: inherit;
  font-size: 0.7rem;
  cursor: pointer;
}

.hdr-tone-header button:hover {
  color: rgb(255 255 255 / 86%);
}

.hdr-tone-chart {
  display: block;
  width: 100%;
  height: 7.6rem;
  margin-bottom: 0.65rem;
  border-radius: 0.45rem;
  background: rgb(255 255 255 / 4%);
}

.hdr-tone-sdr-line {
  stroke: rgb(255 255 255 / 18%);
  stroke-width: 1;
  stroke-dasharray: 4 4;
}

.hdr-tone-curve {
  fill: none;
  stroke: #ffd700;
  stroke-width: 2;
}

.hdr-tone-panel label {
  display: block;
  margin-top: 0.55rem;
}

.hdr-tone-panel label span {
  display: flex;
  justify-content: space-between;
  color: rgb(255 255 255 / 62%);
  font-size: 0.68rem;
}

.hdr-tone-panel output {
  color: rgb(255 255 255 / 90%);
  font-variant-numeric: tabular-nums;
}

.hdr-tone-panel input {
  width: 100%;
  margin: 0.22rem 0 0;
  accent-color: #ffd700;
}

@media (max-width: 640px) {
  .hdr-tone-panel {
    top: 0.65rem;
    right: 0.65rem;
    width: min(18rem, calc(100% - 1.3rem));
  }
}
</style>
