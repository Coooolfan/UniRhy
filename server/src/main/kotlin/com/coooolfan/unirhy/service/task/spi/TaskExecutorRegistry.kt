package com.coooolfan.unirhy.service.task.spi

import com.coooolfan.unirhy.service.task.common.TaskKey
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * 节点本地的 Executor 注册表。注册与否就是本节点对该 TaskKey 的能力声明：
 * 缺少本地能力（如 ffmpeg）时不注册对应 key，该 key 的任务留给其他节点。
 */
@Component
class TaskExecutorRegistry {
    private val executors = ConcurrentHashMap<TaskKey, TaskExecutor>()

    fun register(executor: TaskExecutor) {
        val existing = executors.putIfAbsent(executor.key, executor)
        check(existing == null) { "duplicate TaskExecutor registration for ${executor.key}" }
    }

    /** 原子替换（插件覆盖升级），key 不存在时等价于注册 */
    fun replace(executor: TaskExecutor) {
        executors[executor.key] = executor
    }

    fun unregister(key: TaskKey) {
        executors.remove(key)
    }

    fun find(key: TaskKey): TaskExecutor? = executors[key]
}
