export const TaskType_CONSTANTS = [
    'METADATA_PARSE', 
    'TRANSCODE', 
    'VECTORIZE', 
    'DATA_CLEAN'
] as const;
export type TaskType = typeof TaskType_CONSTANTS[number];
