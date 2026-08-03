import type {LoginTransferStatus} from '../enums/';

export interface LoginTransferCreateResponse {
    readonly id: string;
    readonly secret: string;
    readonly status: LoginTransferStatus;
    readonly createdAt: string;
    readonly expiresAt: string;
}
