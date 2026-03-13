package com.coooolfan.unirhy.service.task

import com.coooolfan.unirhy.service.task.common.TaskStatus
import com.coooolfan.unirhy.service.task.common.TaskType

data class AsyncTaskLogCountRow(
    val taskType: TaskType,
    val status: TaskStatus,
    val count: Long,
)
