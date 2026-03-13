export const TaskStatus_CONSTANTS = [
    'PENDING', 
    'RUNNING', 
    'COMPLETED', 
    'FAILED'
] as const;
export type TaskStatus = typeof TaskStatus_CONSTANTS[number];
