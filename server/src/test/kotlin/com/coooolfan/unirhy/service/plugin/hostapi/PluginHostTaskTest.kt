package com.coooolfan.unirhy.service.plugin.hostapi

import com.coooolfan.unirhy.service.plugin.WasmExecutionContext
import com.coooolfan.unirhy.service.task.AsyncTaskService
import com.coooolfan.unirhy.service.task.TaskDefinitionService
import com.coooolfan.unirhy.service.task.TaskDefinitionView
import com.coooolfan.unirhy.service.task.TaskStatisticsService
import com.coooolfan.unirhy.service.task.common.AsyncTaskStore
import com.coooolfan.unirhy.service.task.common.TaskKey
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import run.endive.runtime.ExportFunction
import run.endive.runtime.Instance
import run.endive.runtime.Memory
import tools.jackson.databind.node.ObjectNode
import tools.jackson.module.kotlin.jacksonObjectMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PluginHostTaskTest {
    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `enqueue adds child payloads to the current task and task key`() {
        val taskStore = mock(AsyncTaskStore::class.java)

        val response = invoke(taskStore, WasmExecutionContext(42L, TASK_TYPE), """
            {"payloads":[{"url":"a"},{"url":"b"}]}
        """.trimIndent())

        assertTrue(response.path("ok").booleanValue())
        assertEquals(0, response.path("data").path("enqueued").intValue())
        verify(taskStore).enqueueChildrenIgnoringConflicts(
            42L,
            TaskKey(PLUGIN_ID, TASK_TYPE),
            listOf("{\"url\":\"a\"}", "{\"url\":\"b\"}"),
        )
    }

    @Test
    fun `enqueue is unavailable outside plugin task execution`() {
        val response = invoke(
            mock(AsyncTaskStore::class.java),
            null,
            """{"payloads":[{"url":"a"}]}""",
        )

        assertFalse(response.path("ok").booleanValue())
        assertEquals("CONFLICT", response.path("error").path("code").stringValue())
    }

    private fun invoke(
        taskStore: AsyncTaskStore,
        context: WasmExecutionContext?,
        request: String,
    ): ObjectNode {
        val instance = mock(Instance::class.java)
        val memory = mock(Memory::class.java)
        val requestBytes = request.toByteArray()
        `when`(instance.memory()).thenReturn(memory)
        `when`(memory.readBytes(16, requestBytes.size)).thenReturn(requestBytes)
        `when`(instance.export("alloc")).thenReturn(ExportFunction { longArrayOf(1024L) })
        val taskDefinitionService = mock(TaskDefinitionService::class.java)
        `when`(taskDefinitionService.find(TaskKey(PLUGIN_ID, TASK_TYPE))).thenReturn(
            TaskDefinitionView(PLUGIN_ID, TASK_TYPE, null, objectMapper.createObjectNode()),
        )
        val function = buildTaskHostFunctions(
            taskDefinitionService = taskDefinitionService,
            asyncTaskService = mock(AsyncTaskService::class.java),
            taskStatisticsService = mock(TaskStatisticsService::class.java),
            asyncTaskStore = taskStore,
            pluginId = PLUGIN_ID,
            executionContext = context,
            objectMapper = objectMapper,
            instanceRef = { instance },
        ).single { it.name() == "host_task_enqueue" }

        function.handle().apply(instance, 16L, requestBytes.size.toLong())

        val bytes = ArgumentCaptor.forClass(ByteArray::class.java)
        verify(memory).write(eq(1024), bytes.capture())
        return objectMapper.readTree(bytes.value) as ObjectNode
    }

    companion object {
        private const val PLUGIN_ID = "com.example.plugin"
        private const val TASK_TYPE = "IMPORT_ITEMS"
    }
}
