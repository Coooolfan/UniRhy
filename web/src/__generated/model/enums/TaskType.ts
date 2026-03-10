export const TaskType_CONSTANTS = [
    'SCAN', 
    'TRANSCODE'
] as const;
export type TaskType = typeof TaskType_CONSTANTS[number];
