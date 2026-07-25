package com.coooolfan.unirhy.service.task.dispatch

import com.coooolfan.unirhy.service.plugin.PluginStore
import com.coooolfan.unirhy.service.task.common.AsyncTaskStore
import com.coooolfan.unirhy.service.task.common.TaskKey
import com.coooolfan.unirhy.service.task.common.TaskStatus
import com.coooolfan.unirhy.service.task.common.failureReason
import com.coooolfan.unirhy.service.task.spi.TaskExecutorRegistry
import com.coooolfan.unirhy.service.task.spi.TaskSpec
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.ObjectMapper

/**
 * 统一任务执行引擎。所有任务走同一条路径：交由 `(namespace, taskType)` 对应的
 * Executor 执行，其返回的后继在同一执行事务中入队；返回空序列即叶子。
 */
@Component
class TaskExecutionEngine(
    transactionManager: PlatformTransactionManager,
    private val taskStore: AsyncTaskStore,
    private val executorRegistry: TaskExecutorRegistry,
    private val pluginStore: PluginStore,
    private val objectMapper: ObjectMapper,
) {
    private val logger = LoggerFactory.getLogger(TaskExecutionEngine::class.java)
    private val transactionTemplate = TransactionTemplate(transactionManager)

    fun executeOne(key: TaskKey) {
        transactionTemplate.executeWithoutResult { status ->
            val claimed = taskStore.claimOne(key) ?: return@executeWithoutResult
            if (!isKeyClaimable(key)) {
                status.setRollbackOnly()
                return@executeWithoutResult
            }
            val executor = executorRegistry.find(key)
            if (executor == null) {
                status.setRollbackOnly()
                return@executeWithoutResult
            }

            val startedAt = System.currentTimeMillis()
            val savepoint = status.createSavepoint()
            try {
                val payload = objectMapper.readTree(claimed.payloadJson)
                val enqueued = enqueueSuccessors(claimed.id, executor.execute(claimed.id, payload))
                status.releaseSavepoint(savepoint)
                taskStore.complete(claimed.id, TaskStatus.COMPLETED, "SUCCESS")
                logger.info(
                    "Task completed: taskId={}, taskKey={}, parentTaskId={}, enqueued={}, durationMs={}",
                    claimed.id, key, claimed.parentId, enqueued, System.currentTimeMillis() - startedAt,
                )
            } catch (ex: Exception) {
                status.rollbackToSavepoint(savepoint)
                val reason = failureReason(ex)
                taskStore.complete(claimed.id, TaskStatus.FAILED, reason)
                logger.error(
                    "Task failed: taskId={}, taskKey={}, parentTaskId={}, durationMs={}, reason={}",
                    claimed.id, key, claimed.parentId, System.currentTimeMillis() - startedAt, reason, ex,
                )
            }
        }
    }

    /**
     * 惰性消费后继序列并按目标 TaskKey 分批入队。后继可跨 key、跨 namespace，
     * 因此按 key 分桶累积，任一桶满即刷写。
     */
    private fun enqueueSuccessors(parentId: Long, specs: Sequence<TaskSpec>): Int {
        var enqueued = 0
        val batches = LinkedHashMap<TaskKey, ArrayList<String>>()
        for (spec in specs) {
            val batch = batches.getOrPut(spec.key) { ArrayList(ENQUEUE_BATCH_SIZE) }
            batch += spec.payload.toString()
            if (batch.size >= ENQUEUE_BATCH_SIZE) {
                enqueued += taskStore.enqueueChildrenIgnoringConflicts(parentId, spec.key, batch)
                batch.clear()
            }
        }
        for ((specKey, batch) in batches) {
            enqueued += taskStore.enqueueChildrenIgnoringConflicts(parentId, specKey, batch)
        }
        return enqueued
    }

    private fun isKeyClaimable(key: TaskKey): Boolean =
        key.namespace == TaskKey.BUILT_IN_NAMESPACE || pluginStore.isEnabled(key.namespace)

    private companion object {
        private const val ENQUEUE_BATCH_SIZE = 512
    }
}
