package com.coooolfan.unirhy.service.task.spi

import com.coooolfan.unirhy.service.task.common.TaskKey
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class TaskPlannerRegistry {
    private val planners = ConcurrentHashMap<TaskKey, TaskPlanner>()

    fun register(planner: TaskPlanner) {
        val existing = planners.putIfAbsent(planner.key, planner)
        check(existing == null) { "duplicate TaskPlanner registration for ${planner.key}" }
    }

    /** 原子替换（插件覆盖升级），key 不存在时等价于注册 */
    fun replace(planner: TaskPlanner) {
        planners[planner.key] = planner
    }

    fun unregister(key: TaskKey) {
        planners.remove(key)
    }

    fun find(key: TaskKey): TaskPlanner? = planners[key]

    fun snapshot(): Map<TaskKey, TaskPlanner> = HashMap(planners)
}
