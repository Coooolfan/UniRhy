package com.coooolfan.unirhy.service.task

import com.coooolfan.unirhy.error.TaskException
import com.coooolfan.unirhy.service.plugin.PluginTaskStore
import com.coooolfan.unirhy.service.plugin.key
import com.coooolfan.unirhy.service.task.common.TaskKey
import tools.jackson.databind.JsonNode
import org.springframework.stereotype.Service

/**
 * 当前可用的任务定义。内建定义来自服务端静态定义，
 * 插件定义来自已启用插件的 `plugin_task`。
 *
 * [userSubmittable] 为 true 表示可被用户从表单直接投递（入口任务）；
 * 其余任务只能由上游 Executor 的返回值产生，但仍是合法的派活目标。
 */
data class TaskDefinitionView(
    val namespace: String,
    val taskType: String,
    val name: String?,
    val userSubmittable: Boolean,
    val formDefinition: JsonNode,
)

@Service
class TaskDefinitionService(
    private val pluginTaskStore: PluginTaskStore,
) {

    /** 当前完整任务定义，包含不可直接提交的工作任务 */
    fun list(): List<TaskDefinitionView> = allDefinitions()

    fun get(namespace: String, taskType: String): TaskDefinitionView {
        val key = TaskKey.ofOrNull(namespace, taskType)
            ?: throw TaskException.invalidTaskKey(reason = "invalid task key: $namespace:$taskType")
        return find(key) ?: throw TaskException.definitionNotFound()
    }

    /** 查找任意任务定义，含非入口任务 */
    fun find(key: TaskKey): TaskDefinitionView? =
        allDefinitions().firstOrNull { it.namespace == key.namespace && it.taskType == key.taskType }

    /** 统计缺省范围使用的"当前定义"集合：内建 + 全部已安装插件（含禁用） */
    fun allDefinedKeys(): List<TaskKey> =
        BuiltInTasks.ALL_KEYS + pluginTaskStore.findAll().map { it.key }

    private fun allDefinitions(): List<TaskDefinitionView> = builtInDefinitions() + enabledPluginDefinitions()

    private fun builtInDefinitions(): List<TaskDefinitionView> = listOf(
        builtIn(BuiltInTasks.METADATA_PARSE, BuiltInTasks.METADATA_PARSE_NAME, true, BuiltInTasks.METADATA_PARSE_FORM),
        builtIn(
            BuiltInTasks.METADATA_PARSE_ITEM,
            BuiltInTasks.METADATA_PARSE_ITEM_NAME,
            false,
            BuiltInTasks.METADATA_PARSE_ITEM_FORM,
        ),
        builtIn(BuiltInTasks.TRANSCODE, BuiltInTasks.TRANSCODE_NAME, true, BuiltInTasks.TRANSCODE_FORM),
        builtIn(BuiltInTasks.TRANSCODE_ITEM, BuiltInTasks.TRANSCODE_ITEM_NAME, false, BuiltInTasks.TRANSCODE_ITEM_FORM),
    )

    private fun builtIn(
        key: TaskKey,
        name: String,
        userSubmittable: Boolean,
        formDefinition: JsonNode,
    ): TaskDefinitionView = TaskDefinitionView(
        namespace = key.namespace,
        taskType = key.taskType,
        name = name,
        userSubmittable = userSubmittable,
        formDefinition = formDefinition,
    )

    private fun enabledPluginDefinitions(): List<TaskDefinitionView> =
        pluginTaskStore.findEnabled().map { task ->
            TaskDefinitionView(
                namespace = task.pluginId,
                taskType = task.taskType,
                name = task.plugin.name,
                userSubmittable = task.userSubmittable,
                formDefinition = task.formDefinition,
            )
        }
}
