package com.coooolfan.unirhy.service.task.dispatch

import com.coooolfan.unirhy.service.plugin.PluginStore
import com.coooolfan.unirhy.service.task.BuiltInTasks
import com.coooolfan.unirhy.service.task.common.AsyncTaskStore
import com.coooolfan.unirhy.service.task.common.ClaimedTask
import com.coooolfan.unirhy.service.task.common.TaskKey
import com.coooolfan.unirhy.service.task.common.TaskStatus
import com.coooolfan.unirhy.service.task.spi.TaskExecutor
import com.coooolfan.unirhy.service.task.spi.TaskExecutorRegistry
import com.coooolfan.unirhy.service.task.spi.TaskSpec
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.`when`
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus
import tools.jackson.databind.JsonNode
import tools.jackson.module.kotlin.jacksonObjectMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TaskExecutionEngineTest {
    private val objectMapper = jacksonObjectMapper()
    private val entryKey = BuiltInTasks.METADATA_PARSE
    private val itemKey = BuiltInTasks.METADATA_PARSE_ITEM

    @Test
    fun `successors returned by executor are enqueued`() {
        val taskStore = mock(AsyncTaskStore::class.java)
        `when`(taskStore.claimOne(entryKey)).thenReturn(
            ClaimedTask(id = 10L, parentId = null, key = entryKey, payloadJson = "{}"),
        )

        var executed = false
        val executor = executor(entryKey) { _, _ ->
            executed = true
            sequenceOf(TaskSpec(itemKey, objectMapper.createObjectNode().put("child", 1)))
        }

        createEngine(taskStore, executor).executeOne(entryKey)

        assertTrue(executed)
        verify(taskStore).enqueueChildrenIgnoringConflicts(10L, itemKey, listOf("{\"child\":1}"))
        verify(taskStore).complete(10L, TaskStatus.COMPLETED, "SUCCESS")
    }

    @Test
    fun `executor returning empty sequence is a leaf`() {
        val taskStore = mock(AsyncTaskStore::class.java)
        `when`(taskStore.claimOne(itemKey)).thenReturn(
            ClaimedTask(id = 11L, parentId = 10L, key = itemKey, payloadJson = "{\"child\":1}"),
        )

        var seenTaskId = -1L
        var seenChild = -1
        val executor = executor(itemKey) { taskId, payload ->
            seenTaskId = taskId
            seenChild = payload.path("child").intValue()
            emptySequence()
        }

        createEngine(taskStore, executor).executeOne(itemKey)

        assertEquals(11L, seenTaskId)
        assertEquals(1, seenChild)
        verify(taskStore).claimOne(itemKey)
        verify(taskStore).complete(11L, TaskStatus.COMPLETED, "SUCCESS")
        verifyNoMoreInteractions(taskStore)
    }

    @Test
    fun `successors are grouped by their own task key`() {
        val taskStore = mock(AsyncTaskStore::class.java)
        `when`(taskStore.claimOne(entryKey)).thenReturn(
            ClaimedTask(id = 12L, parentId = null, key = entryKey, payloadJson = "{}"),
        )
        val foreignKey = TaskKey("com.example.plugin", "IMPORT_ITEM")

        val executor = executor(entryKey) { _, _ ->
            sequenceOf(
                TaskSpec(itemKey, objectMapper.createObjectNode().put("a", 1)),
                TaskSpec(foreignKey, objectMapper.createObjectNode().put("b", 2)),
                TaskSpec(itemKey, objectMapper.createObjectNode().put("c", 3)),
            )
        }

        createEngine(taskStore, executor).executeOne(entryKey)

        verify(taskStore).enqueueChildrenIgnoringConflicts(
            12L, itemKey, listOf("{\"a\":1}", "{\"c\":3}"),
        )
        verify(taskStore).enqueueChildrenIgnoringConflicts(12L, foreignKey, listOf("{\"b\":2}"))
        verify(taskStore).complete(12L, TaskStatus.COMPLETED, "SUCCESS")
    }

    private fun executor(
        executorKey: TaskKey,
        body: (taskId: Long, payload: JsonNode) -> Sequence<TaskSpec>,
    ): TaskExecutor = object : TaskExecutor {
        override val key = executorKey

        override fun execute(taskId: Long, payload: JsonNode): Sequence<TaskSpec> = body(taskId, payload)
    }

    private fun createEngine(
        taskStore: AsyncTaskStore,
        executor: TaskExecutor,
    ): TaskExecutionEngine {
        val transactionManager = mock(PlatformTransactionManager::class.java)
        val transactionStatus = mock(TransactionStatus::class.java)
        `when`(transactionManager.getTransaction(any(TransactionDefinition::class.java)))
            .thenReturn(transactionStatus)
        `when`(transactionStatus.createSavepoint()).thenReturn(Any())

        return TaskExecutionEngine(
            transactionManager = transactionManager,
            taskStore = taskStore,
            executorRegistry = TaskExecutorRegistry().apply { register(executor) },
            pluginStore = mock(PluginStore::class.java),
            objectMapper = objectMapper,
        )
    }
}
