export const AsyncTaskLogStatus_CONSTANTS = [
    'RUNNING', 
    'COMPLETED', 
    'ABORTED'
] as const;
export type AsyncTaskLogStatus = typeof AsyncTaskLogStatus_CONSTANTS[number];
