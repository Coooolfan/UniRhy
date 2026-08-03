import type {LoginTransferPlatform, LoginTransferStatus} from '../enums/';

export interface LoginTransferUpdateRequest {
    readonly status: LoginTransferStatus;
    readonly secret?: string | undefined;
    readonly deviceName?: string | undefined;
    readonly platform?: LoginTransferPlatform | undefined;
    readonly clientVersion?: string | undefined;
}
