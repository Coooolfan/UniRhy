/// <reference types="vite/client" />

interface LightPillarFrameSample {
  frameIndex: number
  ts: number
  frameIntervalMs: number
  callbackMs: number
  renderCpuMs: number
  gpuMs: number | null
  droppedFrames: number
}

interface LightPillarLongTaskSample {
  ts: number
  durationMs: number
}

interface LightPillarProfilingSummary {
  sampleDurationMs: number
  frameCount: number
  fps: number
  avgFrameIntervalMs: number
  p95FrameIntervalMs: number
  avgCallbackMs: number
  p95CallbackMs: number
  avgRenderCpuMs: number
  p95RenderCpuMs: number
  avgGpuMs: number | null
  p95GpuMs: number | null
  gpuSampleCount: number
  gpuWorkMsPerSecond: number | null
  gpuFrameBudgetRatio: number | null
  droppedFrames: number
  droppedFrameRatio: number
  targetFrameRate: number
  targetFrameTimeMs: number
  longTaskCount: number
  maxLongTaskMs: number
  pixelRatio: number
  canvasWidth: number
  canvasHeight: number
  renderWidth: number
  renderHeight: number
  gpuTimerSupported: boolean
  webglVersion: 'webgl1' | 'webgl2' | 'unsupported'
  rendererName: string | null
  vendorName: string | null
}

interface LightPillarProfilingSnapshot {
  summary: LightPillarProfilingSummary | null
  frameSamples: LightPillarFrameSample[]
  longTasks: LightPillarLongTaskSample[]
}

interface LightPillarProfilerHandle {
  readonly enabled: boolean
  readonly summary: LightPillarProfilingSummary | null
  snapshot: () => LightPillarProfilingSnapshot
  reset: () => void
  download: () => void
}

interface Window {
  unirhyLightPillarProfiler?: LightPillarProfilerHandle
}

declare module '*.vue' {
  import type { DefineComponent } from 'vue'

  const component: DefineComponent<Record<string, never>, Record<string, never>, unknown>
  export default component
}

declare module '*.md' {
  import type { BlogFrontmatter } from '@/types/blog'

  export const frontmatter: BlogFrontmatter
  export const html: string
}
