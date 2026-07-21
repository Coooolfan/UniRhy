package com.coooolfan.unirhy.service.task.common

enum class TaskStatus {
    PENDING,
    RUNNING, // Worker 长事务提交前对其他连接不可见，管理查询通常观察不到此状态
    COMPLETED,
    FAILED,
    CANCELLED,
}
