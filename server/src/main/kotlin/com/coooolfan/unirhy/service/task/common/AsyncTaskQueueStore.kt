package com.coooolfan.unirhy.service.task.common

import com.coooolfan.unirhy.model.*
import com.coooolfan.unirhy.service.task.AsyncTaskLogCountRow
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.count
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class AsyncTaskQueueStore(
    private val sql: KSqlClient,
    private val jdbc: NamedParameterJdbcTemplate,
) {

    fun enqueueIgnoringConflicts(taskType: TaskType, paramsJsonList: List<String>): Int {
        // 这里用不了 Jimmer
        // Jimmer 生成的 ON CONFLICT DO NOTHING 总是会匹配具体索引
        // 无法解析此表的复杂索引
        if (paramsJsonList.isEmpty()) {
            return 0
        }
        val now = Instant.now()
        val sql = """
            INSERT INTO public.async_task_log (
                task_type,
                created_at,
                started_at,
                completed_at,
                params,
                completed_reason,
                status
            ) VALUES (
                :taskType,
                :createdAt,
                NULL,
                NULL,
                :params,
                NULL,
                :status
            )
            ON CONFLICT DO NOTHING
        """.trimIndent()
        val batchParams = paramsJsonList.map { paramsJson ->
            MapSqlParameterSource()
                .addValue("taskType", taskType.name)
                .addValue("createdAt", now)
                .addValue("params", paramsJson)
                .addValue("status", TaskStatus.PENDING.name)
        }.toTypedArray()
        return jdbc.batchUpdate(sql, batchParams).sum()
    }

    fun claimNext(taskType: TaskType): ClaimedAsyncTask? {
        val sql = """
            WITH grabbed_tasks AS (
                SELECT id
                FROM public.async_task_log
                WHERE task_type = :taskType
                  AND status = :pendingStatus
                ORDER BY created_at, id
                LIMIT 1
                FOR UPDATE SKIP LOCKED
            )
            UPDATE public.async_task_log task
            SET status = :runningStatus,
                started_at = now()
            WHERE task.id IN (SELECT id FROM grabbed_tasks)
            RETURNING task.id, task.params
        """.trimIndent()
        val params = MapSqlParameterSource()
            .addValue("taskType", taskType.name)
            .addValue("pendingStatus", TaskStatus.PENDING.name)
            .addValue("runningStatus", TaskStatus.RUNNING.name)
        return jdbc.query(sql, params) { rs, _ ->
            ClaimedAsyncTask(
                id = rs.getLong("id"),
                paramsJson = rs.getString("params"),
            )
        }.firstOrNull()
    }

    fun completeTask(logId: Long, status: TaskStatus, reason: String) {
        sql.createUpdate(AsyncTaskLog::class) {
            set(table.status, status)
            set(table.completedAt, Instant.now())
            set(table.completedReason, reason)
            where(table.id eq logId)
        }.execute()
    }

    fun listCounts(): List<AsyncTaskLogCountRow> {
        val actualCounts = sql.createQuery(AsyncTaskLog::class) {
            groupBy(table.taskType, table.status)
            select(
                table.taskType,
                table.status,
                count(table.id),
            )
        }.execute().associate { tuple ->
            val taskType = tuple._1
            val status = tuple._2
            (taskType to status) to tuple._3
        }

        return TaskType.entries.flatMap { taskType ->
            TaskStatus.entries.map { status ->
                AsyncTaskLogCountRow(
                    taskType = taskType,
                    status = status,
                    count = actualCounts[taskType to status] ?: 0L,
                )
            }
        }
    }

    data class ClaimedAsyncTask(
        val id: Long,
        val paramsJson: String,
    )
}
