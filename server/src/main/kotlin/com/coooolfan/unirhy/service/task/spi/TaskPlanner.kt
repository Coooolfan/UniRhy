package com.coooolfan.unirhy.service.task.spi

import com.coooolfan.unirhy.service.task.common.TaskKey
import tools.jackson.databind.JsonNode

/**
 * 规划 SPI：根据 submission 参数生成任务 payload 序列。
 *
 * 在 Planner worker 的事务内调用；使用当前数据库连接的写入参与该事务。
 * 该 SPI 属于服务端内部，不属于 WASM ABI，也不开放 JVM 插件装载。
 */
interface TaskPlanner {
    val key: TaskKey

    fun plan(params: JsonNode): Sequence<JsonNode>
}
