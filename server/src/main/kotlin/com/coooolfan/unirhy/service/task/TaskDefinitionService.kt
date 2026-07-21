package com.coooolfan.unirhy.service.task

import com.coooolfan.unirhy.error.TaskException
import com.coooolfan.unirhy.model.Plugin
import com.coooolfan.unirhy.model.enabled
import com.coooolfan.unirhy.model.formDefinition
import com.coooolfan.unirhy.model.id
import com.coooolfan.unirhy.model.name
import com.coooolfan.unirhy.model.taskType
import com.coooolfan.unirhy.service.task.common.TaskKey
import tools.jackson.databind.JsonNode
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.springframework.stereotype.Service

/**
 * 当前可提交的任务定义。内建定义来自服务端静态定义，
 * 插件定义来自已启用插件的 `form_definition`。
 */
data class TaskDefinitionView(
    val namespace: String,
    val taskType: String,
    val name: String?,
    val formDefinition: JsonNode,
)

@Service
class TaskDefinitionService(
    private val sql: KSqlClient,
) {

    fun list(): List<TaskDefinitionView> = builtInDefinitions() + enabledPluginDefinitions()

    fun get(namespace: String, taskType: String): TaskDefinitionView {
        val key = TaskKey.ofOrNull(namespace, taskType)
            ?: throw TaskException.invalidTaskKey(reason = "invalid task key: $namespace:$taskType")
        return find(key) ?: throw TaskException.definitionNotFound()
    }

    fun find(key: TaskKey): TaskDefinitionView? {
        builtInDefinitions().firstOrNull { it.namespace == key.namespace && it.taskType == key.taskType }
            ?.let { return it }
        return sql.createQuery(Plugin::class) {
            where(table.id eq key.namespace)
            where(table.taskType eq key.taskType)
            where(table.enabled eq true)
            select(table.id, table.name, table.taskType, table.formDefinition)
        }.execute().firstOrNull()?.let {
            TaskDefinitionView(namespace = it._1, name = it._2, taskType = it._3, formDefinition = it._4)
        }
    }

    /** 统计缺省范围使用的“当前定义”集合：内建 + 全部已安装插件（含禁用） */
    fun allDefinedKeys(): List<TaskKey> {
        val pluginKeys = sql.createQuery(Plugin::class) {
            select(table.id, table.taskType)
        }.execute().mapNotNull { TaskKey.ofOrNull(it._1, it._2) }
        return listOf(BuiltInTasks.METADATA_PARSE, BuiltInTasks.TRANSCODE) + pluginKeys
    }

    private fun builtInDefinitions(): List<TaskDefinitionView> = listOf(
        TaskDefinitionView(
            namespace = BuiltInTasks.METADATA_PARSE.namespace,
            taskType = BuiltInTasks.METADATA_PARSE.taskType,
            name = BuiltInTasks.METADATA_PARSE_NAME,
            formDefinition = BuiltInTasks.METADATA_PARSE_FORM,
        ),
        TaskDefinitionView(
            namespace = BuiltInTasks.TRANSCODE.namespace,
            taskType = BuiltInTasks.TRANSCODE.taskType,
            name = BuiltInTasks.TRANSCODE_NAME,
            formDefinition = BuiltInTasks.TRANSCODE_FORM,
        ),
    )

    private fun enabledPluginDefinitions(): List<TaskDefinitionView> =
        sql.createQuery(Plugin::class) {
            where(table.enabled eq true)
            orderBy(table.id)
            select(table.id, table.name, table.taskType, table.formDefinition)
        }.execute().map {
            TaskDefinitionView(namespace = it._1, name = it._2, taskType = it._3, formDefinition = it._4)
        }
}
