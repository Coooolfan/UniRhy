package com.coooolfan.unirhy.service.task.dispatch

import com.coooolfan.unirhy.service.plugin.PluginStore
import com.coooolfan.unirhy.service.task.common.AsyncTaskStore
import com.coooolfan.unirhy.service.task.common.TaskAction
import com.coooolfan.unirhy.service.task.common.TaskKey
import com.coooolfan.unirhy.service.task.common.TaskStatus
import com.coooolfan.unirhy.service.task.common.failureReason
import com.coooolfan.unirhy.service.task.spi.AsyncTaskHandlerRegistry
import com.coooolfan.unirhy.service.task.spi.TaskPlannerRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.ObjectMapper

/**
 * 统一任务执行引擎。PLAN 与 RUN 都从同一任务表 claim，并在同一执行事务中
 * 创建子任务和完成当前节点。
 */
@Component
class TaskExecutionEngine(
    transactionManager: PlatformTransactionManager,
    private val taskStore: AsyncTaskStore,
    private val plannerRegistry: TaskPlannerRegistry,
    private val handlerRegistry: AsyncTaskHandlerRegistry,
    private val pluginStore: PluginStore,
    private val objectMapper: ObjectMapper,
) {
    private val logger = LoggerFactory.getLogger(TaskExecutionEngine::class.java)
    private val transactionTemplate = TransactionTemplate(transactionManager)

    fun executeOne(key: TaskKey, actions: Collection<TaskAction>) {
        transactionTemplate.executeWithoutResult { status ->
            val claimed = taskStore.claimOne(key, actions) ?: return@executeWithoutResult
            if (!isKeyClaimable(key)) {
                status.setRollbackOnly()
                return@executeWithoutResult
            }

            val startedAt = System.currentTimeMillis()
            val savepoint = status.createSavepoint()
            try {
                val payload = objectMapper.readTree(claimed.payloadJson)
                val enqueued = when (claimed.action) {
                    TaskAction.PLAN -> {
                        val planner = plannerRegistry.find(key)
                        if (planner == null) {
                            status.setRollbackOnly()
                            return@executeWithoutResult
                        }
                        enqueuePlannedChildren(claimed.id, key, planner.plan(payload))
                    }
                    TaskAction.RUN -> {
                        val handler = handlerRegistry.find(key)
                        if (handler == null) {
                            status.setRollbackOnly()
                            return@executeWithoutResult
                        }
                        handler.run(claimed.id, payload)
                        0
                    }
                }
                status.releaseSavepoint(savepoint)
                taskStore.complete(claimed.id, TaskStatus.COMPLETED, "SUCCESS")
                logger.info(
                    "Task completed: taskId={}, taskKey={}, action={}, enqueued={}, durationMs={}",
                    claimed.id, key, claimed.action, enqueued, System.currentTimeMillis() - startedAt,
                )
            } catch (ex: Exception) {
                status.rollbackToSavepoint(savepoint)
                val reason = failureReason(ex)
                taskStore.complete(claimed.id, TaskStatus.FAILED, reason)
                logger.error(
                    "Task failed: taskId={}, taskKey={}, action={}, durationMs={}, reason={}",
                    claimed.id, key, claimed.action, System.currentTimeMillis() - startedAt, reason, ex,
                )
            }
        }
    }

    private fun enqueuePlannedChildren(parentId: Long, key: TaskKey, payloads: Sequence<tools.jackson.databind.JsonNode>): Int {
        var enqueued = 0
        val batch = ArrayList<String>(ENQUEUE_BATCH_SIZE)
        for (payload in payloads) {
            batch += payload.toString()
            if (batch.size >= ENQUEUE_BATCH_SIZE) {
                enqueued += taskStore.enqueueChildrenIgnoringConflicts(parentId, key, batch)
                batch.clear()
            }
        }
        return enqueued + taskStore.enqueueChildrenIgnoringConflicts(parentId, key, batch)
    }

    private fun isKeyClaimable(key: TaskKey): Boolean =
        key.namespace == TaskKey.BUILT_IN_NAMESPACE || pluginStore.isEnabled(key.namespace)

    private companion object {
        private const val ENQUEUE_BATCH_SIZE = 512
    }
}
