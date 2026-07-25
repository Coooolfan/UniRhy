package com.coooolfan.unirhy.service.task.common

import com.coooolfan.unirhy.model.AsyncTask
import com.coooolfan.unirhy.model.completedAt
import com.coooolfan.unirhy.model.completedReason
import com.coooolfan.unirhy.model.createdAt
import com.coooolfan.unirhy.model.id
import com.coooolfan.unirhy.model.namespace
import com.coooolfan.unirhy.model.parentId
import com.coooolfan.unirhy.model.status
import com.coooolfan.unirhy.model.taskType
import org.babyfish.jimmer.Page
import org.babyfish.jimmer.sql.fetcher.Fetcher
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.count
import org.babyfish.jimmer.sql.kt.ast.expression.desc
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.babyfish.jimmer.sql.kt.ast.expression.isNull
import org.babyfish.jimmer.sql.kt.ast.expression.valueIn
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import java.time.Instant

data class ClaimedTask(
    val id: Long,
    val parentId: Long?,
    val key: TaskKey,
    val payloadJson: String,
)

@Component
class AsyncTaskStore(
    private val sql: KSqlClient,
    private val jdbc: NamedParameterJdbcTemplate,
) {

    /** 创建一个用户投递的入口任务（无父任务）。 */
    fun enqueueRoot(key: TaskKey, payloadJson: String): Long =
        jdbc.queryForObject(
            """
            INSERT INTO public.async_task (parent_task_id, namespace, task_type, payload, status)
            VALUES (NULL, :namespace, :taskType, CAST(:payload AS jsonb), 'PENDING')
            RETURNING id
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("namespace", key.namespace)
                .addValue("taskType", key.taskType)
                .addValue("payload", payloadJson),
            Long::class.java,
        )!!

    /**
     * 批量创建后继任务。[key] 是后继自己的 TaskKey，可与父任务不同、可跨 namespace。
     * 同父同 key 的活动任务去重由 `uq_async_task_active_sibling` 提供，冲突记录被忽略。
     */
    fun enqueueChildrenIgnoringConflicts(parentId: Long, key: TaskKey, payloadJsonList: List<String>): Int {
        if (payloadJsonList.isEmpty()) return 0
        val insertSql = """
            INSERT INTO public.async_task (parent_task_id, namespace, task_type, payload, status)
            VALUES (:parentId, :namespace, :taskType, CAST(:payload AS jsonb), 'PENDING')
            ON CONFLICT DO NOTHING
        """.trimIndent()
        val batchParams = payloadJsonList.map { payloadJson ->
            MapSqlParameterSource()
                .addValue("parentId", parentId)
                .addValue("namespace", key.namespace)
                .addValue("taskType", key.taskType)
                .addValue("payload", payloadJson)
        }.toTypedArray()
        return jdbc.batchUpdate(insertSql, batchParams).sum()
    }

    /** 按 TaskKey 统计待执行任务数；入口任务与工作任务是不同的 key，无需分列。 */
    fun discoverPendingCounts(): Map<TaskKey, Long> {
        val querySql = """
            SELECT namespace, task_type, count(*)
            FROM public.async_task
            WHERE status = 'PENDING'
            GROUP BY namespace, task_type
        """.trimIndent()
        val result = mutableMapOf<TaskKey, Long>()
        jdbc.query(querySql) { rs ->
            val key = TaskKey.ofOrNull(rs.getString(1), rs.getString(2)) ?: return@query
            result[key] = rs.getLong(3)
        }
        return result
    }

    /** claim 一条当前节点可执行的任务并标记为 RUNNING；行锁保持到执行事务结束。 */
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
            SET status = 'RUNNING', started_at = now()
            WHERE t.id IN (SELECT id FROM grabbed)
            RETURNING t.id, t.parent_task_id, t.payload
        """.trimIndent()
        val params = MapSqlParameterSource()
            .addValue("namespace", key.namespace)
            .addValue("taskType", key.taskType)
        return jdbc.query(claimSql, params) { rs, _ ->
            ClaimedTask(
                id = rs.getLong(1),
                parentId = rs.getLong(2).let { if (rs.wasNull()) null else it },
                key = key,
                payloadJson = rs.getString(3),
            )
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
        parentId: Long?,
        rootsOnly: Boolean,
        namespace: String?,
        taskType: String?,
        statuses: List<TaskStatus>,
        pageIndex: Int,
        pageSize: Int,
        fetcher: Fetcher<AsyncTask>,
    ): Page<AsyncTask> =
        sql.createQuery(AsyncTask::class) {
            parentId?.let { where(table.parentId eq it) }
            if (rootsOnly) where(table.parentId.isNull())
            namespace?.let { where(table.namespace eq it) }
            taskType?.let { where(table.taskType eq it) }
            if (statuses.isNotEmpty()) where(table.status valueIn statuses)
            orderBy(table.createdAt.desc(), table.id.desc())
            select(table.fetch(fetcher))
        }.fetchPage(pageIndex, pageSize)

    fun countStatusesByParent(parentId: Long): Map<TaskStatus, Long> {
        val rows = sql.createQuery(AsyncTask::class) {
            where(table.parentId eq parentId)
            groupBy(table.status)
            select(table.status, count(table.id))
        }.execute()
        return rows.associate { it._1 to it._2 }
    }

    fun cancelPending(ids: Collection<Long>, reason: String): List<Long> {
        if (ids.isEmpty()) return emptyList()
        val sql = """
            WITH grabbed AS (
                SELECT id FROM public.async_task
                WHERE id IN (:ids) AND status = 'PENDING'
                FOR UPDATE SKIP LOCKED
            )
            UPDATE public.async_task t
            SET status = 'CANCELLED', completed_at = now(), completed_reason = :reason
            WHERE t.id IN (SELECT id FROM grabbed)
              AND t.status = 'PENDING'
            RETURNING t.id
        """.trimIndent()
        return jdbc.query(
            sql,
            MapSqlParameterSource().addValue("ids", ids).addValue("reason", reason),
        ) { rs, _ -> rs.getLong(1) }
    }

    fun requeueTerminal(ids: Collection<Long>): List<Long> {
        if (ids.isEmpty()) return emptyList()
        val sql = """
            WITH grabbed AS (
                SELECT id FROM public.async_task
                WHERE id IN (:ids) AND status IN ('FAILED', 'CANCELLED')
                FOR UPDATE SKIP LOCKED
            )
            UPDATE public.async_task t
            SET status = 'PENDING', started_at = NULL, completed_at = NULL, completed_reason = NULL
            WHERE t.id IN (SELECT id FROM grabbed)
              AND t.status IN ('FAILED', 'CANCELLED')
            RETURNING t.id
        """.trimIndent()
        return jdbc.query(sql, MapSqlParameterSource("ids", ids)) { rs, _ -> rs.getLong(1) }
    }

    fun cancelPendingByKey(key: TaskKey, reason: String): Int {
        val sql = """
            WITH grabbed AS (
                SELECT id FROM public.async_task
                WHERE namespace = :namespace
                  AND task_type = :taskType
                  AND status = 'PENDING'
                ORDER BY created_at, id
                FOR UPDATE SKIP LOCKED
            )
            UPDATE public.async_task t
            SET status = 'CANCELLED', completed_at = now(), completed_reason = :reason
            WHERE t.id IN (SELECT id FROM grabbed)
              AND t.status = 'PENDING'
        """.trimIndent()
        val params = MapSqlParameterSource()
            .addValue("namespace", key.namespace)
            .addValue("taskType", key.taskType)
            .addValue("reason", reason)
        return jdbc.update(sql, params)
    }

    fun requeueByKey(key: TaskKey, sourceStatuses: Collection<TaskStatus>): Int {
        if (sourceStatuses.isEmpty()) return 0
        val sql = """
            WITH grabbed AS (
                SELECT id FROM public.async_task
                WHERE namespace = :namespace
                  AND task_type = :taskType
                  AND status IN (:sourceStatuses)
                ORDER BY created_at, id
                FOR UPDATE SKIP LOCKED
            )
            UPDATE public.async_task t
            SET status = 'PENDING', started_at = NULL, completed_at = NULL, completed_reason = NULL
            WHERE t.id IN (SELECT id FROM grabbed)
              AND t.status IN (:sourceStatuses)
        """.trimIndent()
        val params = MapSqlParameterSource()
            .addValue("namespace", key.namespace)
            .addValue("taskType", key.taskType)
            .addValue("sourceStatuses", sourceStatuses.map { it.name })
        return jdbc.update(sql, params)
    }

    fun hasActiveByNamespace(namespace: String): Boolean =
        jdbc.queryForObject(
            """
            SELECT EXISTS (
                SELECT 1 FROM public.async_task
                WHERE namespace = :namespace AND status IN ('PENDING', 'RUNNING')
            )
            """.trimIndent(),
            MapSqlParameterSource("namespace", namespace),
            Boolean::class.java,
        ) == true

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
