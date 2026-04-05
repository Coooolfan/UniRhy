export const PlaybackStrategy_CONSTANTS = [
    'SEQUENTIAL', 
    'SHUFFLE', 
    'RADIO'
] as const;
export type PlaybackStrategy = typeof PlaybackStrategy_CONSTANTS[number];
