package com.coooolfan.unirhy.service.task.common

import com.coooolfan.unirhy.model.AsyncTask
import com.coooolfan.unirhy.model.completedAt
import com.coooolfan.unirhy.model.completedReason
import com.coooolfan.unirhy.model.createdAt
import com.coooolfan.unirhy.model.id
import com.coooolfan.unirhy.model.namespace
import com.coooolfan.unirhy.model.status
import com.coooolfan.unirhy.model.submissionId
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

data class ClaimedTask(
    val id: Long,
    val key: TaskKey,
    val payloadJson: String,
)

@Component
class AsyncTaskStore(
    private val sql: KSqlClient,
    private val jdbc: NamedParameterJdbcTemplate,
) {

    /**
     * 批量投递任务；活动 payload 去重由唯一表达式索引
     * `uq_async_task_active_payload` 提供，冲突记录被忽略。返回实际插入数量。
     */
    fun enqueueIgnoringConflicts(submissionId: Long, key: TaskKey, payloadJsonList: List<String>): Int {
        if (payloadJsonList.isEmpty()) {
            return 0
        }
        val insertSql = """
            INSERT INTO public.async_task (submission_id, namespace, task_type, payload, status)
            VALUES (:submissionId, :namespace, :taskType, CAST(:payload AS jsonb), 'PENDING')
            ON CONFLICT DO NOTHING
        """.trimIndent()
        val batchParams = payloadJsonList.map { payloadJson ->
            MapSqlParameterSource()
                .addValue("submissionId", submissionId)
                .addValue("namespace", key.namespace)
                .addValue("taskType", key.taskType)
                .addValue("payload", payloadJson)
        }.toTypedArray()
        return jdbc.batchUpdate(insertSql, batchParams).sum()
    }

    /** 统计当前快照中可见的 PENDING 任务数量，按 TaskKey 分组 */
    fun discoverPendingCounts(): Map<TaskKey, Long> {
        val querySql = """
            SELECT namespace, task_type, count(*)
            FROM public.async_task
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
     * claim 一条指定 TaskKey 的 PENDING 任务并标记为 RUNNING。
     * 必须在事务内调用；行锁与连接保持到 Handler 执行结束。
     */
    fun claimOne(key: TaskKey): ClaimedTask? {
        val claimSql = """
            WITH grabbed AS (
                SELECT id
                FROM public.async_task
                WHERE namespace = :namespace
                  AND task_type = :taskType
                  AND status = 'PENDING'
                ORDER BY created_at, id
                LIMIT 1
                FOR UPDATE SKIP LOCKED
            )
            UPDATE public.async_task t
            SET status = 'RUNNING',
                started_at = now()
            WHERE t.id IN (SELECT id FROM grabbed)
            RETURNING t.id, t.payload
        """.trimIndent()
        val params = MapSqlParameterSource()
            .addValue("namespace", key.namespace)
            .addValue("taskType", key.taskType)
        return jdbc.query(claimSql, params) { rs, _ ->
            ClaimedTask(id = rs.getLong(1), key = key, payloadJson = rs.getString(2))
        }.firstOrNull()
    }

    fun complete(id: Long, status: TaskStatus, reason: String?) {
        sql.createUpdate(AsyncTask::class) {
            set(table.status, status)
            set(table.completedAt, Instant.now())
            set(table.completedReason, reason)
            where(table.id eq id)
        }.execute()
    }

    fun findById(id: Long, fetcher: Fetcher<AsyncTask>): AsyncTask? =
        sql.createQuery(AsyncTask::class) {
            where(table.id eq id)
            select(table.fetch(fetcher))
        }.execute().firstOrNull()

    fun list(
        submissionId: Long?,
        namespace: String?,
        taskType: String?,
        statuses: List<TaskStatus>,
        pageIndex: Int,
        pageSize: Int,
        fetcher: Fetcher<AsyncTask>,
    ): Page<AsyncTask> =
        sql.createQuery(AsyncTask::class) {
            submissionId?.let { where(table.submissionId eq it) }
            namespace?.let { where(table.namespace eq it) }
            taskType?.let { where(table.taskType eq it) }
            if (statuses.isNotEmpty()) {
                where(table.status valueIn statuses)
            }
            orderBy(table.createdAt.desc(), table.id.desc())
            select(table.fetch(fetcher))
        }.fetchPage(pageIndex, pageSize)

    /** submission 详情中的子任务状态计数 */
    fun countStatusesBySubmission(submissionId: Long): Map<TaskStatus, Long> {
        val rows = sql.createQuery(AsyncTask::class) {
            where(table.submissionId eq submissionId)
            groupBy(table.status)
            select(table.status, count(table.id))
        }.execute()
        return rows.associate { it._1 to it._2 }
    }

    /** 将尚未被 Handler 锁定的 PENDING 任务取消。返回实际更新的 id 集合。 */
    fun cancelPending(ids: Collection<Long>, reason: String): List<Long> {
        if (ids.isEmpty()) return emptyList()
        val cancelSql = """
            WITH grabbed AS (
                SELECT id
                FROM public.async_task
                WHERE id IN (:ids)
                  AND status = 'PENDING'
                FOR UPDATE SKIP LOCKED
            )
            UPDATE public.async_task t
            SET status = 'CANCELLED',
                completed_at = now(),
                completed_reason = :reason
            WHERE t.id IN (SELECT id FROM grabbed)
            RETURNING t.id
        """.trimIndent()
        val params = MapSqlParameterSource()
            .addValue("ids", ids)
            .addValue("reason", reason)
        return jdbc.query(cancelSql, params) { rs, _ -> rs.getLong(1) }
    }

    /** 将 FAILED 任务重置为 PENDING，清空执行时间与失败原因。返回实际更新的 id 集合。 */
    fun requeueFailed(ids: Collection<Long>): List<Long> {
        if (ids.isEmpty()) return emptyList()
        val requeueSql = """
            WITH grabbed AS (
                SELECT id
                FROM public.async_task
                WHERE id IN (:ids)
                  AND status = 'FAILED'
                FOR UPDATE SKIP LOCKED
            )
            UPDATE public.async_task t
            SET status = 'PENDING',
                started_at = NULL,
                completed_at = NULL,
                completed_reason = NULL
            WHERE t.id IN (SELECT id FROM grabbed)
            RETURNING t.id
        """.trimIndent()
        return jdbc.query(requeueSql, MapSqlParameterSource("ids", ids)) { rs, _ -> rs.getLong(1) }
    }

    /** 指定 namespace 下是否存在活动（PENDING / RUNNING）submission 或任务 */
    fun hasActiveByNamespace(namespace: String): Boolean {
        val querySql = """
            SELECT EXISTS (
                SELECT 1 FROM public.task_submission
                WHERE namespace = :namespace AND status IN ('PENDING', 'RUNNING')
            ) OR EXISTS (
                SELECT 1 FROM public.async_task
                WHERE namespace = :namespace AND status IN ('PENDING', 'RUNNING')
            )
        """.trimIndent()
        return jdbc.queryForObject(querySql, MapSqlParameterSource("namespace", namespace), Boolean::class.java) == true
    }

    /** submission 的子任务是否全部处于终态 */
    fun hasActiveBySubmission(submissionId: Long): Boolean {
        val querySql = """
            SELECT EXISTS (
                SELECT 1 FROM public.async_task
                WHERE submission_id = :submissionId AND status IN ('PENDING', 'RUNNING')
            )
        """.trimIndent()
        return jdbc.queryForObject(querySql, MapSqlParameterSource("submissionId", submissionId), Boolean::class.java) == true
    }

    /** 一次查询聚合全部 TaskKey 的状态计数 */
    fun countByKeyAndStatus(): Map<TaskKey, Map<TaskStatus, Long>> {
        val rows = sql.createQuery(AsyncTask::class) {
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
