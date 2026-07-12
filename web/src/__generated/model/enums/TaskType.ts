export const TaskType_CONSTANTS = [
    'METADATA_PARSE', 
    'TRANSCODE'
] as const;
export type TaskType = typeof TaskType_CONSTANTS[number];
