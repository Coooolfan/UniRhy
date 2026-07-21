package com.coooolfan.unirhy.service.task

import com.coooolfan.unirhy.error.TaskException
import com.coooolfan.unirhy.model.AsyncTask
import com.coooolfan.unirhy.model.by
import com.coooolfan.unirhy.service.task.common.AsyncTaskStore
import com.coooolfan.unirhy.service.task.common.TaskKey
import com.coooolfan.unirhy.service.task.common.TaskStatus
import org.babyfish.jimmer.Page
import org.babyfish.jimmer.sql.fetcher.Fetcher
import org.babyfish.jimmer.sql.kt.fetcher.newFetcher
import org.springframework.stereotype.Service

@Service
class AsyncTaskService(
    private val taskStore: AsyncTaskStore,
    private val definitionService: TaskDefinitionService,
) {

    fun list(
        submissionId: Long?,
        namespace: String?,
        taskType: String?,
        statuses: List<TaskStatus>,
        pageIndex: Int,
        pageSize: Int,
        fetcher: Fetcher<AsyncTask>,
    ): Page<AsyncTask> =
        taskStore.list(submissionId, namespace, taskType, statuses, pageIndex, pageSize, fetcher)

    fun get(id: Long, fetcher: Fetcher<AsyncTask>): AsyncTask =
        taskStore.findById(id, fetcher) ?: throw TaskException.taskNotFound()

    /**
     * 单项状态变更；只接受 `PENDING -> CANCELLED` 与 `FAILED -> PENDING`。
     * 目标状态已生效时不重复执行转换；被 Worker 锁定或非法迁移抛 409。
     */
    fun patchStatus(id: Long, target: TaskStatus, fetcher: Fetcher<AsyncTask>): AsyncTask {
        val updated = when (target) {
            TaskStatus.CANCELLED -> taskStore.cancelPending(listOf(id), CANCELLED_BY_ADMIN_REASON)
            TaskStatus.PENDING -> requeueFailed(listOf(id))
            else -> throw TaskException.statusConflict()
        }
        val current = taskStore.findById(id, fetcher) ?: throw TaskException.taskNotFound()
        if (updated.isEmpty() && current.status != target) {
            throw TaskException.statusConflict()
        }
        return current
    }

    /** 批量状态变更，返回实际更新数量（不含已处于目标状态的记录） */
    fun patchStatusBatch(ids: List<Long>, target: TaskStatus): Int {
        val distinctIds = ids.distinct()
        return when (target) {
            TaskStatus.CANCELLED -> taskStore.cancelPending(distinctIds, CANCELLED_BY_ADMIN_REASON).size
            TaskStatus.PENDING -> requeueFailed(distinctIds).size
            else -> throw TaskException.statusConflict()
        }
    }

    /** 将 FAILED 任务重新排队；TaskKey 必须当前存在且可用，插件不存在时不允许重置 */
    private fun requeueFailed(ids: List<Long>): List<Long> {
        val requeueable = ids.filter { id ->
            val task = taskStore.findById(id, TASK_KEY_FETCHER) ?: return@filter false
            val key = TaskKey.ofOrNull(task.namespace, task.taskType) ?: return@filter false
            definitionService.find(key) != null
        }
        if (requeueable.isEmpty() && ids.size == 1) {
            val task = taskStore.findById(ids[0], TASK_KEY_FETCHER)
            if (task != null && task.status == TaskStatus.FAILED) {
                throw TaskException.pluginUnavailable()
            }
        }
        return taskStore.requeueFailed(requeueable)
    }

    companion object {
        private const val CANCELLED_BY_ADMIN_REASON = "CANCELLED_BY_ADMIN"

        private val TASK_KEY_FETCHER = newFetcher(AsyncTask::class).by {
            namespace()
            taskType()
            status()
        }
    }
}
