export const PlaybackStatus_CONSTANTS = [
    'PLAYING', 
    'PAUSED'
] as const;
export type PlaybackStatus = typeof PlaybackStatus_CONSTANTS[number];
