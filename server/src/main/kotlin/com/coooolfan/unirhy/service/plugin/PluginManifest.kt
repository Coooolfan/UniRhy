package com.coooolfan.unirhy.service.plugin

import com.coooolfan.unirhy.service.task.common.TaskFormSchema
import com.coooolfan.unirhy.service.task.common.TaskKey
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

const val UNIRHY_WASM_ABI_V1 = "unirhy-wasm-abi-v1"

/**
 * 插件 manifest（plugin.yml）。
 *
 * - `id` 即任务命名空间（反向域名），`task.type` 为任务名段，`(id, task.type)` 构成任务身份二元组
 * - 一个插件只声明一个任务，插件即该任务的唯一提供者
 * - 不定义 permissions；所有已启用插件获得同一组 Host imports
 */
data class PluginManifest(
    val id: String,
    val name: String? = null,
    val version: String,
    val runtime: PluginRuntime,
    val task: PluginTaskSpec,
    val form: PluginFormSpec? = null,
) {
    val taskKey: TaskKey get() = TaskKey(id, task.type)

    /** 组装持久化用的完整表单定义 `{schema, order}`；未声明 form 时使用空表单 */
    fun formDefinition(): JsonNode {
        val formSpec = form ?: return TaskFormSchema.emptyFormDefinition()
        val mapper = JsonMapper.shared()
        val node = mapper.createObjectNode()
        node.set("schema", formSpec.schema)
        node.set("order", mapper.valueToTree(formSpec.order))
        return node
    }

    /**
     * 校验 manifest 语义，返回首个错误信息；通过时返回 null。
     * 运行时类型 / ABI 校验由调用方单独处理以映射到对应错误码。
     */
    fun validate(): String? {
        if (!TaskKey.isValidNamespace(id)) {
            return "invalid plugin id (must be a reverse-domain namespace): $id"
        }
        if (TaskKey.isReservedNamespace(id)) {
            return "plugin id uses reserved namespace: $id"
        }
        if (!TaskKey.isValidTaskType(task.type)) {
            return "invalid task type (must be an upper-case identifier): ${task.type}"
        }
        if (task.concurrency <= 0) {
            return "task.concurrency must be a positive integer"
        }
        try {
            TaskFormSchema.validateFormDefinition(formDefinition())
        } catch (ex: IllegalArgumentException) {
            return ex.message
        }
        return null
    }
}

data class PluginRuntime(
    val type: String,
    val abi: String,
)

data class PluginTaskSpec(
    val type: String,
    /** 首次安装时的任务执行并发初始值 */
    val concurrency: Int,
)

data class PluginFormSpec(
    val schema: JsonNode,
    val order: List<String> = emptyList(),
)
