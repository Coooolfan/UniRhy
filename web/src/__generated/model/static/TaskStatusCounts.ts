/**
 * 状态计数。`active` 为查询快照中 `PENDING` / `RUNNING` 的合计：
 * 长事务内的 `RUNNING` 对统计查询不可见，不提供集群级 pending / running 拆分。
 */
export interface TaskStatusCounts {
    readonly active: number;
    readonly completed: number;
    readonly failed: number;
    readonly cancelled: number;
    readonly total: number;
}
