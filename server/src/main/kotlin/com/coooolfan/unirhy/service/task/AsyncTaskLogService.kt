package com.coooolfan.unirhy.service.task

import com.coooolfan.unirhy.service.task.common.AsyncTaskQueueStore
import org.springframework.stereotype.Service

@Service
class AsyncTaskLogService(
    private val queueStore: AsyncTaskQueueStore,
) {

    fun listCounts(): List<AsyncTaskLogCountRow> {
        return queueStore.listCounts()
    }
}
