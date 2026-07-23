package com.coooolfan.unirhy.service.task.common

import com.coooolfan.unirhy.model.AsyncTask
import com.coooolfan.unirhy.model.action
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
    val action: TaskAction,
    val payloadJson: String,
)

@Component
class AsyncTaskStore(
    private val sql: KSqlClient,
    private val jdbc: NamedParameterJdbcTemplate,
) {

    /** 创建一个无父节点的入口规划任务。 */
    fun enqueueRoot(key: TaskKey, payloadJson: String): Long =
        jdbc.queryForObject(
            """
            INSERT INTO public.async_task (parent_task_id, namespace, task_type, action, payload, status)
            VALUES (NULL, :namespace, :taskType, 'PLAN', CAST(:payload AS jsonb), 'PENDING')
            RETURNING id
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("namespace", key.namespace)
                .addValue("taskType", key.taskType)
                .addValue("payload", payloadJson),
            Long::class.java,
        )!!

    /**
     * 批量创建父任务的 RUN 子任务。活动子任务去重由
     * `uq_async_task_active_child` 提供，冲突记录被忽略。
     */
    fun enqueueChildrenIgnoringConflicts(parentId: Long, key: TaskKey, payloadJsonList: List<String>): Int {
        if (payloadJsonList.isEmpty()) return 0
        val insertSql = """
            INSERT INTO public.async_task (parent_task_id, namespace, task_type, action, payload, status)
            VALUES (:parentId, :namespace, :taskType, 'RUN', CAST(:payload AS jsonb), 'PENDING')
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

    fun discoverPendingCounts(): Map<TaskKey, Map<TaskAction, Long>> {
        val querySql = """
            SELECT namespace, task_type, action, count(*)
            FROM public.async_task
            WHERE status = 'PENDING'
            GROUP BY namespace, task_type, action
        """.trimIndent()
        val result = mutableMapOf<TaskKey, MutableMap<TaskAction, Long>>()
        jdbc.query(querySql) { rs ->
            val key = TaskKey.ofOrNull(rs.getString(1), rs.getString(2)) ?: return@query
            result.getOrPut(key) { mutableMapOf() }[TaskAction.valueOf(rs.getString(3))] = rs.getLong(4)
        }
        return result
    }

    /** claim 一条指定 action 的任务并标记为 RUNNING；行锁保持到执行事务结束。 */
    fun claimOne(key: TaskKey, actions: Collection<TaskAction>): ClaimedTask? {
        if (actions.isEmpty()) return null
        val claimSql = """
            WITH grabbed AS (
                SELECT id
                FROM public.async_task
                WHERE namespace = :namespace
                  AND task_type = :taskType
                  AND action IN (:actions)
                  AND status = 'PENDING'
                ORDER BY created_at, id
                LIMIT 1
                FOR UPDATE SKIP LOCKED
            )
            UPDATE public.async_task t
            SET status = 'RUNNING', started_at = now()
            WHERE t.id IN (SELECT id FROM grabbed)
            RETURNING t.id, t.parent_task_id, t.action, t.payload
        """.trimIndent()
        val params = MapSqlParameterSource()
            .addValue("namespace", key.namespace)
            .addValue("taskType", key.taskType)
            .addValue("actions", actions.map { it.name })
        return jdbc.query(claimSql, params) { rs, _ ->
            ClaimedTask(
                id = rs.getLong(1),
                parentId = rs.getLong(2).let { if (rs.wasNull()) null else it },
                key = key,
                action = TaskAction.valueOf(rs.getString(3)),
                payloadJson = rs.getString(4),
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
        actions: List<TaskAction>,
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
            if (actions.isNotEmpty()) where(table.action valueIn actions)
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
            RETURNING t.id
        """.trimIndent()
        return jdbc.query(
            sql,
            MapSqlParameterSource().addValue("ids", ids).addValue("reason", reason),
        ) { rs, _ -> rs.getLong(1) }
    }

    fun requeueFailed(ids: Collection<Long>): List<Long> {
        if (ids.isEmpty()) return emptyList()
        val sql = """
            WITH grabbed AS (
                SELECT id FROM public.async_task
                WHERE id IN (:ids) AND status = 'FAILED'
                FOR UPDATE SKIP LOCKED
            )
            UPDATE public.async_task t
            SET status = 'PENDING', started_at = NULL, completed_at = NULL, completed_reason = NULL
            WHERE t.id IN (SELECT id FROM grabbed)
            RETURNING t.id
        """.trimIndent()
        return jdbc.query(sql, MapSqlParameterSource("ids", ids)) { rs, _ -> rs.getLong(1) }
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
