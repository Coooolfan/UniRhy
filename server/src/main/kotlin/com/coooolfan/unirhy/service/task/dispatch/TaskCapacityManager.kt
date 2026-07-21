package com.coooolfan.unirhy.service.task.dispatch

import com.coooolfan.unirhy.service.task.common.TaskKey
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 每节点的 TaskKey 本地并发容量。
 *
 * - Handler 容量：Dispatcher 在提交 Worker 前预留，Worker 在 `finally` 中释放；
 *   不限制集群总并发，降低并发值不中断已经执行的任务。
 * - Planner 容量：每 TaskKey single-flight，同一节点对同一 TaskKey 同时只规划一条 submission。
 */
@Component
class TaskCapacityManager {

    private class HandlerCapacity(limit: Int) {
        val limit = AtomicInteger(limit)
        val inUse = AtomicInteger(0)
    }

    private val handlerCapacities = ConcurrentHashMap<TaskKey, HandlerCapacity>()
    private val planningKeys = ConcurrentHashMap.newKeySet<TaskKey>()

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

    fun tryBeginPlanning(key: TaskKey): Boolean = planningKeys.add(key)

    fun endPlanning(key: TaskKey) {
        planningKeys.remove(key)
    }
}
