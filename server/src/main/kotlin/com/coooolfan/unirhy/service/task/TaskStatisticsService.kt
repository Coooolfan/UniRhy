package com.coooolfan.unirhy.service.task

import com.coooolfan.unirhy.error.TaskException
import com.coooolfan.unirhy.service.task.common.AsyncTaskStore
import com.coooolfan.unirhy.service.task.common.TaskKey
import com.coooolfan.unirhy.service.task.common.TaskStatus
import com.coooolfan.unirhy.service.task.common.TaskSubmissionStore
import org.springframework.stereotype.Service

/**
 * 状态计数。`active` 为查询快照中 `PENDING` / `RUNNING` 的合计：
 * 长事务内的 `RUNNING` 对统计查询不可见，不提供集群级 pending / running 拆分。
 */
data class TaskStatusCounts(
    val active: Long,
    val completed: Long,
    val failed: Long,
    val cancelled: Long,
    val total: Long,
) {
    companion object {
        val ZERO = TaskStatusCounts(0, 0, 0, 0, 0)

        fun from(counts: Map<TaskStatus, Long>): TaskStatusCounts {
            val active = (counts[TaskStatus.PENDING] ?: 0L) + (counts[TaskStatus.RUNNING] ?: 0L)
            val completed = counts[TaskStatus.COMPLETED] ?: 0L
            val failed = counts[TaskStatus.FAILED] ?: 0L
            val cancelled = counts[TaskStatus.CANCELLED] ?: 0L
            return TaskStatusCounts(
                active = active,
                completed = completed,
                failed = failed,
                cancelled = cancelled,
                total = active + completed + failed + cancelled,
            )
        }
    }
}

data class TaskStatisticsResponse(
    val namespace: String,
    val taskType: String,
    val submissions: TaskStatusCounts,
    val tasks: TaskStatusCounts,
)

@Service
class TaskStatisticsService(
    private val submissionStore: TaskSubmissionStore,
    private val taskStore: AsyncTaskStore,
    private val definitionService: TaskDefinitionService,
) {

    /**
     * 按 TaskKey 返回 submission 与 async task 的状态计数。
     *
     * @param taskKeys 紧凑序列化形式的 TaskKey 集合；缺省时返回全部当前定义
     *   或存在历史记录的 TaskKey。重复值按首次出现去重并保持请求顺序，
     *   合法但无记录的 key 返回全零统计；格式错误返回 400。
     */
    fun statistics(taskKeys: List<String>?): List<TaskStatisticsResponse> {
        val requestedKeys = taskKeys?.map { compact ->
            TaskKey.parseOrNull(compact)
                ?: throw TaskException.invalidTaskKey(reason = "invalid task key: $compact")
        }?.distinct()

        val submissionCounts = submissionStore.countByKeyAndStatus()
        val taskCounts = taskStore.countByKeyAndStatus()

        val keys = requestedKeys
            ?: (definitionService.allDefinedKeys() + submissionCounts.keys + taskCounts.keys)
                .distinct()
                .sortedWith(compareBy({ it.namespace }, { it.taskType }))

        return keys.map { key ->
            TaskStatisticsResponse(
                namespace = key.namespace,
                taskType = key.taskType,
                submissions = submissionCounts[key]?.let { TaskStatusCounts.from(it) } ?: TaskStatusCounts.ZERO,
                tasks = taskCounts[key]?.let { TaskStatusCounts.from(it) } ?: TaskStatusCounts.ZERO,
            )
        }
    }
}
