package com.coooolfan.unirhy.service.task

import com.coooolfan.unirhy.model.*
import com.coooolfan.unirhy.service.task.common.AsyncTaskLogStatus
import com.coooolfan.unirhy.service.task.common.AsyncTaskManager
import com.coooolfan.unirhy.service.task.common.TaskType
import org.babyfish.jimmer.Page
import org.babyfish.jimmer.sql.fetcher.Fetcher
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.*
import org.springframework.stereotype.Service

@Service
class AsyncTaskLogService(
    private val sql: KSqlClient,
    private val asyncTaskManager: AsyncTaskManager,
) {

    fun listLogs(
        pageIndex: Int,
        pageSize: Int,
        fetcher: Fetcher<AsyncTaskLog>,
        taskType: TaskType?,
        status: AsyncTaskLogStatus?,
    ): Page<AsyncTaskLog> {
        val runningLogIds = asyncTaskManager.listRunningLogIds()
        if (status == AsyncTaskLogStatus.RUNNING && runningLogIds.isEmpty()) {
            return Page(emptyList(), 0, 0)
        }

        return sql.createQuery(AsyncTaskLog::class) {
            taskType?.let { where(table.taskType eq it) }

            when (status) {
                AsyncTaskLogStatus.COMPLETED -> where(table.completedAt.isNotNull())
                AsyncTaskLogStatus.RUNNING -> {
                    where(table.completedAt.isNull())
                    where(table.id valueIn runningLogIds)
                }

                AsyncTaskLogStatus.ABORTED -> {
                    where(table.completedAt.isNull())
                    if (runningLogIds.isNotEmpty()) {
                        where(table.id valueNotIn runningLogIds)
                    }
                }

                null -> {}
            }

            orderBy(table.startedAt.desc(), table.id.desc())
            select(table.fetch(fetcher))
        }.fetchPage(pageIndex, pageSize)
    }

}
