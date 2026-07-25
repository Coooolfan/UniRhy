package com.coooolfan.unirhy.service.task

import com.coooolfan.unirhy.service.plugin.PluginTaskStore
import com.coooolfan.unirhy.service.task.common.TaskKey
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import tools.jackson.module.kotlin.jacksonObjectMapper
import kotlin.test.Test
import kotlin.test.assertEquals

class TaskDefinitionServiceTest {

    @Test
    fun `list returns entry and worker definitions with backend names`() {
        val pluginTaskStore = mock(PluginTaskStore::class.java)
        `when`(pluginTaskStore.findEnabled()).thenReturn(emptyList())
        val service = TaskDefinitionService(jacksonObjectMapper(), pluginTaskStore)

        val definitions = service.list()

        assertEquals(BuiltInTasks.ALL_KEYS, definitions.map { TaskKey(it.namespace, it.taskType) })
        assertEquals(
            listOf(
                BuiltInTasks.METADATA_PARSE_NAME,
                BuiltInTasks.METADATA_PARSE_ITEM_NAME,
                BuiltInTasks.TRANSCODE_NAME,
                BuiltInTasks.TRANSCODE_ITEM_NAME,
            ),
            definitions.map { it.name },
        )
        assertEquals(listOf(true, false, true, false), definitions.map { it.userSubmittable })
    }
}
