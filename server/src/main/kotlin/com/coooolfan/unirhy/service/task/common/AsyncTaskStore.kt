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
        val rows = sql.createQuery(AsyncTask::class) {
            where(table.status eq TaskStatus.PENDING)
            groupBy(table.namespace, table.taskType)
            select(table.namespace, table.taskType, count(table.id))
        }.execute()
        val result = mutableMapOf<TaskKey, Long>()
        for (row in rows) {
            val key = TaskKey.ofOrNull(row._1, row._2) ?: continue
            result[key] = row._3
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
        return jdbc.query(
            transitionSql(SELECTOR_BY_IDS, "status = 'PENDING'", SET_CANCELLED, returningId = true),
            MapSqlParameterSource().addValue("ids", ids).addValue("reason", reason),
        ) { rs, _ -> rs.getLong(1) }
    }

    fun requeueTerminal(ids: Collection<Long>): List<Long> {
        if (ids.isEmpty()) return emptyList()
        return jdbc.query(
            transitionSql(SELECTOR_BY_IDS, "status IN ('FAILED', 'CANCELLED')", SET_PENDING, returningId = true),
            MapSqlParameterSource("ids", ids),
        ) { rs, _ -> rs.getLong(1) }
    }

    fun cancelPendingByKey(key: TaskKey, reason: String): Int = jdbc.update(
        transitionSql(SELECTOR_BY_KEY, "status = 'PENDING'", SET_CANCELLED),
        MapSqlParameterSource()
            .addValue("namespace", key.namespace)
            .addValue("taskType", key.taskType)
            .addValue("reason", reason),
    )

    fun requeueByKey(key: TaskKey, sourceStatuses: Collection<TaskStatus>): Int {
        if (sourceStatuses.isEmpty()) return 0
        return jdbc.update(
            transitionSql(SELECTOR_BY_KEY, "status IN (:sourceStatuses)", SET_PENDING),
            MapSqlParameterSource()
                .addValue("namespace", key.namespace)
                .addValue("taskType", key.taskType)
                .addValue("sourceStatuses", sourceStatuses.map { it.name }),
        )
    }

    fun hasActiveByNamespace(namespace: String): Boolean =
        sql.createQuery(AsyncTask::class) {
            where(table.namespace eq namespace, table.status valueIn ACTIVE_STATUSES)
            select(table.id)
        }.exists()

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

/** 未进入终态的任务状态 */
private val ACTIVE_STATUSES = listOf(TaskStatus.PENDING, TaskStatus.RUNNING)

/** 按 id 集合选取待迁移的任务 */
private const val SELECTOR_BY_IDS = "id IN (:ids)"

/** 按 TaskKey 选取待迁移的任务，按创建顺序抓取 */
private const val SELECTOR_BY_KEY = "namespace = :namespace AND task_type = :taskType"

private const val SET_CANCELLED = "status = 'CANCELLED', completed_at = now(), completed_reason = :reason"

private const val SET_PENDING =
    "status = 'PENDING', started_at = NULL, completed_at = NULL, completed_reason = NULL"

/**
 * 批量状态迁移的统一模板：先用 `FOR UPDATE SKIP LOCKED` 抓取候选行，
 * 避开被 Worker 锁定的任务，再对其中仍处于 [statusFilter] 的行执行 [setClause]。
 */
private fun transitionSql(
    selector: String,
    statusFilter: String,
    setClause: String,
    returningId: Boolean = false,
): String = """
    WITH grabbed AS (
        SELECT id FROM public.async_task
        WHERE $selector AND $statusFilter
        ORDER BY created_at, id
        FOR UPDATE SKIP LOCKED
    )
    UPDATE public.async_task t
    SET $setClause
    WHERE t.id IN (SELECT id FROM grabbed)
      AND t.$statusFilter
    ${if (returningId) "RETURNING t.id" else ""}
""".trimIndent()
