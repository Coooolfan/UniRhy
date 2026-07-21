package com.coooolfan.unirhy.service.task.common

import com.coooolfan.unirhy.model.TaskSubmission
import com.coooolfan.unirhy.model.completedAt
import com.coooolfan.unirhy.model.completedReason
import com.coooolfan.unirhy.model.createdAt
import com.coooolfan.unirhy.model.id
import com.coooolfan.unirhy.model.namespace
import com.coooolfan.unirhy.model.status
import com.coooolfan.unirhy.model.taskType
import org.babyfish.jimmer.Page
import org.babyfish.jimmer.sql.fetcher.Fetcher
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.count
import org.babyfish.jimmer.sql.kt.ast.expression.desc
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.babyfish.jimmer.sql.kt.ast.expression.valueIn
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import java.time.Instant

data class ClaimedSubmission(
    val id: Long,
    val key: TaskKey,
    val paramsJson: String,
)

@Component
class TaskSubmissionStore(
    private val sql: KSqlClient,
    private val jdbc: NamedParameterJdbcTemplate,
) {

    /** 插入 PENDING submission，返回 id */
    fun insertPending(key: TaskKey, paramsJson: String): Long {
        val insertSql = """
            INSERT INTO public.task_submission (namespace, task_type, params, status)
            VALUES (:namespace, :taskType, CAST(:params AS jsonb), 'PENDING')
            RETURNING id
        """.trimIndent()
        val params = MapSqlParameterSource()
            .addValue("namespace", key.namespace)
            .addValue("taskType", key.taskType)
            .addValue("params", paramsJson)
        return jdbc.queryForObject(insertSql, params, Long::class.java)!!
    }

    /** 统计当前快照中可见的 PENDING submission 数量，按 TaskKey 分组 */
    fun discoverPendingCounts(): Map<TaskKey, Long> {
        val querySql = """
            SELECT namespace, task_type, count(*)
            FROM public.task_submission
            WHERE status = 'PENDING'
            GROUP BY namespace, task_type
        """.trimIndent()
        val result = mutableMapOf<TaskKey, Long>()
        jdbc.query(querySql) { rs ->
            TaskKey.ofOrNull(rs.getString(1), rs.getString(2))?.let { result[it] = rs.getLong(3) }
        }
        return result
    }

    /**
     * claim 一条指定 TaskKey 的 PENDING submission 并标记为 RUNNING。
     * 必须在事务内调用；行锁与连接保持到规划结束。
     */
    fun claimOne(key: TaskKey): ClaimedSubmission? {
        val claimSql = """
            WITH grabbed AS (
                SELECT id
                FROM public.task_submission
                WHERE namespace = :namespace
                  AND task_type = :taskType
                  AND status = 'PENDING'
                ORDER BY created_at, id
                LIMIT 1
                FOR UPDATE SKIP LOCKED
            )
            UPDATE public.task_submission s
            SET status = 'RUNNING',
                started_at = now()
            WHERE s.id IN (SELECT id FROM grabbed)
            RETURNING s.id, s.params
        """.trimIndent()
        val params = MapSqlParameterSource()
            .addValue("namespace", key.namespace)
            .addValue("taskType", key.taskType)
        return jdbc.query(claimSql, params) { rs, _ ->
            ClaimedSubmission(id = rs.getLong(1), key = key, paramsJson = rs.getString(2))
        }.firstOrNull()
    }

    fun complete(id: Long, status: TaskStatus, reason: String?) {
        sql.createUpdate(TaskSubmission::class) {
            set(table.status, status)
            set(table.completedAt, Instant.now())
            set(table.completedReason, reason)
            where(table.id eq id)
        }.execute()
    }

    fun findById(id: Long, fetcher: Fetcher<TaskSubmission>): TaskSubmission? =
        sql.createQuery(TaskSubmission::class) {
            where(table.id eq id)
            select(table.fetch(fetcher))
        }.execute().firstOrNull()

    fun list(
        namespace: String?,
        taskType: String?,
        statuses: List<TaskStatus>,
        pageIndex: Int,
        pageSize: Int,
        fetcher: Fetcher<TaskSubmission>,
    ): Page<TaskSubmission> =
        sql.createQuery(TaskSubmission::class) {
            namespace?.let { where(table.namespace eq it) }
            taskType?.let { where(table.taskType eq it) }
            if (statuses.isNotEmpty()) {
                where(table.status valueIn statuses)
            }
            orderBy(table.createdAt.desc(), table.id.desc())
            select(table.fetch(fetcher))
        }.fetchPage(pageIndex, pageSize)

    /**
     * 将尚未被 Planner 锁定的 PENDING submission 取消。返回实际更新的 id 集合。
     */
    fun cancelPending(ids: Collection<Long>, reason: String): List<Long> {
        if (ids.isEmpty()) return emptyList()
        val cancelSql = """
            WITH grabbed AS (
                SELECT id
                FROM public.task_submission
                WHERE id IN (:ids)
                  AND status = 'PENDING'
                FOR UPDATE SKIP LOCKED
            )
            UPDATE public.task_submission s
            SET status = 'CANCELLED',
                completed_at = now(),
                completed_reason = :reason
            WHERE s.id IN (SELECT id FROM grabbed)
            RETURNING s.id
        """.trimIndent()
        val params = MapSqlParameterSource()
            .addValue("ids", ids)
            .addValue("reason", reason)
        return jdbc.query(cancelSql, params) { rs, _ -> rs.getLong(1) }
    }

    /** 将 FAILED submission 重置为 PENDING，清空执行时间与失败原因。返回实际更新的 id 集合。 */
    fun requeueFailed(ids: Collection<Long>): List<Long> {
        if (ids.isEmpty()) return emptyList()
        val requeueSql = """
            WITH grabbed AS (
                SELECT id
                FROM public.task_submission
                WHERE id IN (:ids)
                  AND status = 'FAILED'
                FOR UPDATE SKIP LOCKED
            )
            UPDATE public.task_submission s
            SET status = 'PENDING',
                started_at = NULL,
                completed_at = NULL,
                completed_reason = NULL
            WHERE s.id IN (SELECT id FROM grabbed)
            RETURNING s.id
        """.trimIndent()
        return jdbc.query(requeueSql, MapSqlParameterSource("ids", ids)) { rs, _ -> rs.getLong(1) }
    }

    /**
     * 删除 submission 及其全部子任务（数据库级联）。
     * 调用方需先锁定并校验 submission 与子任务全部处于终态。
     */
    fun delete(id: Long): Int =
        jdbc.update("DELETE FROM public.task_submission WHERE id = :id", MapSqlParameterSource("id", id))

    /** 锁定 submission 行，返回当前状态；不存在返回 null。必须在事务内调用。 */
    fun lockById(id: Long): TaskStatus? =
        jdbc.query(
            "SELECT status FROM public.task_submission WHERE id = :id FOR UPDATE",
            MapSqlParameterSource("id", id),
        ) { rs, _ -> TaskStatus.valueOf(rs.getString(1)) }.firstOrNull()

    /** 一次查询聚合全部 TaskKey 的状态计数 */
    fun countByKeyAndStatus(): Map<TaskKey, Map<TaskStatus, Long>> {
        val rows = sql.createQuery(TaskSubmission::class) {
            groupBy(table.namespace, table.taskType, table.status)
            select(table.namespace, table.taskType, table.status, count(table.id))
        }.execute()
        val result = mutableMapOf<TaskKey, MutableMap<TaskStatus, Long>>()
        for (row in rows) {
            val key = TaskKey.ofOrNull(row._1, row._2) ?: continue
            result.getOrPut(key) { mutableMapOf() }[row._3] = row._4
        }
        return result
    }
}
