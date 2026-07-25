package com.coooolfan.unirhy.service.task.dispatch

import com.coooolfan.unirhy.service.task.PluginTaskService
import com.coooolfan.unirhy.service.task.common.AsyncTaskStore
import com.coooolfan.unirhy.service.task.spi.TaskExecutorRegistry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException

/**
 * 发现并调度待执行任务。所有任务同构：能否调度只取决于本节点是否注册了
 * 对应 TaskKey 的 Executor 以及该 key 的本地配额，与任务在树中的位置无关。
 */
@Component
class TaskDispatcher(
    private val pluginTaskService: PluginTaskService,
    private val taskStore: AsyncTaskStore,
    private val capacityManager: TaskCapacityManager,
    private val executorRegistry: TaskExecutorRegistry,
    private val executionEngine: TaskExecutionEngine,
    @Qualifier("taskExecutorService") private val workerExecutor: ExecutorService,
) {
    private val logger = LoggerFactory.getLogger(TaskDispatcher::class.java)

    fun tick() {
        runPhase("plugin registry reconciliation") { pluginTaskService.reconcile() }
        runPhase("task discovery") { dispatchTasks() }
    }

    private fun dispatchTasks() {
        val pendingCounts = taskStore.discoverPendingCounts()
        for ((key, pendingCount) in pendingCounts) {
            if (executorRegistry.find(key) == null) continue
            val slots = minOf(pendingCount, capacityManager.availableSlots(key).toLong())
            for (i in 0 until slots) {
                if (!capacityManager.tryAcquireSlot(key)) break
                val submitted = runCatching {
                    workerExecutor.execute {
                        try {
                            executionEngine.executeOne(key)
                        } catch (ex: Throwable) {
                            logger.error("Task worker crashed for {}", key, ex)
                        } finally {
                            capacityManager.releaseSlot(key)
                        }
                    }
                }
                if (submitted.isFailure) {
                    capacityManager.releaseSlot(key)
                    if (submitted.exceptionOrNull() !is RejectedExecutionException) {
                        throw submitted.exceptionOrNull()!!
                    }
                    break
                }
            }
        }
    }

    private fun runPhase(phase: String, block: () -> Unit) {
        try {
            block()
        } catch (ex: Throwable) {
            logger.error("Dispatcher phase failed: {}", phase, ex)
        }
    }
}
