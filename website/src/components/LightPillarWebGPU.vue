<script setup lang="ts">
import {
  onBeforeUnmount,
  onMounted,
  shallowRef,
  useTemplateRef,
  watch,
  type CSSProperties,
} from 'vue'
import * as THREE from 'three/webgpu'
import { positionGeometry, screenSize, screenUV, uniform, vec4, wgslFn } from 'three/tsl'
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
  pillarRotation?: number
  mixBlendMode?: CSSProperties['mixBlendMode']
  toneCurve?: HdrToneCurve
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
  pillarRotation: 0,
  mixBlendMode: 'normal',
  toneCurve: () => ({ ...DEFAULT_HDR_TONE_CURVE }),
})

const emit = defineEmits<{
  ready: []
  unavailable: []
}>()

const MACBOOK_PRO_XDR_HEADROOM = 3.2
const MACBOOK_PRO_XDR_CORE_THRESHOLD = 3.1

const containerRef = useTemplateRef('containerRef')
const rendererRef = shallowRef<THREE.WebGPURenderer | null>(null)
const sceneRef = shallowRef<THREE.Scene | null>(null)
const cameraRef = shallowRef<THREE.OrthographicCamera | null>(null)
const materialRef = shallowRef<THREE.NodeMaterial | null>(null)
const geometryRef = shallowRef<THREE.PlaneGeometry | null>(null)
const uniformsRef = shallowRef<ReturnType<typeof createUniforms> | null>(null)

let cleanup: (() => void) | null = null
let didEmitUnavailable = false
let isUnmounted = false

function createUniforms() {
  return {
    time: uniform(0),
    mouse: uniform(new THREE.Vector2()),
    topColor: uniform(new THREE.Color(props.topColor)),
    bottomColor: uniform(new THREE.Color(props.bottomColor)),
    intensity: uniform(props.intensity),
    interactive: uniform(props.interactive),
    glowAmount: uniform(props.glowAmount),
    pillarWidth: uniform(props.pillarWidth),
    pillarHeight: uniform(props.pillarHeight),
    noiseIntensity: uniform(props.noiseIntensity),
    pillarRotation: uniform(props.pillarRotation),
    toneBase: uniform(props.toneCurve.base),
    toneGamma: uniform(props.toneCurve.gamma),
    toneHighlightStart: uniform(props.toneCurve.highlightStart),
    toneHighlightGain: uniform(props.toneCurve.highlightGain),
    toneHighlightBias: uniform(props.toneCurve.highlightBias),
  }
}

function emitUnavailable() {
  if (didEmitUnavailable) return
  didEmitUnavailable = true
  emit('unavailable')
}

async function recordHdrDiagnostics({
  renderer,
  scene,
  camera,
  container,
}: {
  renderer: THREE.WebGPURenderer
  scene: THREE.Scene
  camera: THREE.OrthographicCamera
  container: HTMLElement
}) {
  if (!new URLSearchParams(window.location.search).has('hdrDebug')) return

  const width = 160
  const height = 100
  const renderTarget = new THREE.RenderTarget(width, height, {
    type: THREE.HalfFloatType,
    depthBuffer: false,
  })

  renderer.setRenderTarget(renderTarget)
  renderer.render(scene, camera)
  await renderer.backend.device!.queue.onSubmittedWorkDone()

  const pixels = await renderer.readRenderTargetPixelsAsync(renderTarget, 0, 0, width, height)
  let peak = 0
  let highlightPixels = 0
  let xdrCorePixels = 0

  for (let pixel = 0; pixel < width * height; pixel += 1) {
    const offset = pixel * 4
    const red =
      pixels instanceof Uint16Array
        ? THREE.DataUtils.fromHalfFloat(pixels[offset]!)
        : Number(pixels[offset])
    const green =
      pixels instanceof Uint16Array
        ? THREE.DataUtils.fromHalfFloat(pixels[offset + 1]!)
        : Number(pixels[offset + 1])
    const blue =
      pixels instanceof Uint16Array
        ? THREE.DataUtils.fromHalfFloat(pixels[offset + 2]!)
        : Number(pixels[offset + 2])
    const pixelPeak = Math.max(red, green, blue)

    peak = Math.max(peak, pixelPeak)
    if (pixelPeak > 1) highlightPixels += 1
    if (pixelPeak >= MACBOOK_PRO_XDR_CORE_THRESHOLD) xdrCorePixels += 1
  }

  container.dataset.hdrPeak = peak.toFixed(3)
  container.dataset.hdrHighlightRatio = (highlightPixels / (width * height)).toFixed(4)
  container.dataset.hdrXdrCoreRatio = (xdrCorePixels / (width * height)).toFixed(4)

  renderer.setRenderTarget(null)
  renderTarget.dispose()
  renderer.render(scene, camera)
  await renderer.backend.device!.queue.onSubmittedWorkDone()
}

async function setup() {
  const container = containerRef.value
  if (!container) return

  await new Promise<void>((resolve) => {
    requestAnimationFrame(() => resolve())
  })
  if (isUnmounted) return

  const uniforms = createUniforms()
  uniformsRef.value = uniforms

  const lightPillar = wgslFn(`
    fn renderLightPillar(
      normalizedUv: vec2<f32>,
      resolution: vec2<f32>,
      time: f32,
      mouse: vec2<f32>,
      topColor: vec3<f32>,
      bottomColor: vec3<f32>,
      intensity: f32,
      interactive: bool,
      glowAmount: f32,
      pillarWidth: f32,
      pillarHeight: f32,
      noiseIntensity: f32,
      pillarRotation: f32,
      toneBase: f32,
      toneGamma: f32,
      toneHighlightStart: f32,
      toneHighlightGain: f32,
      toneHighlightBias: f32
    ) -> vec4<f32> {
      return lightPillar(
        normalizedUv,
        resolution,
        time,
        mouse,
        topColor,
        bottomColor,
        intensity,
        interactive,
        glowAmount,
        pillarWidth,
        pillarHeight,
        noiseIntensity,
        pillarRotation,
        toneBase,
        toneGamma,
        toneHighlightStart,
        toneHighlightGain,
        toneHighlightBias
      );
    }

    fn rotate2d(point: vec2<f32>, angle: f32) -> vec2<f32> {
      let sine = sin(angle);
      let cosine = cos(angle);
      return vec2<f32>(
        point.x * cosine - point.y * sine,
        point.x * sine + point.y * cosine
      );
    }

    fn noise(coordinate: vec2<f32>) -> f32 {
      let e = 2.718281828459045;
      let randomPair = e * sin(e * coordinate);
      return fract(randomPair.x * randomPair.y * (1.0 + coordinate.x));
    }

    fn applyWaveDeformation(position: vec3<f32>, timeOffset: f32) -> vec3<f32> {
      var deformed = position;
      var frequency = 1.0;
      var amplitude = 1.0;

      for (var i = 0; i < 4; i++) {
        let rotated = rotate2d(deformed.xz, 0.4);
        deformed = vec3<f32>(rotated.x, deformed.y, rotated.y);
        let phase = timeOffset * f32(i) * 2.0;
        deformed += cos(deformed.zxy * frequency - phase) * amplitude;
        frequency *= 2.0;
        amplitude *= 0.5;
      }

      return deformed;
    }

    fn blendMin(a: f32, b: f32, amount: f32) -> f32 {
      let scaledAmount = amount * 4.0;
      let h = max(scaledAmount - abs(a - b), 0.0);
      return min(a, b) - h * h * 0.25 / scaledAmount;
    }

    fn blendMax(a: f32, b: f32, amount: f32) -> f32 {
      return -blendMin(-a, -b, amount);
    }

    fn lightPillar(
      normalizedUv: vec2<f32>,
      resolution: vec2<f32>,
      time: f32,
      mouse: vec2<f32>,
      topColor: vec3<f32>,
      bottomColor: vec3<f32>,
      intensity: f32,
      interactive: bool,
      glowAmount: f32,
      pillarWidth: f32,
      pillarHeight: f32,
      noiseIntensity: f32,
      pillarRotation: f32,
      toneBase: f32,
      toneGamma: f32,
      toneHighlightStart: f32,
      toneHighlightGain: f32,
      toneHighlightBias: f32
    ) -> vec4<f32> {
      let shaderUv = vec2<f32>(normalizedUv.x, 1.0 - normalizedUv.y);
      var uv = (shaderUv * resolution * 2.0 - resolution) / resolution.y;
      uv = rotate2d(uv, pillarRotation * ${Math.PI / 180});

      let origin = vec3<f32>(0.0, 0.0, -10.0);
      let direction = normalize(vec3<f32>(uv, 1.0));
      var depth = 0.1;
      var rayRotation = time * 0.3;

      if (interactive && length(mouse) > 0.0) {
        rayRotation = mouse.x * ${Math.PI * 2};
      }

      var result = vec3<f32>(0.0);

      for (var i = 0; i < 100; i++) {
        var position = origin + direction * depth;
        let rotatedPosition = rotate2d(position.xz, rayRotation);
        position = vec3<f32>(rotatedPosition.x, position.y, rotatedPosition.y);

        var deformed = position;
        deformed.y *= pillarHeight;
        deformed = applyWaveDeformation(deformed + vec3<f32>(0.0, time, 0.0), time);

        let cosinePair = cos(deformed.xz);
        var fieldDistance = length(cosinePair) - 0.2;
        let radialBound = length(position.xz) - pillarWidth;

        fieldDistance = blendMax(radialBound, fieldDistance, 1.0);
        fieldDistance = abs(fieldDistance) * 0.15 + 0.01;

        let verticalMix = 1.0 - smoothstep(-15.0, 15.0, position.y);
        let gradient = mix(bottomColor, topColor, verticalMix);
        result += gradient / fieldDistance;
        depth += fieldDistance;

        if (fieldDistance < 0.001 || depth > 50.0) {
          break;
        }
      }

      let widthNormalization = pillarWidth / 3.0;
      let signal = result * glowAmount / widthNormalization;
      var referenceRange = tanh(signal);
      referenceRange -=
        noise(shaderUv * resolution) /
        15.0 *
        noiseIntensity;
      referenceRange = clamp(
        referenceRange * intensity,
        vec3<f32>(0.0),
        vec3<f32>(1.0)
      );
      let referenceLuminance = dot(
        referenceRange,
        vec3<f32>(0.2126, 0.7152, 0.0722)
      );
      let mappedReferenceLuminance =
        pow(referenceLuminance, toneGamma) *
        toneBase;
      referenceRange *=
        mappedReferenceLuminance /
        max(referenceLuminance, 0.001);
      referenceRange = max(
        mix(
          vec3<f32>(mappedReferenceLuminance),
          referenceRange,
          1.65
        ),
        vec3<f32>(0.0)
      );

      let signalLuminance = dot(signal, vec3<f32>(0.2126, 0.7152, 0.0722));
      let highlightMask = smoothstep(
        toneHighlightStart,
        toneHighlightStart + 1.5,
        signalLuminance
      );
      let horizontalHighlightWeight = max(
        0.0,
        1.0 + (shaderUv.x * 2.0 - 1.0) * toneHighlightBias
      );
      let highlightEnergy =
        min(
          max(signalLuminance - toneHighlightStart, 0.0) *
          toneHighlightGain *
          horizontalHighlightWeight,
          3.1
        ) *
        highlightMask;
      let highlightColor =
        signal / max(signalLuminance, 0.001) *
        highlightEnergy;
      let hdrColor = clamp(
        referenceRange + highlightColor,
        vec3<f32>(0.0),
        vec3<f32>(${MACBOOK_PRO_XDR_HEADROOM})
      );

      return vec4<f32>(hdrColor, 1.0);
    }
  `)

  const fragmentNode = lightPillar(
    screenUV,
    screenSize,
    uniforms.time,
    uniforms.mouse,
    uniforms.topColor,
    uniforms.bottomColor,
    uniforms.intensity,
    uniforms.interactive,
    uniforms.glowAmount,
    uniforms.pillarWidth,
    uniforms.pillarHeight,
    uniforms.noiseIntensity,
    uniforms.pillarRotation,
    uniforms.toneBase,
    uniforms.toneGamma,
    uniforms.toneHighlightStart,
    uniforms.toneHighlightGain,
    uniforms.toneHighlightBias,
  )

  const scene = new THREE.Scene()
  const camera = new THREE.OrthographicCamera(-1, 1, 1, -1, 0, 1)
  sceneRef.value = scene
  cameraRef.value = camera
  const material = new THREE.NodeMaterial()
  material.vertexNode = vec4(positionGeometry, 1)
  material.fragmentNode = fragmentNode
  material.depthWrite = false
  material.depthTest = false
  material.transparent = false
  materialRef.value = material

  const geometry = new THREE.PlaneGeometry(2, 2)
  geometryRef.value = geometry
  scene.add(new THREE.Mesh(geometry, material))

  const renderer = new THREE.WebGPURenderer({
    antialias: false,
    alpha: false,
    depth: false,
    stencil: false,
    powerPreference: 'high-performance',
    outputType: THREE.HalfFloatType,
  })
  rendererRef.value = renderer

  const disposeAttempt = () => {
    renderer.setAnimationLoop(null)
    renderer.dispose()
    material.dispose()
    geometry.dispose()
    if (container.contains(renderer.domElement)) container.removeChild(renderer.domElement)
    rendererRef.value = null
    materialRef.value = null
    geometryRef.value = null
    uniformsRef.value = null
    sceneRef.value = null
    cameraRef.value = null
  }

  try {
    await renderer.init()
    if (isUnmounted) {
      disposeAttempt()
      return
    }

    if (renderer.backend.isWebGPUBackend !== true) {
      disposeAttempt()
      emitUnavailable()
      return
    }

    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
    renderer.setSize(Math.max(container.clientWidth, 1), Math.max(container.clientHeight, 1))
    container.appendChild(renderer.domElement)

    renderer.backend.device!.pushErrorScope('validation')
    renderer.render(scene, camera)
    await renderer.backend.device!.queue.onSubmittedWorkDone()
    const validationError = await renderer.backend.device!.popErrorScope()
    if (validationError) {
      console.warn(`HDR renderer unavailable: ${validationError.message}`)
      disposeAttempt()
      emitUnavailable()
      return
    }

    await recordHdrDiagnostics({ renderer, scene, camera, container })
    emit('ready')
  } catch {
    disposeAttempt()
    emitUnavailable()
    return
  }

  let mouseMoveFrame: number | null = null
  const handleMouseMove = (event: MouseEvent) => {
    if (!props.interactive || mouseMoveFrame !== null) return

    mouseMoveFrame = requestAnimationFrame(() => {
      mouseMoveFrame = null
      const rect = container.getBoundingClientRect()
      uniforms.mouse.value.set(
        ((event.clientX - rect.left) / rect.width) * 2 - 1,
        -((event.clientY - rect.top) / rect.height) * 2 + 1,
      )
    })
  }

  if (props.interactive) {
    container.addEventListener('mousemove', handleMouseMove, { passive: true })
  }

  let lastTime = performance.now()
  let lastRenderTime = lastTime
  const frameInterval = 1000 / 60
  const animate = (currentTime: number) => {
    if (currentTime - lastRenderTime < frameInterval) return

    const deltaSeconds = Math.min((currentTime - lastTime) / 1000, 0.1)
    uniforms.time.value += deltaSeconds * props.rotationSpeed
    lastTime = currentTime
    lastRenderTime = currentTime
    renderer.render(scene, camera)
  }
  if (!window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    renderer.setAnimationLoop(animate)
  }

  let resizeFrame: number | null = null
  const handleResize = () => {
    if (resizeFrame !== null) cancelAnimationFrame(resizeFrame)
    resizeFrame = requestAnimationFrame(() => {
      resizeFrame = null
      renderer.setSize(Math.max(container.clientWidth, 1), Math.max(container.clientHeight, 1))
    })
  }
  window.addEventListener('resize', handleResize, { passive: true })

  let isActive = true
  const handleDeviceLost = () => {
    if (isActive) emitUnavailable()
  }
  void renderer.backend.device?.lost.then(handleDeviceLost)

  cleanup = () => {
    isActive = false
    window.removeEventListener('resize', handleResize)
    container.removeEventListener('mousemove', handleMouseMove)
    if (mouseMoveFrame !== null) cancelAnimationFrame(mouseMoveFrame)
    if (resizeFrame !== null) cancelAnimationFrame(resizeFrame)
    disposeAttempt()
  }
}

onMounted(() => {
  void setup()
})

onBeforeUnmount(() => {
  isUnmounted = true
  cleanup?.()
})

watch(
  () => [
    props.topColor,
    props.bottomColor,
    props.intensity,
    props.interactive,
    props.glowAmount,
    props.pillarWidth,
    props.pillarHeight,
    props.noiseIntensity,
    props.pillarRotation,
    props.toneCurve.base,
    props.toneCurve.gamma,
    props.toneCurve.highlightStart,
    props.toneCurve.highlightGain,
    props.toneCurve.highlightBias,
  ],
  () => {
    const uniforms = uniformsRef.value
    if (!uniforms) return
    uniforms.topColor.value.set(props.topColor)
    uniforms.bottomColor.value.set(props.bottomColor)
    uniforms.intensity.value = props.intensity
    uniforms.interactive.value = props.interactive
    uniforms.glowAmount.value = props.glowAmount
    uniforms.pillarWidth.value = props.pillarWidth
    uniforms.pillarHeight.value = props.pillarHeight
    uniforms.noiseIntensity.value = props.noiseIntensity
    uniforms.pillarRotation.value = props.pillarRotation
    uniforms.toneBase.value = props.toneCurve.base
    uniforms.toneGamma.value = props.toneCurve.gamma
    uniforms.toneHighlightStart.value = props.toneCurve.highlightStart
    uniforms.toneHighlightGain.value = props.toneCurve.highlightGain
    uniforms.toneHighlightBias.value = props.toneCurve.highlightBias

    const renderer = rendererRef.value
    const scene = sceneRef.value
    const camera = cameraRef.value
    if (renderer && scene && camera) renderer.render(scene, camera)
  },
)
</script>

<template>
  <div
    ref="containerRef"
    data-render-backend="webgpu-hdr"
    data-hdr-target="macbook-pro-xdr"
    :data-output-headroom="MACBOOK_PRO_XDR_HEADROOM"
    class="absolute top-0 left-0 h-full w-full"
    :style="{ mixBlendMode }"
  />
</template>
