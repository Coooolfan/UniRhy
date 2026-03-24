export const VectorizeMode_CONSTANTS = [
    'ALL', 
    'PENDING_ONLY'
] as const;
export type VectorizeMode = typeof VectorizeMode_CONSTANTS[number];
