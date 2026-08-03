export const LoginTransferStatus_CONSTANTS = [
    'WAITING', 
    'CLAIMED', 
    'AUTHORIZED', 
    'COMPLETED', 
    'REJECTED', 
    'CANCELLED', 
    'EXPIRED'
] as const;
export type LoginTransferStatus = typeof LoginTransferStatus_CONSTANTS[number];
