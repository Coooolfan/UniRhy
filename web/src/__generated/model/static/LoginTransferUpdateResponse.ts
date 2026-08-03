import type {LoginTransferDto} from '../dto/';

/**
 * PATCH 的统一响应。
 * 
 * [claimAccessToken] 仅在新设备认领成功时下发一次，审批响应不含该字段；
 * 认领响应按新设备投影裁剪，原设备专属字段缺省。
 */
export interface LoginTransferUpdateResponse {
    readonly transfer: LoginTransferDto['LoginTransferController/SOURCE_TRANSFER_FETCHER'];
    readonly claimAccessToken?: string | undefined;
}
