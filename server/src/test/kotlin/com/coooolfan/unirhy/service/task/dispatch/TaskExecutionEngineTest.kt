package com.coooolfan.unirhy.service.task.dispatch

import com.coooolfan.unirhy.service.plugin.PluginStore
import com.coooolfan.unirhy.service.task.BuiltInTasks
import com.coooolfan.unirhy.service.task.common.AsyncTaskStore
import com.coooolfan.unirhy.service.task.common.ClaimedTask
import com.coooolfan.unirhy.service.task.common.TaskStatus
import com.coooolfan.unirhy.service.task.spi.AsyncTaskHandler
import com.coooolfan.unirhy.service.task.spi.AsyncTaskHandlerRegistry
import com.coooolfan.unirhy.service.task.spi.TaskPlanner
import com.coooolfan.unirhy.service.task.spi.TaskPlannerRegistry
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TaskExecutionEngineTest {
    private val objectMapper = jacksonObjectMapper()
    private val key = BuiltInTasks.METADATA_PARSE

    @Test
    fun `root task is planned from parent absence`() {
        val taskStore = mock(AsyncTaskStore::class.java)
        `when`(taskStore.claimOne(key, true)).thenReturn(
            ClaimedTask(id = 10L, parentId = null, key = key, payloadJson = "{}"),
        )

        var planned = false
        val planner = object : TaskPlanner {
            override val key = this@TaskExecutionEngineTest.key

            override fun plan(params: JsonNode): Sequence<JsonNode> {
                planned = true
                return sequenceOf(objectMapper.createObjectNode().put("child", 1))
            }
        }
        var handled = false
        val handler = object : AsyncTaskHandler {
            override val key = this@TaskExecutionEngineTest.key

            override fun run(taskId: Long, payload: JsonNode) {
                handled = true
            }
        }

        createEngine(taskStore, planner, handler).executeOne(key, includeRoots = true)

        assertTrue(planned)
        assertFalse(handled)
        verify(taskStore).enqueueChildrenIgnoringConflicts(10L, key, listOf("{\"child\":1}"))
        verify(taskStore).complete(10L, TaskStatus.COMPLETED, "SUCCESS")
    }

    @Test
    fun `child task is handled from parent presence`() {
        val taskStore = mock(AsyncTaskStore::class.java)
        `when`(taskStore.claimOne(key, false)).thenReturn(
            ClaimedTask(id = 11L, parentId = 10L, key = key, payloadJson = "{\"child\":1}"),
        )

        var planned = false
        val planner = object : TaskPlanner {
            override val key = this@TaskExecutionEngineTest.key

            override fun plan(params: JsonNode): Sequence<JsonNode> {
                planned = true
                return emptySequence()
            }
        }
        var handled = false
        val handler = object : AsyncTaskHandler {
            override val key = this@TaskExecutionEngineTest.key

            override fun run(taskId: Long, payload: JsonNode) {
                handled = taskId == 11L && payload.path("child").intValue() == 1
            }
        }

        createEngine(taskStore, planner, handler).executeOne(key, includeRoots = false)

        assertFalse(planned)
        assertTrue(handled)
        verify(taskStore).claimOne(key, false)
        verify(taskStore).complete(11L, TaskStatus.COMPLETED, "SUCCESS")
        verifyNoMoreInteractions(taskStore)
    }

    private fun createEngine(
        taskStore: AsyncTaskStore,
        planner: TaskPlanner,
        handler: AsyncTaskHandler,
    ): TaskExecutionEngine {
        val transactionManager = mock(PlatformTransactionManager::class.java)
        val transactionStatus = mock(TransactionStatus::class.java)
        `when`(transactionManager.getTransaction(any(TransactionDefinition::class.java)))
            .thenReturn(transactionStatus)
        `when`(transactionStatus.createSavepoint()).thenReturn(Any())

        val plannerRegistry = TaskPlannerRegistry().apply { register(planner) }
        val handlerRegistry = AsyncTaskHandlerRegistry().apply { register(handler) }
        return TaskExecutionEngine(
            transactionManager = transactionManager,
            taskStore = taskStore,
            plannerRegistry = plannerRegistry,
            handlerRegistry = handlerRegistry,
            pluginStore = mock(PluginStore::class.java),
            objectMapper = objectMapper,
        )
    }
}
