package com.coooolfan.unirhy.service.task

import com.coooolfan.unirhy.error.TaskException
import com.coooolfan.unirhy.model.TaskSubmission
import com.coooolfan.unirhy.model.by
import com.coooolfan.unirhy.service.plugin.PluginStore
import com.coooolfan.unirhy.service.task.common.AsyncTaskStore
import com.coooolfan.unirhy.service.task.common.TaskFormSchema
import com.coooolfan.unirhy.service.task.common.TaskKey
import com.coooolfan.unirhy.service.task.common.TaskStatus
import com.coooolfan.unirhy.service.task.common.TaskSubmissionStore
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import org.babyfish.jimmer.Page
import org.babyfish.jimmer.sql.fetcher.Fetcher
import org.babyfish.jimmer.sql.kt.fetcher.newFetcher
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate

@Service
class TaskSubmissionService(
    private val objectMapper: ObjectMapper,
    private val submissionStore: TaskSubmissionStore,
    private val taskStore: AsyncTaskStore,
    private val pluginStore: PluginStore,
    private val definitionService: TaskDefinitionService,
    private val transactionTemplate: TransactionTemplate,
) {

    /**
     * 创建 submission：只校验任务身份、当前可用性与请求结构，
     * 写入 `PENDING` 后立即返回；规划由 Planner worker 异步执行。
     */
    fun create(namespace: String, taskType: String, params: JsonNode): Long {
        val key = TaskKey.ofOrNull(namespace, taskType)
            ?: throw TaskException.invalidTaskKey(reason = "invalid task key: $namespace:$taskType")
        if (!params.isObject) {
            throw TaskException.invalidParams(reason = "params must be a JSON object")
        }

        return transactionTemplate.execute {
            val formDefinition = resolveFormDefinitionLocked(key)
            val errors = TaskFormSchema.validateParams(formDefinition, params)
            if (errors.isNotEmpty()) {
                throw TaskException.invalidParams(reason = errors.joinToString("; "))
            }
            submissionStore.insertPending(key, params.toString())
        }!!
    }

    /**
     * 解析任务定义并对插件行持有共享锁直到 submission 插入提交，
     * 防止并发删除遗漏尚未提交的新 submission。
     */
    private fun resolveFormDefinitionLocked(key: TaskKey): JsonNode {
        if (key.namespace == TaskKey.BUILT_IN_NAMESPACE) {
            return when (key) {
                BuiltInTasks.METADATA_PARSE -> BuiltInTasks.METADATA_PARSE_FORM
                BuiltInTasks.TRANSCODE -> BuiltInTasks.TRANSCODE_FORM
                else -> throw TaskException.definitionNotFound()
            }
        }
        val plugin = pluginStore.lockForShare(key.namespace) ?: throw TaskException.definitionNotFound()
        if (plugin.taskType != key.taskType) {
            throw TaskException.definitionNotFound()
        }
        if (!plugin.enabled) {
            throw TaskException.pluginUnavailable()
        }
        return objectMapper.readTree(plugin.formDefinitionJson)
    }

    fun list(
        namespace: String?,
        taskType: String?,
        statuses: List<TaskStatus>,
        pageIndex: Int,
        pageSize: Int,
        fetcher: Fetcher<TaskSubmission>,
    ): Page<TaskSubmission> =
        submissionStore.list(namespace, taskType, statuses, pageIndex, pageSize, fetcher)

    fun get(id: Long, fetcher: Fetcher<TaskSubmission>): TaskSubmission =
        submissionStore.findById(id, fetcher) ?: throw TaskException.submissionNotFound()

    fun taskStatusCounts(id: Long): Map<TaskStatus, Long> {
        submissionStore.findById(id, SUBMISSION_ID_FETCHER) ?: throw TaskException.submissionNotFound()
        return taskStore.countStatusesBySubmission(id)
    }

    /**
     * 单项状态变更；只接受 `PENDING -> CANCELLED` 与 `FAILED -> PENDING`。
     * 目标状态已生效时不重复执行转换；被 Worker 锁定或非法迁移抛 409。
     */
    fun patchStatus(id: Long, target: TaskStatus, fetcher: Fetcher<TaskSubmission>): TaskSubmission {
        val updated = when (target) {
            TaskStatus.CANCELLED -> submissionStore.cancelPending(listOf(id), CANCELLED_BY_ADMIN_REASON)
            TaskStatus.PENDING -> requeueFailed(listOf(id))
            else -> throw TaskException.statusConflict()
        }
        val current = submissionStore.findById(id, fetcher) ?: throw TaskException.submissionNotFound()
        if (updated.isEmpty() && current.status != target) {
            throw TaskException.statusConflict()
        }
        return current
    }

    /** 批量状态变更，返回实际更新数量（不含已处于目标状态的记录） */
    fun patchStatusBatch(ids: List<Long>, target: TaskStatus): Int {
        val distinctIds = ids.distinct()
        return when (target) {
            TaskStatus.CANCELLED -> submissionStore.cancelPending(distinctIds, CANCELLED_BY_ADMIN_REASON).size
            TaskStatus.PENDING -> requeueFailed(distinctIds).size
            else -> throw TaskException.statusConflict()
        }
    }

    /** 将 FAILED submission 重新排队；TaskKey 必须当前存在且可用 */
    private fun requeueFailed(ids: List<Long>): List<Long> {
        val requeueable = ids.filter { id ->
            val submission = submissionStore.findById(id, SUBMISSION_KEY_FETCHER) ?: return@filter false
            val key = TaskKey.ofOrNull(submission.namespace, submission.taskType) ?: return@filter false
            definitionService.find(key) != null
        }
        if (requeueable.isEmpty() && ids.size == 1) {
            // 单项路径下若 submission 存在但 TaskKey 不可用，报 409 而非静默忽略
            val submission = submissionStore.findById(ids[0], SUBMISSION_KEY_FETCHER)
            if (submission != null && submission.status == TaskStatus.FAILED) {
                throw TaskException.pluginUnavailable()
            }
        }
        return submissionStore.requeueFailed(requeueable)
    }

    /**
     * 显式删除 submission 及其全部子任务；仅当 submission 与全部子任务
     * 都处于终态时允许，否则 409。
     */
    fun delete(id: Long) {
        transactionTemplate.executeWithoutResult {
            val status = submissionStore.lockById(id) ?: throw TaskException.submissionNotFound()
            if (status == TaskStatus.PENDING || status == TaskStatus.RUNNING) {
                throw TaskException.deleteConflict()
            }
            if (taskStore.hasActiveBySubmission(id)) {
                throw TaskException.deleteConflict()
            }
            submissionStore.delete(id)
        }
    }

    companion object {
        private const val CANCELLED_BY_ADMIN_REASON = "CANCELLED_BY_ADMIN"

        private val SUBMISSION_ID_FETCHER = newFetcher(TaskSubmission::class).by {}

        private val SUBMISSION_KEY_FETCHER = newFetcher(TaskSubmission::class).by {
            namespace()
            taskType()
            status()
        }
    }
}
