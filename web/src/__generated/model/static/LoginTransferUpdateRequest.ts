import type {LoginTransferStatus} from '../enums/';

/**
 * 原设备的审批请求，只允许携带目标状态。
 */
export interface LoginTransferUpdateRequest {
    readonly status: LoginTransferStatus;
}
