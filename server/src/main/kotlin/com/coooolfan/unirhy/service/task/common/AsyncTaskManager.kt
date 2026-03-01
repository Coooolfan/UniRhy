package com.coooolfan.unirhy.service.task.common

import com.coooolfan.unirhy.model.*
import com.fasterxml.jackson.databind.ObjectMapper
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.babyfish.jimmer.sql.kt.ast.expression.valueIn
import org.babyfish.jimmer.sql.kt.fetcher.newFetcher
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService

@Service
class AsyncTaskManager(
    private val sql: KSqlClient,
    private val objectMapper: ObjectMapper,
    @Qualifier("taskExecutor")
    private val executor: ExecutorService,
) {

    private val logger = LoggerFactory.getLogger(AsyncTaskManager::class.java)
    private val runningTasks = ConcurrentHashMap<TaskType, RunningTaskState>()

    fun submit(type: TaskType, params: Any, action: () -> Unit) {
        val startedAt = Instant.now()
        val pendingRunning = RunningTaskState(logId = null)
        val existing = runningTasks.putIfAbsent(type, pendingRunning)
        if (existing != null) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Task already running: $type",
            )
        }

        val runningLog = try {
            createRunningLog(type, startedAt, params)
        } catch (ex: Throwable) {
            runningTasks.remove(type, pendingRunning)
            throw ex
        }
        val running = pendingRunning.copy(logId = runningLog.id)
        runningTasks[type] = running

        try {
            executor.execute {
                try {
                    action()
                    completeLog(runningLog.id, "SUCCESS")
                } catch (ex: Throwable) {
                    logger.error("Async task failed: {}", type, ex)
                    completeLog(runningLog.id, failureReason(ex))
                } finally {
                    runningTasks.remove(type, running)
                }
            }
        } catch (ex: Throwable) {
            runningTasks.remove(type, running)
            completeLog(runningLog.id, failureReason(ex))
            throw ex
        }
    }

    fun listRunning(): List<AsyncTaskLog> {
        val runningLogIds = listRunningLogIds()
        if (runningLogIds.isEmpty()) {
            return emptyList()
        }
        return sql.createQuery(AsyncTaskLog::class) {
            where(table.id valueIn runningLogIds)
            orderBy(table.startedAt)
            select(table)
        }.execute()
    }

    fun listRunningLogIds(): Set<Long> {
        return runningTasks.values
            .mapNotNull { it.logId }
            .toSet()
    }

    private fun createRunningLog(type: TaskType, startedAt: Instant, params: Any): AsyncTaskLog {
        val paramsJson = objectMapper.writeValueAsString(params)
        return sql.saveCommand(
            AsyncTaskLog {
                taskType = type
                this.startedAt = startedAt
                this.params = paramsJson
            },
            SaveMode.INSERT_ONLY,
        ).execute(ASYNC_TASK_LOG_FETCHER).modifiedEntity
    }

    private fun completeLog(logId: Long, reason: String) {
        try {
            sql.createUpdate(AsyncTaskLog::class) {
                set(table.completedAt, Instant.now())
                set(table.completedReason, reason)
                where(table.id eq logId)
            }.execute()
        } catch (ex: Throwable) {
            logger.error("Failed to complete async task log id={}, reason={}", logId, reason, ex)
        }
    }

    private fun failureReason(ex: Throwable): String {
        val simpleName = ex::class.simpleName ?: ex.javaClass.name
        val message = ex.message?.trim().orEmpty()
        return if (message.isBlank()) {
            "FAILED: $simpleName"
        } else {
            "FAILED: $simpleName: $message"
        }
    }

    companion object {
        private val ASYNC_TASK_LOG_FETCHER = newFetcher(AsyncTaskLog::class).by {
            allScalarFields()
        }
    }
}

private data class RunningTaskState(
    val logId: Long?,
)
