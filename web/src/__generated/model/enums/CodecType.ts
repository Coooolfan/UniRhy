export const CodecType_CONSTANTS = [
    'MP3', 
    'OPUS', 
    'AAC'
] as const;
export type CodecType = typeof CodecType_CONSTANTS[number];
