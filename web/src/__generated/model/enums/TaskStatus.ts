export const TaskStatus_CONSTANTS = [
    'PENDING', 
    'RUNNING', 
    'COMPLETED', 
    'FAILED', 
    'CANCELLED'
] as const;
export type TaskStatus = typeof TaskStatus_CONSTANTS[number];
