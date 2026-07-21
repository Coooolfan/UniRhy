package com.coooolfan.unirhy.service.task.spi

import com.coooolfan.unirhy.service.task.common.TaskKey
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class AsyncTaskHandlerRegistry {
    private val handlers = ConcurrentHashMap<TaskKey, AsyncTaskHandler>()

    fun register(handler: AsyncTaskHandler) {
        val existing = handlers.putIfAbsent(handler.key, handler)
        check(existing == null) { "duplicate AsyncTaskHandler registration for ${handler.key}" }
    }

    /** 原子替换（插件覆盖升级），key 不存在时等价于注册 */
    fun replace(handler: AsyncTaskHandler) {
        handlers[handler.key] = handler
    }

    fun unregister(key: TaskKey) {
        handlers.remove(key)
    }

    fun find(key: TaskKey): AsyncTaskHandler? = handlers[key]

    fun snapshot(): Map<TaskKey, AsyncTaskHandler> = HashMap(handlers)
}
