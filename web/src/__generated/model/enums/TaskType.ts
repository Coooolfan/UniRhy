export const TaskType_CONSTANTS = [
    'METADATA_PARSE', 
    'TRANSCODE', 
    'VECTORIZE', 
    'DATA_CLEAN', 
    'PLAYLIST_GENERATE'
] as const;
export type TaskType = typeof TaskType_CONSTANTS[number];
