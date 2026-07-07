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

    /**
     * 按 ids / taskType / statuses 的交集重置任务为 PENDING。
     *
     * - 三个过滤条件均可选，但至少提供一个；否则视为误操作拒绝
     * - 状态过滤规则：caller 显式传入 statuses 时按 caller 意愿执行；未传时
     *   服务端兜底只匹配 FAILED / COMPLETED，避免 `?ids=` 路径把正在被 worker
     *   处理的 RUNNING 记录拽回 PENDING 造成 double-run
     * - 返回实际被重置的行数
     */
    @Transactional
    fun resetToPending(
        ids: List<Long>,
        taskType: TaskType?,
        statuses: List<TaskStatus>,
    ): Int {
        if (ids.isEmpty() && taskType == null && statuses.isEmpty()) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "at least one of ids / taskType / statuses must be provided",
            )
        }
        val effectiveStatuses = statuses.ifEmpty {
            listOf(TaskStatus.FAILED, TaskStatus.COMPLETED)
        }
        return sql.createUpdate(AsyncTaskLog::class) {
            if (ids.isNotEmpty()) where(table.id valueIn ids)
            if (taskType != null) where(table.taskType eq taskType)
            where(table.status valueIn effectiveStatuses)
            set(table.status, TaskStatus.PENDING)
            set(table.startedAt, null)
            set(table.completedAt, null)
            set(table.completedReason, null)
        }.execute()
    }
}
