export const PlaybackStrategy_CONSTANTS = [
    'SEQUENTIAL', 
    'SHUFFLE'
] as const;
export type PlaybackStrategy = typeof PlaybackStrategy_CONSTANTS[number];
