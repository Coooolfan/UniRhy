export const StopStrategy_CONSTANTS = [
    'TRACK', 
    'LIST'
] as const;
export type StopStrategy = typeof StopStrategy_CONSTANTS[number];
