<script setup lang="ts">
import * as THREE from 'three'
import {
  computed,
  onBeforeUnmount,
  onMounted,
  ref,
  shallowRef,
  useTemplateRef,
  watch,
  type CSSProperties,
} from 'vue'

interface WebGL1TimerQueryExtension {
  TIME_ELAPSED_EXT: number
  GPU_DISJOINT_EXT: number
  QUERY_RESULT_EXT: number
  QUERY_RESULT_AVAILABLE_EXT: number
  createQueryEXT: () => object | null
  deleteQueryEXT: (query: object) => void
  beginQueryEXT: (target: number, query: object) => void
  endQueryEXT: (target: number) => void
  getQueryObjectEXT: (query: object, parameter: number) => unknown
}

interface WebGL2TimerQueryExtension {
  TIME_ELAPSED_EXT: number
  GPU_DISJOINT_EXT: number
}

interface WebGLDebugRendererInfoExtension {
  UNMASKED_RENDERER_WEBGL: number
  UNMASKED_VENDOR_WEBGL: number
}

interface GpuTimerContext {
  readonly isSupported: boolean
  begin: (frameIndex: number) => boolean
  end: () => void
  collectReadyResults: () => Array<{ frameIndex: number; gpuMs: number }>
  dispose: () => void
}

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
  profiling?: boolean
  pixelRatioOverride?: number
  maxPixelRatio?: number
  renderScaleX?: number
  renderScaleY?: number
  maxRaySteps?: number
  maxDepth?: number
  rayStepScale?: number
  waveOctaves?: number
  fixedTime?: number
}

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
  profiling: false,
  pixelRatioOverride: undefined,
  maxPixelRatio: 1,
  renderScaleX: 0.58,
  renderScaleY: 0.6,
  maxRaySteps: 100,
  maxDepth: 50,
  rayStepScale: 1,
  waveOctaves: 4,
  fixedTime: undefined,
})

const containerRef = useTemplateRef('containerRef')
const rafRef = ref<number | null>(null)
const rendererRef = shallowRef<THREE.WebGLRenderer | null>(null)
const materialRef = shallowRef<THREE.ShaderMaterial | null>(null)
const sceneRef = shallowRef<THREE.Scene | null>(null)
const cameraRef = shallowRef<THREE.OrthographicCamera | null>(null)
const geometryRef = shallowRef<THREE.BufferGeometry | null>(null)
const mouseRef = ref<THREE.Vector2>(new THREE.Vector2(0, 0))
const timeRef = ref<number>(0)
const webGLSupported = ref<boolean>(true)
const webGLVersionRef = ref<'webgl1' | 'webgl2' | 'unsupported'>('unsupported')
const rendererNameRef = ref<string | null>(null)
const vendorNameRef = ref<string | null>(null)
const lightRenderSizeRef = ref<{ width: number; height: number }>({ width: 0, height: 0 })
const frameSamplesRef = ref<LightPillarFrameSample[]>([])
const frameSampleByIndexRef = ref<Map<number, LightPillarFrameSample>>(new Map())
const longTaskSamplesRef = ref<LightPillarLongTaskSample[]>([])
const profilerSummaryRef = ref<LightPillarProfilingSummary | null>(null)
const gpuTimerSupportedRef = ref<boolean>(false)
const totalDroppedFramesRef = ref<number>(0)
const profilingStartedAtRef = ref<number | null>(null)
const profilingEnabled = computed(() => props.profiling && webGLSupported.value)

const LONG_TASK_THRESHOLD_MS = 50
const MAX_FRAME_SAMPLES = 900
const MAX_LONG_TASK_SAMPLES = 200
const PROFILING_TARGET_FRAME_RATE = 120
const PROFILING_TARGET_FRAME_TIME_MS = 1000 / PROFILING_TARGET_FRAME_RATE
const PROFILING_SUMMARY_INTERVAL_MS = 250
const ORIGINAL_TIME_STEP_PER_MS = 0.016 / (1000 / 60)
const MAX_RAY_STEPS = 100

let profilerLongTaskObserver: PerformanceObserver | null = null

function clampSamples<T>(samples: T[], max: number) {
  if (samples.length > max) {
    samples.splice(0, samples.length - max)
  }
}

function average(values: number[]): number {
  if (values.length === 0) return 0
  return values.reduce((sum, value) => sum + value, 0) / values.length
}

function percentile(values: number[], ratio: number): number {
  if (values.length === 0) return 0
  const sorted = [...values].sort((left, right) => left - right)
  const index = Math.min(sorted.length - 1, Math.floor((sorted.length - 1) * ratio))
  return sorted[index] ?? 0
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), max)
}

function resetProfilerState() {
  frameSamplesRef.value = []
  frameSampleByIndexRef.value.clear()
  longTaskSamplesRef.value = []
  totalDroppedFramesRef.value = 0
  profilingStartedAtRef.value = profilingEnabled.value ? performance.now() : null
  profilerSummaryRef.value = null
}

function updateProfilerSummary(metrics: {
  pixelRatio: number
  canvasWidth: number
  canvasHeight: number
  renderWidth: number
  renderHeight: number
  gpuTimerSupported: boolean
}) {
  if (!profilingEnabled.value) {
    profilerSummaryRef.value = null
    return
  }

  const samples = frameSamplesRef.value
  const startedAt = profilingStartedAtRef.value
  if (samples.length === 0 || startedAt === null) {
    profilerSummaryRef.value = null
    return
  }

  const lastTimestamp = samples.at(-1)?.ts ?? startedAt
  const firstTimestamp = samples[0]?.ts ?? startedAt
  const sampleDurationMs = Math.max(lastTimestamp - firstTimestamp, 0)
  const frameIntervals = samples.map((sample) => sample.frameIntervalMs)
  const callbackDurations = samples.map((sample) => sample.callbackMs)
  const renderCpuDurations = samples.map((sample) => sample.renderCpuMs)
  const gpuDurations = samples
    .map((sample) => sample.gpuMs)
    .filter((value): value is number => value !== null)
  const avgGpuMs = gpuDurations.length > 0 ? average(gpuDurations) : null
  const actualFps = sampleDurationMs > 0 ? ((samples.length - 1) * 1000) / sampleDurationMs : 0
  const maxLongTaskMs = Math.max(0, ...longTaskSamplesRef.value.map((sample) => sample.durationMs))

  profilerSummaryRef.value = {
    sampleDurationMs,
    frameCount: samples.length,
    fps: actualFps,
    avgFrameIntervalMs: average(frameIntervals),
    p95FrameIntervalMs: percentile(frameIntervals, 0.95),
    avgCallbackMs: average(callbackDurations),
    p95CallbackMs: percentile(callbackDurations, 0.95),
    avgRenderCpuMs: average(renderCpuDurations),
    p95RenderCpuMs: percentile(renderCpuDurations, 0.95),
    avgGpuMs,
    p95GpuMs: gpuDurations.length > 0 ? percentile(gpuDurations, 0.95) : null,
    gpuSampleCount: gpuDurations.length,
    gpuWorkMsPerSecond: avgGpuMs === null ? null : avgGpuMs * actualFps,
    gpuFrameBudgetRatio: avgGpuMs === null ? null : avgGpuMs / PROFILING_TARGET_FRAME_TIME_MS,
    droppedFrames: totalDroppedFramesRef.value,
    droppedFrameRatio:
      samples.length > 0 ? totalDroppedFramesRef.value / Math.max(samples.length, 1) : 0,
    targetFrameRate: PROFILING_TARGET_FRAME_RATE,
    targetFrameTimeMs: PROFILING_TARGET_FRAME_TIME_MS,
    longTaskCount: longTaskSamplesRef.value.length,
    maxLongTaskMs,
    pixelRatio: metrics.pixelRatio,
    canvasWidth: metrics.canvasWidth,
    canvasHeight: metrics.canvasHeight,
    renderWidth: metrics.renderWidth,
    renderHeight: metrics.renderHeight,
    gpuTimerSupported: metrics.gpuTimerSupported,
    webglVersion: webGLVersionRef.value,
    rendererName: rendererNameRef.value,
    vendorName: vendorNameRef.value,
  }
}

function refreshProfilerSummary() {
  if (!profilingEnabled.value || !rendererRef.value) return

  updateProfilerSummary({
    pixelRatio: rendererRef.value.getPixelRatio(),
    canvasWidth: rendererRef.value.domElement.width,
    canvasHeight: rendererRef.value.domElement.height,
    renderWidth: lightRenderSizeRef.value.width,
    renderHeight: lightRenderSizeRef.value.height,
    gpuTimerSupported: gpuTimerSupportedRef.value,
  })
}

function installProfilerHandle() {
  window.unirhyLightPillarProfiler = {
    get enabled() {
      return profilingEnabled.value
    },
    get summary() {
      refreshProfilerSummary()
      return profilerSummaryRef.value
    },
    snapshot() {
      refreshProfilerSummary()
      return {
        summary: profilerSummaryRef.value,
        frameSamples: [...frameSamplesRef.value],
        longTasks: [...longTaskSamplesRef.value],
      }
    },
    reset() {
      resetProfilerState()
    },
    download() {
      const payload = JSON.stringify(
        {
          capturedAt: new Date().toISOString(),
          snapshot: this.snapshot(),
        },
        null,
        2,
      )
      const blob = new Blob([payload], { type: 'application/json' })
      const href = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = href
      link.download = `light-pillar-profile-${Date.now()}.json`
      link.click()
      URL.revokeObjectURL(href)
    },
  }
}

function pushFrameSample(sample: LightPillarFrameSample) {
  frameSamplesRef.value.push(sample)
  frameSampleByIndexRef.value.set(sample.frameIndex, sample)

  while (frameSamplesRef.value.length > MAX_FRAME_SAMPLES) {
    const removed = frameSamplesRef.value.shift()
    if (removed) {
      frameSampleByIndexRef.value.delete(removed.frameIndex)
    }
  }
}

function startLongTaskObserver() {
  if (!profilingEnabled.value || profilerLongTaskObserver) return
  if (typeof PerformanceObserver === 'undefined') return
  const supportedEntryTypes = PerformanceObserver.supportedEntryTypes ?? []
  if (!supportedEntryTypes.includes('longtask')) return

  profilerLongTaskObserver = new PerformanceObserver((list) => {
    for (const entry of list.getEntries()) {
      longTaskSamplesRef.value.push({
        ts: entry.startTime,
        durationMs: entry.duration,
      })
    }
    clampSamples(longTaskSamplesRef.value, MAX_LONG_TASK_SAMPLES)
  })
  profilerLongTaskObserver.observe({ entryTypes: ['longtask'] })
}

function stopLongTaskObserver() {
  profilerLongTaskObserver?.disconnect()
  profilerLongTaskObserver = null
}

function detectWebGL() {
  const canvas = document.createElement('canvas')
  const webgl2Context = canvas.getContext('webgl2')
  const gl = webgl2Context || canvas.getContext('webgl') || canvas.getContext('experimental-webgl')

  if (gl) {
    webGLSupported.value = true
    webGLVersionRef.value = webgl2Context ? 'webgl2' : 'webgl1'
  } else {
    webGLSupported.value = false
    webGLVersionRef.value = 'unsupported'
    console.warn('WebGL is not supported in this browser')
  }

  canvas.remove()
}

function createGpuTimerContext(
  gl: WebGLRenderingContext | WebGL2RenderingContext,
): GpuTimerContext | null {
  if ('createQuery' in gl) {
    const gl2 = gl as WebGL2RenderingContext
    const extension = gl2.getExtension(
      'EXT_disjoint_timer_query_webgl2',
    ) as WebGL2TimerQueryExtension | null
    if (!extension) return null

    const pendingQueries: Array<{ frameIndex: number; query: WebGLQuery }> = []
    return {
      isSupported: true,
      begin(frameIndex) {
        const query = gl2.createQuery()
        if (!query) return false
        gl2.beginQuery(extension.TIME_ELAPSED_EXT, query)
        pendingQueries.push({ frameIndex, query })
        return true
      },
      end() {
        gl2.endQuery(extension.TIME_ELAPSED_EXT)
      },
      collectReadyResults() {
        const results: Array<{ frameIndex: number; gpuMs: number }> = []
        for (let index = pendingQueries.length - 1; index >= 0; index -= 1) {
          const pending = pendingQueries[index]
          if (!pending) continue
          const available = gl2.getQueryParameter(
            pending.query,
            gl2.QUERY_RESULT_AVAILABLE,
          ) as boolean
          const disjoint = gl2.getParameter(extension.GPU_DISJOINT_EXT) as boolean
          if (!available || disjoint) continue
          const elapsedNanoseconds = gl2.getQueryParameter(
            pending.query,
            gl2.QUERY_RESULT,
          ) as number
          results.push({
            frameIndex: pending.frameIndex,
            gpuMs: elapsedNanoseconds / 1_000_000,
          })
          gl2.deleteQuery(pending.query)
          pendingQueries.splice(index, 1)
        }
        return results
      },
      dispose() {
        for (const pending of pendingQueries) {
          gl2.deleteQuery(pending.query)
        }
        pendingQueries.length = 0
      },
    }
  }

  const extension = gl.getExtension('EXT_disjoint_timer_query') as WebGL1TimerQueryExtension | null
  if (!extension) return null

  const pendingQueries: Array<{ frameIndex: number; query: object }> = []
  return {
    isSupported: true,
    begin(frameIndex) {
      const query = extension.createQueryEXT()
      if (!query) return false
      extension.beginQueryEXT(extension.TIME_ELAPSED_EXT, query)
      pendingQueries.push({ frameIndex, query })
      return true
    },
    end() {
      extension.endQueryEXT(extension.TIME_ELAPSED_EXT)
    },
    collectReadyResults() {
      const results: Array<{ frameIndex: number; gpuMs: number }> = []
      for (let index = pendingQueries.length - 1; index >= 0; index -= 1) {
        const pending = pendingQueries[index]
        if (!pending) continue
        const available = extension.getQueryObjectEXT(
          pending.query,
          extension.QUERY_RESULT_AVAILABLE_EXT,
        ) as boolean
        const disjoint = gl.getParameter(extension.GPU_DISJOINT_EXT) as boolean
        if (!available || disjoint) continue
        const elapsedNanoseconds = extension.getQueryObjectEXT(
          pending.query,
          extension.QUERY_RESULT_EXT,
        ) as number
        results.push({
          frameIndex: pending.frameIndex,
          gpuMs: elapsedNanoseconds / 1_000_000,
        })
        extension.deleteQueryEXT(pending.query)
        pendingQueries.splice(index, 1)
      }
      return results
    },
    dispose() {
      for (const pending of pendingQueries) {
        extension.deleteQueryEXT(pending.query)
      }
      pendingQueries.length = 0
    },
  }
}

let cleanup: (() => void) | null = null
const setup = () => {
  if (!containerRef.value || !webGLSupported.value) return

  const container = containerRef.value
  const width = container.clientWidth
  const height = container.clientHeight
  const renderScaleX = clamp(props.renderScaleX, 0.1, 1)
  const renderScaleY = clamp(props.renderScaleY, 0.1, 1)
  const renderWidth = Math.max(1, Math.round(width * renderScaleX))
  const renderHeight = Math.max(1, Math.round(height * renderScaleY))
  lightRenderSizeRef.value = { width: renderWidth, height: renderHeight }
  const rayStepCount = Math.round(clamp(props.maxRaySteps, 1, MAX_RAY_STEPS))
  const maxDepth = clamp(props.maxDepth, 1, 100)
  const rayStepScale = clamp(props.rayStepScale, 0.1, 4)
  const intensity = props.intensity
  const glowScale = (props.glowAmount * 3) / props.pillarWidth
  const colorWeightEarlyBreak = 3.2 / glowScale
  const pillarWidth = props.pillarWidth
  const pillarHeight = props.pillarHeight
  const noiseAmount = props.noiseIntensity / 15
  const pillarRotation = (props.pillarRotation * Math.PI) / 180
  const normalizedPillarRotation = ((props.pillarRotation % 360) + 360) % 360
  const waveOctaves = Math.round(clamp(props.waveOctaves, 1, 4))

  const scene = new THREE.Scene()
  sceneRef.value = scene
  const camera = new THREE.OrthographicCamera(-1, 1, 1, -1, 0, 1)
  cameraRef.value = camera

  let renderer: THREE.WebGLRenderer
  try {
    renderer = new THREE.WebGLRenderer({
      antialias: false,
      alpha: false,
      powerPreference: 'high-performance',
      precision: 'lowp',
      stencil: false,
      depth: false,
    })
  } catch (error) {
    console.error('Failed to create WebGL renderer:', error)
    webGLSupported.value = false
    webGLVersionRef.value = 'unsupported'
    return
  }

  const maxPixelRatio = clamp(props.maxPixelRatio, 0.1, 2)
  const pixelRatio = props.pixelRatioOverride
    ? clamp(props.pixelRatioOverride, 0.1, 2)
    : Math.min(window.devicePixelRatio, maxPixelRatio)
  renderer.autoClear = false
  renderer.setClearColor(0x000000, 1)
  renderer.setSize(renderWidth, renderHeight, false)
  renderer.domElement.style.width = `${width}px`
  renderer.domElement.style.height = `${height}px`
  renderer.setPixelRatio(pixelRatio)
  container.appendChild(renderer.domElement)
  rendererRef.value = renderer

  const gl = renderer.getContext() as WebGLRenderingContext | WebGL2RenderingContext
  const debugInfo = gl.getExtension(
    'WEBGL_debug_renderer_info',
  ) as WebGLDebugRendererInfoExtension | null
  rendererNameRef.value = debugInfo
    ? (gl.getParameter(debugInfo.UNMASKED_RENDERER_WEBGL) as string | null)
    : null
  vendorNameRef.value = debugInfo
    ? (gl.getParameter(debugInfo.UNMASKED_VENDOR_WEBGL) as string | null)
    : null

  if (profilingEnabled.value) {
    installProfilerHandle()
    resetProfilerState()
  }
  const gpuTimerContext = profilingEnabled.value ? createGpuTimerContext(gl) : null
  gpuTimerSupportedRef.value = gpuTimerContext?.isSupported ?? false
  if (profilingEnabled.value) {
    profilingStartedAtRef.value = performance.now()
    startLongTaskObserver()
    updateProfilerSummary({
      pixelRatio: renderer.getPixelRatio(),
      canvasWidth: renderer.domElement.width,
      canvasHeight: renderer.domElement.height,
      renderWidth: lightRenderSizeRef.value.width,
      renderHeight: lightRenderSizeRef.value.height,
      gpuTimerSupported: gpuTimerSupportedRef.value,
    })
  }

  const Color = THREE.Color
  const Vector3 = THREE.Vector3
  const parseColor = (hex: string): THREE.Vector3 => {
    const color = new Color(hex)
    return new Vector3(color.r, color.g, color.b)
  }
  const topColor = parseColor(props.topColor)
  const bottomColor = parseColor(props.bottomColor)

  const vertexShader = `
      varying vec2 vUv;
      void main() {
        vUv = uv;
        gl_Position = vec4(position, 1.0);
      }
    `
  const waveOctave2 = `
        if (depth < 24.0) {
          deformed.xz = rotateVec(deformed.xz, ROT_04);
          deformed += cos(deformed.zxy * 2.0 - uTime * 2.0) * 0.5;
        }
  `
  const waveOctave3 = `
        if (depth < 16.0) {
          deformed.xz = rotateVec(deformed.xz, ROT_04);
          deformed += cos(deformed.zxy * 4.0 - uTime * 4.0) * 0.25;
        }
  `
  const waveOctave4 = `
        if (depth < 10.0) {
          deformed.xz = rotateVec(deformed.xz, ROT_04);
          deformed += cos(deformed.zxy * 8.0 - uTime * 6.0) * 0.125;
        }
  `
  const isNearRotation = (value: number) => Math.abs(normalizedPillarRotation - value) < 0.0001
  function getPillarRotationConstant(): string {
    if (isNearRotation(0) || isNearRotation(90) || isNearRotation(180) || isNearRotation(270)) {
      return ''
    }

    return `
      const vec2 PILLAR_ROTATION = vec2(
        ${Math.cos(pillarRotation).toPrecision(9)},
        ${Math.sin(pillarRotation).toPrecision(9)}
      );
  `
  }

  function getUvRotation(): string {
    if (isNearRotation(0)) return ''
    if (isNearRotation(90)) return 'uv = vec2(-uv.y, uv.x);'
    if (isNearRotation(180)) return 'uv = -uv;'
    if (isNearRotation(270)) return 'uv = vec2(uv.y, -uv.x);'
    return 'uv = rotateVec(uv, PILLAR_ROTATION);'
  }

  const pillarRotationConstant = getPillarRotationConstant()
  const uvRotation = getUvRotation()

  const fragmentShader = `
      uniform float uTime;
      uniform float uAspect;
      uniform vec2 uViewRotation;
      varying vec2 vUv;

      const vec3 TOP_COLOR = vec3(
        ${topColor.x.toPrecision(9)},
        ${topColor.y.toPrecision(9)},
        ${topColor.z.toPrecision(9)}
      );
      const vec3 BOTTOM_COLOR = vec3(
        ${bottomColor.x.toPrecision(9)},
        ${bottomColor.y.toPrecision(9)},
        ${bottomColor.z.toPrecision(9)}
      );
      ${pillarRotationConstant}
      const vec2 ROT_04 = vec2(0.9210609940, 0.3894183423);

      vec2 rotateVec(vec2 value, vec2 rotation) {
        return vec2(
          value.x * rotation.x - value.y * rotation.y,
          value.x * rotation.y + value.y * rotation.x
        );
      }

      float noise(vec2 coord) {
        vec3 p3 = fract(vec3(coord.xyx) * 0.1031);
        p3 += dot(p3, p3.yzx + 33.33);
        return fract((p3.x + p3.y) * p3.z);
      }

      vec3 applyWaveDeformation(vec3 pos, float depth) {
        vec3 deformed = pos;

        deformed.xz = rotateVec(deformed.xz, ROT_04);
        deformed += cos(deformed.zxy);
        ${waveOctaves >= 2 ? waveOctave2 : ''}
        ${waveOctaves >= 3 ? waveOctave3 : ''}
        ${waveOctaves >= 4 ? waveOctave4 : ''}

        return deformed;
      }

      void main() {
        vec2 uv = (vUv * 2.0 - 1.0) * vec2(uAspect, 1.0);
        ${uvRotation}

        vec3 direction = normalize(vec3(uv, 1.0));

        float depth = 0.1;
        vec2 rotatedOriginXz = vec2(uViewRotation.y * 10.0, -uViewRotation.x * 10.0);
        vec2 rotatedDirectionXz = rotateVec(direction.xz, uViewRotation);

        float colorWeight = 0.0;
        float topColorWeight = 0.0;
        const float glowScale = ${glowScale.toPrecision(9)};

        for(float i = 0.0; i < ${rayStepCount}.0; i++) {
          float posY = direction.y * depth;
          vec2 posXz = rotatedOriginXz + rotatedDirectionXz * depth;
          vec3 pos = vec3(posXz.x, posY, posXz.y);

          vec3 deformed = pos;
          deformed.y *= ${pillarHeight.toPrecision(9)};
          deformed = applyWaveDeformation(deformed + vec3(0.0, uTime, 0.0), depth);

          vec2 cosinePair = cos(deformed.xz);
          float fieldDistance = length(cosinePair) - 0.2;

          float radialBound = length(posXz) - ${pillarWidth.toPrecision(9)};
          float blendHeight = max(4.0 - abs(radialBound - fieldDistance), 0.0);
          fieldDistance = max(radialBound, fieldDistance) + blendHeight * blendHeight * 0.0625;
          fieldDistance = abs(fieldDistance) * 0.15 + 0.01;

          float distanceWeight = 1.0 / fieldDistance;
          colorWeight += distanceWeight;
          float verticalMix = clamp((15.0 - posY) * 0.0333333333, 0.0, 1.0);
          topColorWeight += verticalMix * verticalMix * (3.0 - 2.0 * verticalMix) * distanceWeight;

          if(depth > ${maxDepth.toPrecision(9)}) break;
          if(colorWeight > ${colorWeightEarlyBreak.toPrecision(9)} && depth > 12.0) break;
          depth += fieldDistance * ${rayStepScale.toPrecision(9)};
        }

        vec3 color = BOTTOM_COLOR * colorWeight + (TOP_COLOR - BOTTOM_COLOR) * topColorWeight;
        color = tanh(color * glowScale);

        float rnd = noise(gl_FragCoord.xy);
        color -= rnd * ${noiseAmount.toPrecision(9)};

        gl_FragColor = vec4(color * ${intensity.toPrecision(9)}, 1.0);
      }
    `

  const material = new THREE.ShaderMaterial({
    vertexShader,
    fragmentShader,
    uniforms: {
      uTime: { value: 0 },
      uAspect: { value: width / height },
      uViewRotation: { value: new THREE.Vector2(1, 0) },
    },
    transparent: false,
    blending: THREE.NoBlending,
    depthWrite: false,
    depthTest: false,
  })
  materialRef.value = material

  const geometry = new THREE.BufferGeometry()
  geometry.setAttribute(
    'position',
    new THREE.Float32BufferAttribute([-1, -1, 0, 3, -1, 0, -1, 3, 0], 3),
  )
  geometry.setAttribute('uv', new THREE.Float32BufferAttribute([0, 0, 2, 0, 0, 2], 2))
  geometryRef.value = geometry
  const mesh = new THREE.Mesh(geometry, material)
  scene.add(mesh)

  let mouseMoveTimeout: number | null = null
  const handleMouseMove = (event: MouseEvent) => {
    if (!props.interactive) return
    if (mouseMoveTimeout) return

    mouseMoveTimeout = window.setTimeout(() => {
      mouseMoveTimeout = null
    }, 16)

    const rect = container.getBoundingClientRect()
    const x = ((event.clientX - rect.left) / rect.width) * 2 - 1
    const y = -((event.clientY - rect.top) / rect.height) * 2 + 1
    mouseRef.value.set(x, y)
  }

  if (props.interactive) {
    container.addEventListener('mousemove', handleMouseMove, { passive: true })
  }

  let lastTime = performance.now()
  let lastSampleTimestamp: number | null = null
  let lastProfilerSummaryTimestamp = 0
  let frameIndex = 0

  const animate = (currentTime: number) => {
    if (!materialRef.value || !rendererRef.value || !sceneRef.value || !cameraRef.value) return

    if (document.hidden) {
      lastTime = currentTime
      lastSampleTimestamp = profilingEnabled.value ? currentTime : lastSampleTimestamp
      rafRef.value = requestAnimationFrame(animate)
      return
    }

    const deltaTime = currentTime - lastTime
    const callbackStart = performance.now()

    let queryStarted = false
    if (gpuTimerContext) {
      queryStarted = gpuTimerContext.begin(frameIndex)
    }

    const renderStart = performance.now()
    if (props.fixedTime === undefined) {
      timeRef.value += Math.min(deltaTime, 50) * ORIGINAL_TIME_STEP_PER_MS * props.rotationSpeed
    } else {
      timeRef.value = props.fixedTime
    }
    materialRef.value.uniforms.uTime!.value = timeRef.value
    const viewRotationAngle =
      props.interactive && mouseRef.value.length() > 0
        ? mouseRef.value.x * Math.PI * 2
        : timeRef.value * 0.3
    const viewRotation = materialRef.value.uniforms.uViewRotation!.value as THREE.Vector2
    viewRotation.set(Math.cos(viewRotationAngle), Math.sin(viewRotationAngle))
    rendererRef.value.render(sceneRef.value, cameraRef.value)
    const renderCpuMs = performance.now() - renderStart

    if (queryStarted) {
      gpuTimerContext?.end()
    }

    const callbackMs = performance.now() - callbackStart
    lastTime = currentTime

    if (profilingEnabled.value) {
      const frameIntervalMs =
        lastSampleTimestamp === null ? 0 : Math.max(currentTime - lastSampleTimestamp, 0)
      const droppedFrames =
        lastSampleTimestamp === null
          ? 0
          : Math.max(Math.round(frameIntervalMs / PROFILING_TARGET_FRAME_TIME_MS) - 1, 0)
      totalDroppedFramesRef.value += droppedFrames
      lastSampleTimestamp = currentTime

      pushFrameSample({
        frameIndex,
        ts: currentTime,
        frameIntervalMs,
        callbackMs,
        renderCpuMs,
        gpuMs: null,
        droppedFrames,
      })

      for (const result of gpuTimerContext?.collectReadyResults() ?? []) {
        const sample = frameSampleByIndexRef.value.get(result.frameIndex)
        if (sample) {
          sample.gpuMs = result.gpuMs
        }
      }

      if (callbackMs >= LONG_TASK_THRESHOLD_MS) {
        longTaskSamplesRef.value.push({
          ts: currentTime,
          durationMs: callbackMs,
        })
        clampSamples(longTaskSamplesRef.value, MAX_LONG_TASK_SAMPLES)
      }

      if (currentTime - lastProfilerSummaryTimestamp >= PROFILING_SUMMARY_INTERVAL_MS) {
        lastProfilerSummaryTimestamp = currentTime
        updateProfilerSummary({
          pixelRatio: renderer.getPixelRatio(),
          canvasWidth: renderer.domElement.width,
          canvasHeight: renderer.domElement.height,
          renderWidth: lightRenderSizeRef.value.width,
          renderHeight: lightRenderSizeRef.value.height,
          gpuTimerSupported: gpuTimerSupportedRef.value,
        })
      }
    }

    frameIndex += 1
    rafRef.value = requestAnimationFrame(animate)
  }
  rafRef.value = requestAnimationFrame(animate)

  const handleVisibilityChange = () => {
    const now = performance.now()
    lastTime = now
    if (profilingEnabled.value) {
      lastSampleTimestamp = now
    }
  }

  let resizeTimeout: number | null = null
  const handleResize = () => {
    if (resizeTimeout) {
      clearTimeout(resizeTimeout)
    }

    resizeTimeout = window.setTimeout(() => {
      if (!rendererRef.value || !materialRef.value || !containerRef.value) return
      const newWidth = containerRef.value.clientWidth
      const newHeight = containerRef.value.clientHeight
      const newRenderWidth = Math.max(1, Math.round(newWidth * renderScaleX))
      const newRenderHeight = Math.max(1, Math.round(newHeight * renderScaleY))
      lightRenderSizeRef.value = { width: newRenderWidth, height: newRenderHeight }
      rendererRef.value.setSize(newRenderWidth, newRenderHeight, false)
      rendererRef.value.domElement.style.width = `${newWidth}px`
      rendererRef.value.domElement.style.height = `${newHeight}px`
      materialRef.value.uniforms.uAspect!.value = newWidth / newHeight
      if (profilingEnabled.value) {
        updateProfilerSummary({
          pixelRatio: renderer.getPixelRatio(),
          canvasWidth: renderer.domElement.width,
          canvasHeight: renderer.domElement.height,
          renderWidth: lightRenderSizeRef.value.width,
          renderHeight: lightRenderSizeRef.value.height,
          gpuTimerSupported: gpuTimerSupportedRef.value,
        })
      }
    }, 150)
  }

  window.addEventListener('resize', handleResize, { passive: true })
  document.addEventListener('visibilitychange', handleVisibilityChange)

  cleanup = () => {
    window.removeEventListener('resize', handleResize)
    document.removeEventListener('visibilitychange', handleVisibilityChange)
    if (props.interactive) {
      container.removeEventListener('mousemove', handleMouseMove)
    }
    if (rafRef.value) {
      cancelAnimationFrame(rafRef.value)
    }
    gpuTimerContext?.dispose()
    stopLongTaskObserver()
    if (rendererRef.value) {
      rendererRef.value.dispose()
      rendererRef.value.forceContextLoss()
      if (container.contains(rendererRef.value.domElement)) {
        container.removeChild(rendererRef.value.domElement)
      }
    }
    if (materialRef.value) {
      materialRef.value.dispose()
    }
    if (geometryRef.value) {
      geometryRef.value.dispose()
    }

    rendererRef.value = null
    materialRef.value = null
    sceneRef.value = null
    cameraRef.value = null
    geometryRef.value = null
    rafRef.value = null
  }
}

onMounted(() => {
  detectWebGL()
  setup()
})

onBeforeUnmount(() => {
  cleanup?.()
  if (window.unirhyLightPillarProfiler) {
    delete window.unirhyLightPillarProfiler
  }
})

watch(
  () => [
    props.topColor,
    props.bottomColor,
    props.intensity,
    props.rotationSpeed,
    props.interactive,
    props.glowAmount,
    props.pillarWidth,
    props.pillarHeight,
    props.noiseIntensity,
    props.pillarRotation,
    props.profiling,
    props.pixelRatioOverride,
    props.maxPixelRatio,
    props.renderScaleX,
    props.renderScaleY,
    props.maxRaySteps,
    props.maxDepth,
    props.rayStepScale,
    props.waveOctaves,
    props.fixedTime,
    webGLSupported.value,
  ],
  () => {
    cleanup?.()
    setup()
  },
  {
    deep: true,
  },
)
</script>

<template>
  <div
    v-if="!webGLSupported"
    :class="`w-full h-full absolute top-0 left-0 flex items-center justify-center bg-black/10 text-gray-500 text-sm ${className}`"
    :style="{ mixBlendMode }"
  >
    WebGL not supported
  </div>
  <div
    v-else
    ref="containerRef"
    :class="`w-full h-full absolute top-0 left-0 ${className}`"
    :style="{ mixBlendMode }"
  />
</template>
