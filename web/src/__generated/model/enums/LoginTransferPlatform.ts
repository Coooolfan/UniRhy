export const LoginTransferPlatform_CONSTANTS = [
    'ANDROID', 
    'IOS', 
    'MACOS', 
    'WINDOWS', 
    'LINUX', 
    'WEB'
] as const;
export type LoginTransferPlatform = typeof LoginTransferPlatform_CONSTANTS[number];
