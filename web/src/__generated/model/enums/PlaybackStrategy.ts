export const PlaybackStrategy_CONSTANTS = [
    'SEQUENTIAL', 
    'SHUFFLE', 
    'SINGLE'
] as const;
export type PlaybackStrategy = typeof PlaybackStrategy_CONSTANTS[number];
