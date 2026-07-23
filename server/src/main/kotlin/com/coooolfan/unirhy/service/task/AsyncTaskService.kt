package com.coooolfan.unirhy.service.task

import com.coooolfan.unirhy.error.TaskException
import com.coooolfan.unirhy.model.AsyncTask
import com.coooolfan.unirhy.model.by
import com.coooolfan.unirhy.service.plugin.PluginStore
import com.coooolfan.unirhy.service.task.common.AsyncTaskStore
import com.coooolfan.unirhy.service.task.common.TaskAction
import com.coooolfan.unirhy.service.task.common.TaskFormSchema
import com.coooolfan.unirhy.service.task.common.TaskKey
import com.coooolfan.unirhy.service.task.common.TaskStatus
import org.babyfish.jimmer.Page
import org.babyfish.jimmer.sql.fetcher.Fetcher
import org.babyfish.jimmer.sql.kt.fetcher.newFetcher
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

/** 统一任务的创建、查询和状态管理。 */
@Service
class AsyncTaskService(
    private val objectMapper: ObjectMapper,
    private val taskStore: AsyncTaskStore,
    private val pluginStore: PluginStore,
    private val definitionService: TaskDefinitionService,
    private val transactionTemplate: TransactionTemplate,
) {

    /** 校验公开任务表单并创建一个 PLAN 根任务。 */
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
        if (plugin.taskType != key.taskType) throw TaskException.definitionNotFound()
        if (!plugin.enabled) throw TaskException.pluginUnavailable()
        return objectMapper.readTree(plugin.formDefinitionJson)
    }

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
    ): Page<AsyncTask> = taskStore.list(
        parentId, rootsOnly, namespace, taskType, actions, statuses, pageIndex, pageSize, fetcher,
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
            TaskStatus.PENDING -> requeueFailed(listOf(id))
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
            TaskStatus.PENDING -> requeueFailed(distinctIds).size
            else -> throw TaskException.statusConflict()
        }
    }

    private fun requeueFailed(ids: List<Long>): List<Long> {
        val requeueable = ids.filter { id ->
            val task = taskStore.findById(id, TASK_KEY_FETCHER) ?: return@filter false
            val key = TaskKey.ofOrNull(task.namespace, task.taskType) ?: return@filter false
            definitionService.find(key) != null
        }
        if (requeueable.isEmpty() && ids.size == 1) {
            val task = taskStore.findById(ids[0], TASK_KEY_FETCHER)
            if (task != null && task.status == TaskStatus.FAILED) throw TaskException.pluginUnavailable()
        }
        return taskStore.requeueFailed(requeueable)
    }

    companion object {
        private const val CANCELLED_BY_ADMIN_REASON = "CANCELLED_BY_ADMIN"
        private val TASK_ID_FETCHER = newFetcher(AsyncTask::class).by {}
        private val TASK_KEY_FETCHER = newFetcher(AsyncTask::class).by {
            namespace()
            taskType()
            status()
        }
    }
}
