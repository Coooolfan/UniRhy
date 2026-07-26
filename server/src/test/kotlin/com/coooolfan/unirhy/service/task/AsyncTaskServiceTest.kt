package com.coooolfan.unirhy.service.task

import com.coooolfan.unirhy.error.TaskException
import com.coooolfan.unirhy.service.plugin.PluginStore
import com.coooolfan.unirhy.service.plugin.PluginTaskStore
import com.coooolfan.unirhy.service.task.common.AsyncTaskStore
import com.coooolfan.unirhy.service.task.common.TaskStatus
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.module.kotlin.jacksonObjectMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AsyncTaskServiceTest {
    private val key = BuiltInTasks.METADATA_PARSE

    @Test
    fun `failed and cancelled tasks can be requeued by task key`() {
        val taskStore = mock(AsyncTaskStore::class.java)
        val definitionService = mock(TaskDefinitionService::class.java)
        val sourceStatuses = setOf(TaskStatus.FAILED, TaskStatus.CANCELLED)
        `when`(definitionService.find(key)).thenReturn(
            TaskDefinitionView(
                namespace = key.namespace,
                taskType = key.taskType,
                name = null,
                userSubmittable = true,
                formDefinition = jacksonObjectMapper().createObjectNode(),
            ),
        )
        `when`(taskStore.requeueByKey(key, sourceStatuses)).thenReturn(7)

        val transitioned = createService(taskStore, definitionService).transitionStatuses(
            namespace = key.namespace,
            taskType = key.taskType,
            sourceStatuses = sourceStatuses,
            targetStatus = TaskStatus.PENDING,
        )

        assertEquals(7, transitioned)
        verify(taskStore).requeueByKey(key, sourceStatuses)
    }

    @Test
    fun `pending tasks can be cancelled by task key`() {
        val taskStore = mock(AsyncTaskStore::class.java)
        `when`(taskStore.cancelPendingByKey(key, "CANCELLED_BY_ADMIN")).thenReturn(5)

        val transitioned = createService(taskStore).transitionStatuses(
            namespace = key.namespace,
            taskType = key.taskType,
            sourceStatuses = setOf(TaskStatus.PENDING),
            targetStatus = TaskStatus.CANCELLED,
        )

        assertEquals(5, transitioned)
        verify(taskStore).cancelPendingByKey(key, "CANCELLED_BY_ADMIN")
    }

    @Test
    fun `running tasks cannot be transitioned by bulk command`() {
        val taskStore = mock(AsyncTaskStore::class.java)

        assertFailsWith<TaskException.StatusConflict> {
            createService(taskStore).transitionStatuses(
                namespace = key.namespace,
                taskType = key.taskType,
                sourceStatuses = setOf(TaskStatus.RUNNING),
                targetStatus = TaskStatus.CANCELLED,
            )
        }
        verifyNoInteractions(taskStore)
    }

    private fun createService(
        taskStore: AsyncTaskStore,
        definitionService: TaskDefinitionService = mock(TaskDefinitionService::class.java),
    ): AsyncTaskService = AsyncTaskService(
        taskStore = taskStore,
        pluginStore = mock(PluginStore::class.java),
        pluginTaskStore = mock(PluginTaskStore::class.java),
        definitionService = definitionService,
        transactionTemplate = mock(TransactionTemplate::class.java),
    )
}
