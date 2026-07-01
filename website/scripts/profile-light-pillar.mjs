import { execFileSync } from 'node:child_process'
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import path from 'node:path'

const baseUrl = process.env.LIGHT_PILLAR_URL ?? 'http://127.0.0.1:4173/'
const baseline = process.env.LIGHT_PILLAR_BASELINE ?? '/tmp/unirhy-clean-scale100-fixed.png'
const outputDir = process.env.LIGHT_PILLAR_OUTPUT_DIR ?? '/tmp/unirhy-light-pillar-profile'
const viewportWidth = Number(process.env.LIGHT_PILLAR_VIEWPORT_WIDTH ?? 1920)
const viewportHeight = Number(process.env.LIGHT_PILLAR_VIEWPORT_HEIGHT ?? 827)
const fixedTime = Number(process.env.LIGHT_PILLAR_FIXED_TIME ?? 12.34)
const sampleMs = Number(process.env.LIGHT_PILLAR_SAMPLE_MS ?? 10500)

if (!existsSync(baseline)) {
  throw new Error(`Baseline screenshot not found: ${baseline}`)
}

mkdirSync(outputDir, { recursive: true })

function runAgent(args, options = {}) {
  const output = execFileSync('agent-browser', args, {
    encoding: 'utf8',
    input: options.input,
    stdio: options.stdio ?? [options.input === undefined ? 'ignore' : 'pipe', 'pipe', 'pipe'],
  })
  return typeof output === 'string' ? output.trim() : ''
}

function closeBrowsers() {
  try {
    runAgent(['close', '--all'], { stdio: 'ignore' })
  } catch {
    // Ignore stale agent-browser state.
  }
}

function closeSession(session) {
  try {
    runAgent(['--session', session, 'close'], { stdio: 'ignore' })
  } catch {
    // The session may already be gone after a browser-level cleanup.
  }
}

function sessionName(prefix) {
  return `unirhy-${prefix}-${Date.now()}-${Math.round(Math.random() * 1_000_000)}`
}

function urlWithParams(params) {
  const url = new URL(baseUrl)
  for (const [key, value] of Object.entries(params)) {
    url.searchParams.set(key, String(value))
  }
  url.searchParams.set('run', `${Date.now()}${Math.round(Math.random() * 1_000_000)}`)
  return url.toString()
}

function evalInSession(session, expression) {
  const raw = runAgent(['--session', session, 'eval', expression])
  return parseEvalOutput(raw)
}

function evalScriptInSession(session, script) {
  const raw = runAgent(['--session', session, 'eval', '--stdin'], { input: script })
  return parseEvalOutput(raw)
}

function parseEvalOutput(raw) {
  try {
    return JSON.parse(JSON.parse(raw))
  } catch {
    return JSON.parse(raw)
  }
}

function imageDataUrl(filePath) {
  return `data:image/png;base64,${readFileSync(filePath, 'base64')}`
}

function measureImageDelta(session, candidatePath, baselinePath = baseline) {
  const baselineDataUrl = imageDataUrl(baselinePath)
  const candidateDataUrl = imageDataUrl(candidatePath)
  return evalScriptInSession(
    session,
    `
      (async () => {
        const baselineSrc = ${JSON.stringify(baselineDataUrl)};
        const candidateSrc = ${JSON.stringify(candidateDataUrl)};

        function loadImage(src) {
          return new Promise((resolve, reject) => {
            const image = new Image();
            image.onload = () => resolve(image);
            image.onerror = () => reject(new Error('Unable to decode screenshot'));
            image.src = src;
          });
        }

        function getImageData(image) {
          const canvas = document.createElement('canvas');
          canvas.width = image.naturalWidth;
          canvas.height = image.naturalHeight;
          const context = canvas.getContext('2d', { willReadFrequently: true });
          context.drawImage(image, 0, 0);
          return context.getImageData(0, 0, canvas.width, canvas.height);
        }

        const baselineImage = getImageData(await loadImage(baselineSrc));
        const candidateImage = getImageData(await loadImage(candidateSrc));
        if (
          baselineImage.width !== candidateImage.width ||
          baselineImage.height !== candidateImage.height
        ) {
          return JSON.stringify({
            width: candidateImage.width,
            height: candidateImage.height,
            baselineWidth: baselineImage.width,
            baselineHeight: baselineImage.height,
            dimensionMismatch: true,
          });
        }

        const histogram = new Uint32Array(256);
        let sumAbs = 0;
        let sumSquared = 0;
        let sumLumaAbs = 0;
        let maxChannelDelta = 0;
        let maxLumaDelta = 0;
        let over1 = 0;
        let over2 = 0;
        let over5 = 0;
        let over10 = 0;
        const pixels = baselineImage.width * baselineImage.height;

        for (let index = 0; index < baselineImage.data.length; index += 4) {
          const dr = Math.abs(baselineImage.data[index] - candidateImage.data[index]);
          const dg = Math.abs(baselineImage.data[index + 1] - candidateImage.data[index + 1]);
          const db = Math.abs(baselineImage.data[index + 2] - candidateImage.data[index + 2]);
          const channelDelta = Math.max(dr, dg, db);
          const lumaDelta = Math.abs(dr * 0.2126 + dg * 0.7152 + db * 0.0722);

          histogram[channelDelta] += 1;
          sumAbs += dr + dg + db;
          sumSquared += dr * dr + dg * dg + db * db;
          sumLumaAbs += lumaDelta;
          maxChannelDelta = Math.max(maxChannelDelta, channelDelta);
          maxLumaDelta = Math.max(maxLumaDelta, lumaDelta);
          if (channelDelta > 1) over1 += 1;
          if (channelDelta > 2) over2 += 1;
          if (channelDelta > 5) over5 += 1;
          if (channelDelta > 10) over10 += 1;
        }

        function percentileDelta(ratio) {
          const target = Math.ceil(pixels * ratio);
          let count = 0;
          for (let value = 0; value < histogram.length; value += 1) {
            count += histogram[value];
            if (count >= target) return value;
          }
          return 255;
        }

        return JSON.stringify({
          width: candidateImage.width,
          height: candidateImage.height,
          dimensionMismatch: false,
          mae: sumAbs / (pixels * 3),
          rmse: Math.sqrt(sumSquared / (pixels * 3)),
          lumaMae: sumLumaAbs / pixels,
          p95ChannelDelta: percentileDelta(0.95),
          p99ChannelDelta: percentileDelta(0.99),
          maxChannelDelta,
          maxLumaDelta,
          over1Ratio: over1 / pixels,
          over2Ratio: over2 / pixels,
          over5Ratio: over5 / pixels,
          over10Ratio: over10 / pixels,
        });
      })()
    `,
  )
}

function measureBackgroundDelta(session, candidatePath, baselinePath) {
  const baselineDataUrl = imageDataUrl(baselinePath)
  const candidateDataUrl = imageDataUrl(candidatePath)
  return evalScriptInSession(
    session,
    `
      (async () => {
        const baselineSrc = ${JSON.stringify(baselineDataUrl)};
        const candidateSrc = ${JSON.stringify(candidateDataUrl)};

        function loadImage(src) {
          return new Promise((resolve, reject) => {
            const image = new Image();
            image.onload = () => resolve(image);
            image.onerror = () => reject(new Error('Unable to decode screenshot'));
            image.src = src;
          });
        }

        function getImageData(image) {
          const canvas = document.createElement('canvas');
          canvas.width = image.naturalWidth;
          canvas.height = image.naturalHeight;
          const context = canvas.getContext('2d', { willReadFrequently: true });
          context.drawImage(image, 0, 0);
          return context.getImageData(0, 0, canvas.width, canvas.height);
        }

        function lumaAt(image, x, y) {
          const index = (y * image.width + x) * 4;
          return (
            image.data[index] * 0.2126 +
            image.data[index + 1] * 0.7152 +
            image.data[index + 2] * 0.0722
          );
        }

        const baselineImage = getImageData(await loadImage(baselineSrc));
        const candidateImage = getImageData(await loadImage(candidateSrc));
        if (
          baselineImage.width !== candidateImage.width ||
          baselineImage.height !== candidateImage.height
        ) {
          return JSON.stringify({
            width: candidateImage.width,
            height: candidateImage.height,
            baselineWidth: baselineImage.width,
            baselineHeight: baselineImage.height,
            dimensionMismatch: true,
          });
        }

        let lumaSumAbs = 0;
        let lumaSumSquared = 0;
        let gradientBaseline = 0;
        let gradientCandidate = 0;
        let edgeGradientSumAbs = 0;
        let edgeGradientSumSquared = 0;
        let edgeCount = 0;
        let count = 0;

        for (let y = 1; y < baselineImage.height - 1; y += 1) {
          for (let x = 1; x < baselineImage.width - 1; x += 1) {
            const baselineLuma = lumaAt(baselineImage, x, y);
            const candidateLuma = lumaAt(candidateImage, x, y);
            const lumaDelta = Math.abs(baselineLuma - candidateLuma);
            lumaSumAbs += lumaDelta;
            lumaSumSquared += lumaDelta * lumaDelta;
            count += 1;

            const baselineGradient = Math.hypot(
              Math.abs(lumaAt(baselineImage, x + 1, y) - lumaAt(baselineImage, x - 1, y)),
              Math.abs(lumaAt(baselineImage, x, y + 1) - lumaAt(baselineImage, x, y - 1)),
            );
            const candidateGradient = Math.hypot(
              Math.abs(lumaAt(candidateImage, x + 1, y) - lumaAt(candidateImage, x - 1, y)),
              Math.abs(lumaAt(candidateImage, x, y + 1) - lumaAt(candidateImage, x, y - 1)),
            );
            gradientBaseline += baselineGradient;
            gradientCandidate += candidateGradient;

            if (baselineGradient > 6) {
              const edgeGradientDelta = Math.abs(baselineGradient - candidateGradient);
              edgeGradientSumAbs += edgeGradientDelta;
              edgeGradientSumSquared += edgeGradientDelta * edgeGradientDelta;
              edgeCount += 1;
            }
          }
        }

        return JSON.stringify({
          width: candidateImage.width,
          height: candidateImage.height,
          dimensionMismatch: false,
          lumaMae: lumaSumAbs / count,
          lumaRmse: Math.sqrt(lumaSumSquared / count),
          gradientRatio: gradientCandidate / gradientBaseline,
          edgeGradientMae: edgeCount > 0 ? edgeGradientSumAbs / edgeCount : 0,
          edgeGradientRmse:
            edgeCount > 0 ? Math.sqrt(edgeGradientSumSquared / edgeCount) : 0,
          edgeCount,
        });
      })()
    `,
  )
}

function openProfiledPage(session, url) {
  runAgent(['--session', session, 'open', 'about:blank'], { stdio: 'ignore' })
  runAgent(
    ['--session', session, 'set', 'viewport', String(viewportWidth), String(viewportHeight)],
    {
      stdio: 'ignore',
    },
  )
  runAgent(['--session', session, 'open', url], { stdio: 'ignore' })
  runAgent(['--session', session, 'wait', '1800'], { stdio: 'ignore' })
}

function hideProfilerPanel(session) {
  runAgent(
    [
      '--session',
      session,
      'eval',
      '(() => { const panel = document.querySelector(".absolute.top-4.left-4.z-30"); if (panel) panel.style.display = "none"; return "ok"; })()',
    ],
    { stdio: 'ignore' },
  )
}

function hideHomeOverlay(session) {
  runAgent(
    [
      '--session',
      session,
      'eval',
      '(() => { document.querySelectorAll(".home-view > div:not(:first-child)").forEach((element) => { element.style.display = "none"; }); return "ok"; })()',
    ],
    { stdio: 'ignore' },
  )
}

function captureBackgroundQuality() {
  const session = sessionName('background')
  const baselineScreenshotPath = path.join(outputDir, 'background-baseline.png')
  const candidateScreenshotPath = path.join(outputDir, 'background.png')

  try {
    openProfiledPage(
      session,
      urlWithParams({
        profileLightPillar: '1',
        lightPillarFixedTime: fixedTime,
        lightPillarScaleX: 1,
        lightPillarScaleY: 1,
      }),
    )
    hideProfilerPanel(session)
    hideHomeOverlay(session)
    runAgent(['--session', session, 'screenshot', baselineScreenshotPath], { stdio: 'ignore' })

    openProfiledPage(
      session,
      urlWithParams({
        profileLightPillar: '1',
        lightPillarFixedTime: fixedTime,
      }),
    )
    hideProfilerPanel(session)
    hideHomeOverlay(session)
    const metadata = evalInSession(
      session,
      '(() => { const c = document.querySelector("canvas"); return JSON.stringify({ hidden: document.hidden, canvas: c && { width: c.width, height: c.height, styleWidth: c.style.width, styleHeight: c.style.height, clientWidth: c.clientWidth, clientHeight: c.clientHeight } }); })()',
    )
    runAgent(['--session', session, 'screenshot', candidateScreenshotPath], { stdio: 'ignore' })

    return {
      metadata,
      baselineScreenshotPath,
      screenshotPath: candidateScreenshotPath,
      visualMetrics: measureBackgroundDelta(
        session,
        candidateScreenshotPath,
        baselineScreenshotPath,
      ),
    }
  } finally {
    closeSession(session)
  }
}

function captureFixedFrame() {
  const session = sessionName('fixed')
  const screenshotPath = path.join(outputDir, 'fixed.png')
  const diffPath = path.join(outputDir, 'fixed-diff.png')
  const diffLogPath = path.join(outputDir, 'fixed-diff.txt')

  try {
    openProfiledPage(
      session,
      urlWithParams({
        profileLightPillar: '1',
        lightPillarFixedTime: fixedTime,
      }),
    )
    hideProfilerPanel(session)

    const metadata = evalInSession(
      session,
      '(() => { const c = document.querySelector("canvas"); return JSON.stringify({ hidden: document.hidden, hasProfiler: Boolean(window.unirhyLightPillarProfiler), canvas: c && { width: c.width, height: c.height, styleWidth: c.style.width, styleHeight: c.style.height, clientWidth: c.clientWidth, clientHeight: c.clientHeight } }); })()',
    )
    runAgent(['--session', session, 'screenshot', screenshotPath], { stdio: 'ignore' })
    const visualMetrics = measureImageDelta(session, screenshotPath)

    let diffOutput = ''
    try {
      diffOutput = runAgent([
        '--session',
        session,
        'diff',
        'screenshot',
        '--baseline',
        baseline,
        '--output',
        diffPath,
        '--threshold',
        '0.02',
      ])
    } catch (error) {
      diffOutput = `${error.stdout ?? ''}${error.stderr ?? ''}`.trim()
    }
    writeFileSync(diffLogPath, `${diffOutput}\n`)
    return { metadata, screenshotPath, diffPath, diffOutput, visualMetrics }
  } finally {
    closeSession(session)
  }
}

function captureGpuSummary() {
  const session = sessionName('gpu')
  try {
    openProfiledPage(session, urlWithParams({ profileLightPillar: '1' }))
    hideProfilerPanel(session)
    runAgent(
      [
        '--session',
        session,
        'eval',
        '(() => { window.unirhyLightPillarProfiler?.reset(); return "reset"; })()',
      ],
      { stdio: 'ignore' },
    )
    runAgent(['--session', session, 'wait', String(sampleMs)], { stdio: 'ignore' })
    return evalInSession(
      session,
      '(() => { const s = window.unirhyLightPillarProfiler?.summary; return JSON.stringify(s && { sampleDurationMs: s.sampleDurationMs, frameCount: s.frameCount, fps: s.fps, avgCallbackMs: s.avgCallbackMs, p95CallbackMs: s.p95CallbackMs, avgRenderCpuMs: s.avgRenderCpuMs, p95RenderCpuMs: s.p95RenderCpuMs, avgGpuMs: s.avgGpuMs, p95GpuMs: s.p95GpuMs, gpuSampleCount: s.gpuSampleCount, gpuFrameBudgetRatio: s.gpuFrameBudgetRatio, longTaskCount: s.longTaskCount, canvas: s.canvasWidth + "x" + s.canvasHeight, render: s.renderWidth + "x" + s.renderHeight, pixelRatio: s.pixelRatio, gpuTimerSupported: s.gpuTimerSupported, rendererName: s.rendererName }); })()',
    )
  } finally {
    closeSession(session)
  }
}

function captureHiddenBehavior() {
  const session = sessionName('hidden')
  try {
    openProfiledPage(session, urlWithParams({ profileLightPillar: '1' }))
    hideProfilerPanel(session)
    runAgent(
      [
        '--session',
        session,
        'eval',
        '(() => { window.unirhyLightPillarProfiler?.reset(); return "reset"; })()',
      ],
      { stdio: 'ignore' },
    )
    runAgent(['--session', session, 'wait', '1800'], { stdio: 'ignore' })
    const before = evalInSession(
      session,
      '(() => { const s = window.unirhyLightPillarProfiler?.summary; return JSON.stringify({ phase: "visible-before", hidden: document.hidden, frameCount: s?.frameCount, gpuSampleCount: s?.gpuSampleCount }); })()',
    )
    runAgent(
      [
        '--session',
        session,
        'eval',
        '(() => { Object.defineProperty(document, "hidden", { configurable: true, get: () => true }); document.dispatchEvent(new Event("visibilitychange")); return "hidden"; })()',
      ],
      { stdio: 'ignore' },
    )
    runAgent(['--session', session, 'wait', '2200'], { stdio: 'ignore' })
    const hidden = evalInSession(
      session,
      '(() => { const s = window.unirhyLightPillarProfiler?.summary; return JSON.stringify({ phase: "hidden", hidden: document.hidden, frameCount: s?.frameCount, gpuSampleCount: s?.gpuSampleCount }); })()',
    )
    runAgent(
      [
        '--session',
        session,
        'eval',
        '(() => { Object.defineProperty(document, "hidden", { configurable: true, get: () => false }); document.dispatchEvent(new Event("visibilitychange")); return "visible"; })()',
      ],
      { stdio: 'ignore' },
    )
    runAgent(['--session', session, 'wait', '2200'], { stdio: 'ignore' })
    const after = evalInSession(
      session,
      '(() => { const s = window.unirhyLightPillarProfiler?.summary; return JSON.stringify({ phase: "visible-after", hidden: document.hidden, frameCount: s?.frameCount, gpuSampleCount: s?.gpuSampleCount }); })()',
    )
    return { before, hidden, after }
  } finally {
    closeSession(session)
  }
}

closeBrowsers()

const result = {
  capturedAt: new Date().toISOString(),
  baseUrl,
  baseline,
  viewport: `${viewportWidth}x${viewportHeight}`,
  fixed: captureFixedFrame(),
  background: captureBackgroundQuality(),
  gpu: captureGpuSummary(),
  hidden: captureHiddenBehavior(),
}

closeBrowsers()

const resultPath = path.join(outputDir, 'result.json')
writeFileSync(resultPath, `${JSON.stringify(result, null, 2)}\n`)

console.log(JSON.stringify(result, null, 2))
console.log(`Result written to ${resultPath}`)
