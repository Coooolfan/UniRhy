package com.coooolfan.unirhy.service.task

import com.coooolfan.unirhy.model.*
import com.coooolfan.unirhy.service.task.common.AsyncTaskLogStatus
import com.coooolfan.unirhy.service.task.common.AsyncTaskManager
import com.coooolfan.unirhy.service.task.common.TaskType
import org.babyfish.jimmer.Page
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.desc
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.babyfish.jimmer.sql.kt.ast.expression.isNotNull
import org.babyfish.jimmer.sql.kt.ast.expression.isNull
import org.babyfish.jimmer.sql.kt.ast.expression.valueIn
import org.babyfish.jimmer.sql.kt.ast.expression.valueNotIn
import org.springframework.stereotype.Service

@Service
class AsyncTaskLogService(
    private val sql: KSqlClient,
    private val asyncTaskManager: AsyncTaskManager,
) {

    fun listLogs(
        pageIndex: Int,
        pageSize: Int,
        taskType: TaskType?,
        status: AsyncTaskLogStatus?,
    ): Page<AsyncTaskLogPageRow> {
        val runningLogIds = asyncTaskManager.listRunningLogIds()
        if (status == AsyncTaskLogStatus.RUNNING && runningLogIds.isEmpty()) {
            return Page(emptyList(), 0, 0)
        }

        val page = sql.createQuery(AsyncTaskLog::class) {
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
            select(table)
        }.fetchPage(pageIndex, pageSize)

        return Page(
            page.rows.map { log ->
                AsyncTaskLogPageRow(
                    id = log.id,
                    taskType = log.taskType,
                    startedAt = log.startedAt,
                    completedAt = log.completedAt,
                    params = log.params,
                    completedReason = log.completedReason,
                    status = statusOf(log, runningLogIds),
                )
            },
            page.totalRowCount,
            page.totalPageCount,
        )
    }

    private fun statusOf(log: AsyncTaskLog, runningLogIds: Set<Long>): AsyncTaskLogStatus {
        if (log.completedAt != null) {
            return AsyncTaskLogStatus.COMPLETED
        }
        if (log.id in runningLogIds) {
            return AsyncTaskLogStatus.RUNNING
        }
        return AsyncTaskLogStatus.ABORTED
    }
}
