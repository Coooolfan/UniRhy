import type {LoginTransferPlatform, LoginTransferStatus} from '../enums/';

export type LoginTransferDto = {
    /**
     * 原设备视图：可以看到认领设备的信息。
     */
    'LoginTransferController/SOURCE_TRANSFER_FETCHER': {
        readonly id: string;
        readonly deviceName?: string | undefined;
        readonly platform?: LoginTransferPlatform | undefined;
        readonly clientVersion?: string | undefined;
        readonly status: LoginTransferStatus;
        readonly createdAt: string;
        readonly expiresAt: string;
        readonly claimedAt?: string | undefined;
        readonly authorizedAt?: string | undefined;
        readonly closedAt?: string | undefined;
    }
}
