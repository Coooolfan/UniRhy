package com.coooolfan.unirhy.service.task

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService

@Service
class AsyncTaskManager(
    @Qualifier("taskExecutor")
    private val executor: ExecutorService,
) {

    private val logger = LoggerFactory.getLogger(AsyncTaskManager::class.java)
    private val runningTasks = ConcurrentHashMap<TaskType, RunningTaskView>()

    fun submit(type: TaskType, action: () -> Unit) {
        val running = RunningTaskView(type = type, startedAt = System.currentTimeMillis())
        val existing = runningTasks.putIfAbsent(type, running)
        if (existing != null) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Task already running: $type",
            )
        }

        executor.execute {
            try {
                action()
            } catch (ex: Throwable) {
                logger.error("Async task failed: {}", type, ex)
            } finally {
                runningTasks.remove(type, running)
            }
        }
    }

    fun listRunning(): List<RunningTaskView> {
        return runningTasks.values.sortedBy { it.startedAt }
    }
}
