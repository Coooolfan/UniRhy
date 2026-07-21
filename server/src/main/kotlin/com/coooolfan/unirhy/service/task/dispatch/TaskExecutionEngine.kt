package com.coooolfan.unirhy.service.task.dispatch

import com.coooolfan.unirhy.service.plugin.PluginStore
import com.coooolfan.unirhy.service.task.common.AsyncTaskStore
import com.coooolfan.unirhy.service.task.common.TaskKey
import com.coooolfan.unirhy.service.task.common.TaskStatus
import com.coooolfan.unirhy.service.task.common.TaskSubmissionStore
import com.coooolfan.unirhy.service.task.common.failureReason
import com.coooolfan.unirhy.service.task.spi.AsyncTaskHandlerRegistry
import com.coooolfan.unirhy.service.task.spi.TaskPlannerRegistry
import tools.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

/**
 * 任务执行引擎：PostgreSQL 事务即执行所有权。
 *
 * 每条 submission 规划与每条任务执行分别使用一个独立事务；
 * `FOR UPDATE SKIP LOCKED` 行锁、执行逻辑与最终状态写入位于同一线程、同一连接、同一事务内。
 * 可处理的异常回滚到 savepoint 后落为 `FAILED`；连接中断等故障回滚整个事务保留 `PENDING`。
 */
@Component
class TaskExecutionEngine(
    transactionManager: PlatformTransactionManager,
    private val submissionStore: TaskSubmissionStore,
    private val taskStore: AsyncTaskStore,
    private val plannerRegistry: TaskPlannerRegistry,
    private val handlerRegistry: AsyncTaskHandlerRegistry,
    private val pluginStore: PluginStore,
    private val objectMapper: ObjectMapper,
) {
    private val logger = LoggerFactory.getLogger(TaskExecutionEngine::class.java)
    private val transactionTemplate = TransactionTemplate(transactionManager)

    /** claim 并规划一条指定 TaskKey 的 submission；未领取到任务时立即返回 */
    fun planOne(key: TaskKey) {
        transactionTemplate.executeWithoutResult { status ->
            val claimed = submissionStore.claimOne(key) ?: return@executeWithoutResult
            if (!isKeyClaimable(key)) {
                status.setRollbackOnly()
                return@executeWithoutResult
            }
            val planner = plannerRegistry.find(key)
            if (planner == null) {
                status.setRollbackOnly()
                return@executeWithoutResult
            }

            val startedAt = System.currentTimeMillis()
            val savepoint = status.createSavepoint()
            try {
                val params = objectMapper.readTree(claimed.paramsJson)
                var enqueued = 0
                val batch = ArrayList<String>(ENQUEUE_BATCH_SIZE)
                for (payload in planner.plan(params)) {
                    batch += payload.toString()
                    if (batch.size >= ENQUEUE_BATCH_SIZE) {
                        enqueued += taskStore.enqueueIgnoringConflicts(claimed.id, key, batch)
                        batch.clear()
                    }
                }
                enqueued += taskStore.enqueueIgnoringConflicts(claimed.id, key, batch)
                status.releaseSavepoint(savepoint)
                submissionStore.complete(claimed.id, TaskStatus.COMPLETED, "SUCCESS")
                logger.info(
                    "Submission planned: submissionId={}, taskKey={}, enqueued={}, durationMs={}",
                    claimed.id, key, enqueued, System.currentTimeMillis() - startedAt,
                )
            } catch (ex: Exception) {
                status.rollbackToSavepoint(savepoint)
                val reason = failureReason(ex)
                submissionStore.complete(claimed.id, TaskStatus.FAILED, reason)
                logger.error(
                    "Submission planning failed: submissionId={}, taskKey={}, durationMs={}, reason={}",
                    claimed.id, key, System.currentTimeMillis() - startedAt, reason, ex,
                )
            }
        }
    }

    /** claim 并执行一条指定 TaskKey 的任务；未领取到任务时立即返回 */
    fun executeOne(key: TaskKey) {
        transactionTemplate.executeWithoutResult { status ->
            val claimed = taskStore.claimOne(key) ?: return@executeWithoutResult
            if (!isKeyClaimable(key)) {
                status.setRollbackOnly()
                return@executeWithoutResult
            }
            val handler = handlerRegistry.find(key)
            if (handler == null) {
                status.setRollbackOnly()
                return@executeWithoutResult
            }

            val startedAt = System.currentTimeMillis()
            val savepoint = status.createSavepoint()
            try {
                handler.run(objectMapper.readTree(claimed.payloadJson))
                status.releaseSavepoint(savepoint)
                taskStore.complete(claimed.id, TaskStatus.COMPLETED, "SUCCESS")
                logger.info(
                    "Task completed: taskId={}, taskKey={}, durationMs={}",
                    claimed.id, key, System.currentTimeMillis() - startedAt,
                )
            } catch (ex: Exception) {
                status.rollbackToSavepoint(savepoint)
                val reason = failureReason(ex)
                taskStore.complete(claimed.id, TaskStatus.FAILED, reason)
                logger.error(
                    "Task failed: taskId={}, taskKey={}, durationMs={}, reason={}",
                    claimed.id, key, System.currentTimeMillis() - startedAt, reason, ex,
                )
            }
        }
    }

    /** claim 谓词：内建任务始终可执行，插件任务必须存在且 enabled = true */
    private fun isKeyClaimable(key: TaskKey): Boolean =
        key.namespace == TaskKey.BUILT_IN_NAMESPACE || pluginStore.isEnabled(key.namespace)

    private companion object {
        private const val ENQUEUE_BATCH_SIZE = 512
    }
}
