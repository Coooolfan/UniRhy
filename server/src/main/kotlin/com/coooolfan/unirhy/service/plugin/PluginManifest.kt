package com.coooolfan.unirhy.service.plugin

import com.coooolfan.unirhy.service.task.common.TaskFormSchema
import com.coooolfan.unirhy.service.task.common.TaskKey
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

const val UNIRHY_WASM_ABI_V1 = "unirhy-wasm-abi-v1"

/**
 * 插件 manifest（plugin.yml）。
 *
 * - `id` 即任务命名空间（反向域名），每个 `tasks[].type` 为任务名段，
 *   `(id, tasks[].type)` 构成任务身份二元组
 * - 一个插件可声明多个任务：可被用户投递的入口任务（`userSubmittable: true`）
 *   负责展开工作单元，其余任务由上游 Executor 的返回值产生
 * - 所有任务共用同一个 `execute` 导出函数，插件按 `taskType` 自行分发
 * - 不定义 permissions；所有已启用插件获得同一组 Host imports
 */
data class PluginManifest(
    val id: String,
    val name: String? = null,
    val version: String,
    val runtime: PluginRuntime,
    val tasks: List<PluginTaskSpec>,
    val config: PluginFormSpec? = null,
) {
    fun taskKeys(): List<TaskKey> = tasks.map { TaskKey(id, it.type) }

    /** 组装某个任务的完整表单定义 `{schema, order}`；未声明 form 时使用空表单 */
    fun formDefinition(task: PluginTaskSpec): JsonNode = definition(task.form)

    /** 组装持久化用的插件级配置声明；未声明 config 时使用空定义 */
    fun configDefinition(): JsonNode = definition(config)

    private fun definition(spec: PluginFormSpec?): JsonNode {
        val formSpec = spec ?: return TaskFormSchema.emptyFormDefinition()
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
        if (tasks.isEmpty()) {
            return "plugin must declare at least one task"
        }
        val seen = mutableSetOf<String>()
        for (task in tasks) {
            if (!TaskKey.isValidTaskType(task.type)) {
                return "invalid task type (must be an upper-case identifier): ${task.type}"
            }
            if (!seen.add(task.type)) {
                return "duplicate task type: ${task.type}"
            }
            if (task.concurrency <= 0) {
                return "task.concurrency must be a positive integer for ${task.type}"
            }
        }
        if (tasks.none { it.userSubmittable }) {
            return "plugin must declare at least one user-submittable task"
        }
        try {
            for (task in tasks) {
                TaskFormSchema.validateFormDefinition(formDefinition(task))
            }
            TaskFormSchema.validateConfigDefinition(configDefinition())
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
    /** 能否被用户直接从表单投递；false 表示只能由上游 Executor 产出 */
    val userSubmittable: Boolean = false,
    /**
     * 表单定义。入口任务用于渲染投递表单；
     * 非入口任务作为 payload 契约，供跨 key 派活时校验。
     */
    val form: PluginFormSpec? = null,
)

data class PluginFormSpec(
    val schema: JsonNode,
    val order: List<String> = emptyList(),
)
