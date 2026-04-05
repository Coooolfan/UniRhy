export const PlaybackPreference_CONSTANTS = [
    'RAW', 
    'OPUS', 
    'MP3'
] as const;
export type PlaybackPreference = typeof PlaybackPreference_CONSTANTS[number];
