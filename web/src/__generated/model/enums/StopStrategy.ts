export const StopStrategy_CONSTANTS = [
    'TRACK', 
    'LIST', 
    'NEVER'
] as const;
export type StopStrategy = typeof StopStrategy_CONSTANTS[number];
