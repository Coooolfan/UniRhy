package com.coooolfan.unirhy.service.task

import com.coooolfan.unirhy.error.TaskException
import com.coooolfan.unirhy.model.AsyncTask
import com.coooolfan.unirhy.model.by
import com.coooolfan.unirhy.service.plugin.PluginStore
import com.coooolfan.unirhy.service.plugin.PluginTaskStore
import com.coooolfan.unirhy.service.task.common.AsyncTaskStore
import com.coooolfan.unirhy.service.task.common.TaskFormSchema
import com.coooolfan.unirhy.service.task.common.TaskKey
import com.coooolfan.unirhy.service.task.common.TaskStatus
import org.babyfish.jimmer.Page
import org.babyfish.jimmer.sql.fetcher.Fetcher
import org.babyfish.jimmer.sql.kt.fetcher.newFetcher
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.JsonNode

/** 统一任务的创建、查询和状态管理。 */
@Service
class AsyncTaskService(
    private val taskStore: AsyncTaskStore,
    private val pluginStore: PluginStore,
    private val pluginTaskStore: PluginTaskStore,
    private val definitionService: TaskDefinitionService,
    private val transactionTemplate: TransactionTemplate,
) {

    /**
     * 校验入口任务表单并创建一个入口任务。
     * 只有 `userSubmittable` 的任务可被投递；工作任务只能由上游 Executor 产出。
     */
    fun create(namespace: String, taskType: String, payload: JsonNode): Long {
        val key = TaskKey.ofOrNull(namespace, taskType)
            ?: throw TaskException.invalidTaskKey(reason = "invalid task key: $namespace:$taskType")
        if (!payload.isObject) throw TaskException.invalidParams(reason = "payload must be a JSON object")
        return transactionTemplate.execute {
            val definition = resolveFormDefinitionLocked(key)
            val errors = TaskFormSchema.validateParams(definition, payload)
            if (errors.isNotEmpty()) throw TaskException.invalidParams(reason = errors.joinToString("; "))
            taskStore.enqueueRoot(key, payload.toString())
        }
    }

    private fun resolveFormDefinitionLocked(key: TaskKey): JsonNode {
        if (key.namespace == TaskKey.BUILT_IN_NAMESPACE) {
            return when (key) {
                BuiltInTasks.METADATA_PARSE -> BuiltInTasks.METADATA_PARSE_FORM
                BuiltInTasks.TRANSCODE -> BuiltInTasks.TRANSCODE_FORM
                else -> throw TaskException.definitionNotFound()
            }
        }
        val plugin = pluginStore.lockForShare(key.namespace) ?: throw TaskException.definitionNotFound()
        val task = pluginTaskStore.find(key) ?: throw TaskException.definitionNotFound()
        if (!task.userSubmittable) throw TaskException.definitionNotFound()
        if (!plugin.enabled) throw TaskException.pluginUnavailable()
        return task.formDefinition
    }

    fun list(
        parentId: Long?,
        rootsOnly: Boolean,
        namespace: String?,
        taskType: String?,
        statuses: List<TaskStatus>,
        pageIndex: Int,
        pageSize: Int,
        fetcher: Fetcher<AsyncTask>,
    ): Page<AsyncTask> = taskStore.list(
        parentId, rootsOnly, namespace, taskType, statuses, pageIndex, pageSize, fetcher,
    )

    fun get(id: Long, fetcher: Fetcher<AsyncTask>): AsyncTask =
        taskStore.findById(id, fetcher) ?: throw TaskException.taskNotFound()

    fun childStatusCounts(id: Long): Map<TaskStatus, Long> {
        taskStore.findById(id, TASK_ID_FETCHER) ?: throw TaskException.taskNotFound()
        return taskStore.countStatusesByParent(id)
    }

    fun patchStatus(id: Long, target: TaskStatus, fetcher: Fetcher<AsyncTask>): AsyncTask {
        val updated = when (target) {
            TaskStatus.CANCELLED -> taskStore.cancelPending(listOf(id), CANCELLED_BY_ADMIN_REASON)
            TaskStatus.PENDING -> requeueTerminal(listOf(id))
            else -> throw TaskException.statusConflict()
        }
        val current = taskStore.findById(id, fetcher) ?: throw TaskException.taskNotFound()
        if (updated.isEmpty() && current.status != target) throw TaskException.statusConflict()
        return current
    }

    fun patchStatusBatch(ids: List<Long>, target: TaskStatus): Int {
        val distinctIds = ids.distinct()
        return when (target) {
            TaskStatus.CANCELLED -> taskStore.cancelPending(distinctIds, CANCELLED_BY_ADMIN_REASON).size
            TaskStatus.PENDING -> requeueTerminal(distinctIds).size
            else -> throw TaskException.statusConflict()
        }
    }

    fun transitionStatuses(
        namespace: String,
        taskType: String,
        sourceStatuses: Set<TaskStatus>,
        targetStatus: TaskStatus,
    ): Int {
        val key = TaskKey.ofOrNull(namespace, taskType)
            ?: throw TaskException.invalidTaskKey(reason = "invalid task key: $namespace:$taskType")
        return when {
            targetStatus == TaskStatus.PENDING &&
                sourceStatuses.isNotEmpty() &&
                sourceStatuses.all { it in REQUEUEABLE_STATUSES } -> {
                if (definitionService.find(key) == null) throw TaskException.pluginUnavailable()
                taskStore.requeueByKey(key, sourceStatuses)
            }
            targetStatus == TaskStatus.CANCELLED && sourceStatuses == setOf(TaskStatus.PENDING) ->
                taskStore.cancelPendingByKey(key, CANCELLED_BY_ADMIN_REASON)
            else -> throw TaskException.statusConflict()
        }
    }

    private fun requeueTerminal(ids: List<Long>): List<Long> {
        val requeueable = ids.filter { id ->
            val task = taskStore.findById(id, TASK_KEY_FETCHER) ?: return@filter false
            val key = TaskKey.ofOrNull(task.namespace, task.taskType) ?: return@filter false
            definitionService.find(key) != null
        }
        if (requeueable.isEmpty() && ids.size == 1) {
            val task = taskStore.findById(ids[0], TASK_KEY_FETCHER)
            if (task != null && task.status in REQUEUEABLE_STATUSES) throw TaskException.pluginUnavailable()
        }
        return taskStore.requeueTerminal(requeueable)
    }

    companion object {
        private const val CANCELLED_BY_ADMIN_REASON = "CANCELLED_BY_ADMIN"
        private val REQUEUEABLE_STATUSES = setOf(TaskStatus.FAILED, TaskStatus.CANCELLED)
        private val TASK_ID_FETCHER = newFetcher(AsyncTask::class).by {}
        private val TASK_KEY_FETCHER = newFetcher(AsyncTask::class).by {
            namespace()
            taskType()
            status()
        }
    }
}
