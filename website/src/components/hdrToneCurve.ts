export interface HdrToneCurve {
  base: number
  gamma: number
  highlightStart: number
  highlightGain: number
  highlightBias: number
}

export const DEFAULT_HDR_TONE_CURVE: HdrToneCurve = {
  base: 0.4,
  gamma: 1.2,
  highlightStart: 0.25,
  highlightGain: 2.2,
  highlightBias: 0.75,
}
