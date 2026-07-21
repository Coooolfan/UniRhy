package com.coooolfan.unirhy.service.task.dispatch

import com.coooolfan.unirhy.service.task.PluginTaskService
import com.coooolfan.unirhy.service.task.common.AsyncTaskStore
import com.coooolfan.unirhy.service.task.common.TaskSubmissionStore
import com.coooolfan.unirhy.service.task.spi.TaskPlannerRegistry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException

/**
 * 统一 tick：插件 Registry reconciliation → Planner discovery → Task discovery。
 *
 * Discovery 不加锁、不取得所有权，只用于减少空查询；
 * 真正的仲裁由 Worker 事务中的 `FOR UPDATE SKIP LOCKED` 完成。
 * 各阶段分别捕获异常，一个阶段失败不阻止其他阶段或下一轮 tick。
 */
@Component
class TaskDispatcher(
    private val pluginTaskService: PluginTaskService,
    private val submissionStore: TaskSubmissionStore,
    private val taskStore: AsyncTaskStore,
    private val capacityManager: TaskCapacityManager,
    private val plannerRegistry: TaskPlannerRegistry,
    private val executionEngine: TaskExecutionEngine,
    @Qualifier("taskPlannerExecutor") private val plannerExecutor: ExecutorService,
    @Qualifier("asyncTaskWorkerExecutor") private val workerExecutor: ExecutorService,
) {
    private val logger = LoggerFactory.getLogger(TaskDispatcher::class.java)

    fun tick() {
        runPhase("plugin registry reconciliation") { pluginTaskService.reconcile() }
        runPhase("planner discovery") { dispatchPlanners() }
        runPhase("task discovery") { dispatchTasks() }
    }

    private fun dispatchPlanners() {
        val pendingCounts = submissionStore.discoverPendingCounts()
        for (key in pendingCounts.keys) {
            if (plannerRegistry.find(key) == null) {
                continue
            }
            if (!capacityManager.tryBeginPlanning(key)) {
                continue
            }
            val submitted = runCatching {
                plannerExecutor.execute {
                    try {
                        executionEngine.planOne(key)
                    } catch (ex: Throwable) {
                        logger.error("Planner worker crashed for {}", key, ex)
                    } finally {
                        capacityManager.endPlanning(key)
                    }
                }
            }
            if (submitted.isFailure) {
                capacityManager.endPlanning(key)
                if (submitted.exceptionOrNull() !is RejectedExecutionException) {
                    throw submitted.exceptionOrNull()!!
                }
            }
        }
    }

    private fun dispatchTasks() {
        val pendingCounts = taskStore.discoverPendingCounts()
        for ((key, pendingCount) in pendingCounts) {
            val slots = minOf(pendingCount, capacityManager.availableHandlerSlots(key).toLong())
            for (i in 0 until slots) {
                if (!capacityManager.tryAcquireHandlerSlot(key)) {
                    break
                }
                val submitted = runCatching {
                    workerExecutor.execute {
                        try {
                            executionEngine.executeOne(key)
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
