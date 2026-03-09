export const TaskType_CONSTANTS = [
    'SCAN', 
    'CODEC'
] as const;
export type TaskType = typeof TaskType_CONSTANTS[number];
