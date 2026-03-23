export const TaskType_CONSTANTS = [
    'METADATA_PARSE', 
    'TRANSCODE', 
    'VECTORIZE'
] as const;
export type TaskType = typeof TaskType_CONSTANTS[number];
