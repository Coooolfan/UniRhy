package com.coooolfan.unirhy.service.task

import com.coooolfan.unirhy.model.*
import com.coooolfan.unirhy.service.task.common.AsyncTaskQueueStore
import com.coooolfan.unirhy.service.task.common.TaskStatus
import com.coooolfan.unirhy.service.task.common.TaskType
import org.babyfish.jimmer.Page
import org.babyfish.jimmer.sql.fetcher.Fetcher
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.desc
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.babyfish.jimmer.sql.kt.ast.expression.valueIn
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class AsyncTaskLogService(
    private val sql: KSqlClient,
    private val queueStore: AsyncTaskQueueStore,
) {

    fun listCounts(): List<AsyncTaskLogCountRow> {
        return queueStore.listCounts()
    }

    fun listByTypeAndStatuses(
        taskType: TaskType,
        statuses: List<TaskStatus>,
        pageIndex: Int,
        pageSize: Int,
        fetcher: Fetcher<AsyncTaskLog>,
    ): Page<AsyncTaskLog> {
        if (statuses.isEmpty()) {
            return Page(emptyList(), 0L, 0L)
        }
        return sql.createQuery(AsyncTaskLog::class) {
            where(table.taskType eq taskType)
            where(table.status valueIn statuses)
            orderBy(table.createdAt.desc(), table.id.desc())
            select(table.fetch(fetcher))
        }.fetchPage(pageIndex, pageSize)
    }

    @Transactional
    fun resetToPending(id: Long) {
        val existing = sql.findById(AsyncTaskLog::class, id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "task log not found")
        if (existing.status != TaskStatus.FAILED && existing.status != TaskStatus.COMPLETED) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "only FAILED or COMPLETED tasks can be reset, current status: ${existing.status}",
            )
        }
        sql.createUpdate(AsyncTaskLog::class) {
            set(table.status, TaskStatus.PENDING)
            set(table.startedAt, null)
            set(table.completedAt, null)
            set(table.completedReason, null)
            where(table.id eq id)
        }.execute()
    }
}
