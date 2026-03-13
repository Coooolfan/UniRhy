package com.coooolfan.unirhy.service.task.common

enum class TaskStatus {
    PENDING,
    RUNNING, // 因为事务隔离，这个暂时总是返回 0
    COMPLETED,
    FAILED,
}