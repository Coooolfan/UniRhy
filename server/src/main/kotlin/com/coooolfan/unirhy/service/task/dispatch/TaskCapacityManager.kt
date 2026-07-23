package com.coooolfan.unirhy.service.task.dispatch

import com.coooolfan.unirhy.service.task.common.TaskKey
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 每节点的 TaskKey 本地并发容量。
 *
 * - Handler 容量：Dispatcher 在提交 Worker 前预留，Worker 在 `finally` 中释放；
 *   PLAN 与 RUN 共用 TaskKey 容量，不限制集群总并发。
 */
@Component
class TaskCapacityManager {

    private class HandlerCapacity(limit: Int) {
        val limit = AtomicInteger(limit)
        val inUse = AtomicInteger(0)
    }

    private val handlerCapacities = ConcurrentHashMap<TaskKey, HandlerCapacity>()

    fun setHandlerLimit(key: TaskKey, limit: Int) {
        handlerCapacities.compute(key) { _, existing ->
            existing?.also { it.limit.set(limit) } ?: HandlerCapacity(limit)
        }
    }

    fun removeHandler(key: TaskKey) {
        handlerCapacities.remove(key)
    }

    /** 当前可再预留的 Worker 数 */
    fun availableHandlerSlots(key: TaskKey): Int {
        val capacity = handlerCapacities[key] ?: return 0
        return (capacity.limit.get() - capacity.inUse.get()).coerceAtLeast(0)
    }

    fun tryAcquireHandlerSlot(key: TaskKey): Boolean {
        val capacity = handlerCapacities[key] ?: return false
        while (true) {
            val current = capacity.inUse.get()
            if (current >= capacity.limit.get()) {
                return false
            }
            if (capacity.inUse.compareAndSet(current, current + 1)) {
                return true
            }
        }
    }

    fun releaseHandlerSlot(key: TaskKey) {
        handlerCapacities[key]?.inUse?.decrementAndGet()
    }
}
