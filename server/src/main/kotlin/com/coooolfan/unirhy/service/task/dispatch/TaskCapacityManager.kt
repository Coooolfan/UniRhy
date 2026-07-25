package com.coooolfan.unirhy.service.task.dispatch

import com.coooolfan.unirhy.service.task.common.TaskKey
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 每节点的 TaskKey 本地并发容量。
 *
 * Dispatcher 在提交 Worker 前预留，Worker 在 `finally` 中释放；
 * 配额只表示"本节点能同时执行几个该 key 的任务"，不限制集群总并发。
 * 阶段已编码进 TaskKey，入口任务与工作任务是不同的 key，各自独立配额。
 */
@Component
class TaskCapacityManager {

    private class Capacity(limit: Int) {
        val limit = AtomicInteger(limit)
        val inUse = AtomicInteger(0)
    }

    private val capacities = ConcurrentHashMap<TaskKey, Capacity>()

    fun setLimit(key: TaskKey, limit: Int) {
        capacities.compute(key) { _, existing ->
            existing?.also { it.limit.set(limit) } ?: Capacity(limit)
        }
    }

    fun remove(key: TaskKey) {
        capacities.remove(key)
    }

    /** 当前可再预留的 Worker 数 */
    fun availableSlots(key: TaskKey): Int {
        val capacity = capacities[key] ?: return 0
        return (capacity.limit.get() - capacity.inUse.get()).coerceAtLeast(0)
    }

    fun tryAcquireSlot(key: TaskKey): Boolean {
        val capacity = capacities[key] ?: return false
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

    fun releaseSlot(key: TaskKey) {
        capacities[key]?.inUse?.decrementAndGet()
    }
}
