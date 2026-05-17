export const TaskType_CONSTANTS = [
    'METADATA_PARSE', 
    'TRANSCODE', 
    'ARTIST_NORMALIZATION'
] as const;
export type TaskType = typeof TaskType_CONSTANTS[number];
