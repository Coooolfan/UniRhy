import type { LoginTransferStatus } from '@/__generated/model/enums'

/** 交接仍在进行中的状态：需要继续轮询，且允许原设备取消。 */
export const ACTIVE_LOGIN_TRANSFER_STATUSES: ReadonlySet<LoginTransferStatus> = new Set([
    'WAITING',
    'CLAIMED',
    'AUTHORIZED',
])

export const isActiveLoginTransfer = (status: LoginTransferStatus): boolean =>
    ACTIVE_LOGIN_TRANSFER_STATUSES.has(status)
