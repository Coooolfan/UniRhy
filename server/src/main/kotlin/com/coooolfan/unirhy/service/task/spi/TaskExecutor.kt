package com.coooolfan.unirhy.service.task.spi

import com.coooolfan.unirhy.service.task.common.TaskKey
import tools.jackson.databind.JsonNode

/** 待入队的后继任务。[key] 可与产生它的任务不同，也可跨 namespace。 */
data class TaskSpec(
    val key: TaskKey,
    val payload: JsonNode,
)

/**
 * 任务执行 SPI。所有任务节点同构：入口任务与工作任务走同一接口，
 * 区别只在返回值 —— 返回非空序列即展开，返回空序列即叶子。
 *
 * 在任务 Worker 的事务及 savepoint 内调用；[taskId] 是当前执行节点，
 * 最终任务状态由执行引擎统一写入。返回的序列被惰性消费并分批入队。
 */
interface TaskExecutor {
    val key: TaskKey

    fun execute(taskId: Long, payload: JsonNode): Sequence<TaskSpec>
}
