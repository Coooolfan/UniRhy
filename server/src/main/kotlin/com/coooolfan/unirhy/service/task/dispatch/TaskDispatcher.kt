package com.coooolfan.unirhy.service.task.dispatch

import com.coooolfan.unirhy.service.task.PluginTaskService
import com.coooolfan.unirhy.service.task.common.AsyncTaskStore
import com.coooolfan.unirhy.service.task.spi.TaskPlannerRegistry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException

/** 统一发现并调度根任务与子任务。 */
@Component
class TaskDispatcher(
    private val pluginTaskService: PluginTaskService,
    private val taskStore: AsyncTaskStore,
    private val capacityManager: TaskCapacityManager,
    private val plannerRegistry: TaskPlannerRegistry,
    private val executionEngine: TaskExecutionEngine,
    @Qualifier("asyncTaskWorkerExecutor") private val workerExecutor: ExecutorService,
) {
    private val logger = LoggerFactory.getLogger(TaskDispatcher::class.java)

    fun tick() {
        runPhase("plugin registry reconciliation") { pluginTaskService.reconcile() }
        runPhase("task discovery") { dispatchTasks() }
    }

    private fun dispatchTasks() {
        val pendingCounts = taskStore.discoverPendingCounts()
        for ((key, counts) in pendingCounts) {
            val includeRoots = plannerRegistry.find(key) != null
            val executableCount = counts.childCount + if (includeRoots) counts.rootCount else 0L
            val slots = minOf(executableCount, capacityManager.availableHandlerSlots(key).toLong())
            for (i in 0 until slots) {
                if (!capacityManager.tryAcquireHandlerSlot(key)) break
                val submitted = runCatching {
                    workerExecutor.execute {
                        try {
                            executionEngine.executeOne(key, includeRoots)
                        } catch (ex: Throwable) {
                            logger.error("Task worker crashed for {}", key, ex)
                        } finally {
                            capacityManager.releaseHandlerSlot(key)
                        }
                    }
                }
                if (submitted.isFailure) {
                    capacityManager.releaseHandlerSlot(key)
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
