package com.coooolfan.unirhy.service.task

import com.coooolfan.unirhy.service.task.common.AsyncTaskLogStatus
import com.coooolfan.unirhy.service.task.common.TaskType
import java.time.Instant

data class AsyncTaskLogPageRow(
    val id: Long,
    val taskType: TaskType,
    val startedAt: Instant,
    val completedAt: Instant?,
    val params: String,
    val completedReason: String?,
    val status: AsyncTaskLogStatus,
)
