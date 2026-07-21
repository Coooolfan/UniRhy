package com.coooolfan.unirhy.service.task.common

/**
 * 任务身份二元组：namespace（反向域名）+ taskType（全大写标识符）。
 *
 * 组合串 `{namespace}:{TASK_TYPE}` 仅用于前端稳定 id、日志与集合查询参数的紧凑序列化，
 * 服务端内部与数据库均以两个独立字段表示。
 */
data class TaskKey(
    val namespace: String,
    val taskType: String,
) {
    init {
        require(isValidNamespace(namespace)) { "invalid task namespace: $namespace" }
        require(isValidTaskType(taskType)) { "invalid task type: $taskType" }
    }

    fun compact(): String = "$namespace$COMPACT_SEPARATOR$taskType"

    override fun toString(): String = compact()

    companion object {
        /** `app.unirhy` 开头的命名空间整体保留，插件不允许使用 */
        const val RESERVED_NAMESPACE_PREFIX = "app.unirhy"

        /** 内建任务命名空间 */
        const val BUILT_IN_NAMESPACE = "app.unirhy.built-in"

        private const val COMPACT_SEPARATOR = ':'

        private val NAMESPACE_REGEX = Regex("""^[a-z0-9][a-z0-9_-]*(\.[a-z0-9][a-z0-9_-]*)+$""")
        private val TASK_TYPE_REGEX = Regex("""^[A-Z][A-Z0-9_]*$""")
        private const val MAX_SEGMENT_LENGTH = 255

        fun isValidNamespace(namespace: String): Boolean =
            namespace.length <= MAX_SEGMENT_LENGTH && NAMESPACE_REGEX.matches(namespace)

        fun isValidTaskType(taskType: String): Boolean =
            taskType.length <= MAX_SEGMENT_LENGTH && TASK_TYPE_REGEX.matches(taskType)

        fun isReservedNamespace(namespace: String): Boolean =
            namespace.startsWith(RESERVED_NAMESPACE_PREFIX)

        /** 解析紧凑序列化形式，格式非法时抛出 [IllegalArgumentException] */
        fun parse(compact: String): TaskKey {
            val separatorIndex = compact.indexOf(COMPACT_SEPARATOR)
            require(separatorIndex > 0 && separatorIndex < compact.length - 1) {
                "invalid task key: $compact"
            }
            return TaskKey(
                namespace = compact.substring(0, separatorIndex),
                taskType = compact.substring(separatorIndex + 1),
            )
        }

        fun parseOrNull(compact: String): TaskKey? = runCatching { parse(compact) }.getOrNull()

        fun of(namespace: String, taskType: String): TaskKey = TaskKey(namespace, taskType)

        fun ofOrNull(namespace: String, taskType: String): TaskKey? =
            runCatching { TaskKey(namespace, taskType) }.getOrNull()
    }
}
