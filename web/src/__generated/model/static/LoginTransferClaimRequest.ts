import type {LoginTransferPlatform} from '../enums/';

/**
 * 新设备的认领请求。
 * 
 * 二维码密钥同时充当查询索引与凭据，因此请求里不需要交接 id。
 */
export interface LoginTransferClaimRequest {
    readonly secret: string;
    readonly deviceName: string;
    readonly platform: LoginTransferPlatform;
    readonly clientVersion?: string | undefined;
}
