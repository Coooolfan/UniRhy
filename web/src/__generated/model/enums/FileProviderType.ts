export const FileProviderType_CONSTANTS = [
    'OSS', 
    'FILE_SYSTEM'
] as const;
export type FileProviderType = typeof FileProviderType_CONSTANTS[number];
