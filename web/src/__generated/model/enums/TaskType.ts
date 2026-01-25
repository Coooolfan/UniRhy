export const TaskType_CONSTANTS = [
    'SCAN'
] as const;
export type TaskType = typeof TaskType_CONSTANTS[number];
