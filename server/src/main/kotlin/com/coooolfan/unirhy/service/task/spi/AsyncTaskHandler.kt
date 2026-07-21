package com.coooolfan.unirhy.service.task.spi

import com.coooolfan.unirhy.service.task.common.TaskKey
import tools.jackson.databind.JsonNode

/**
 * 执行 SPI：执行单条 `async_task` payload。
 *
 * 在任务 Worker 的事务及 savepoint 内调用；最终任务状态由执行引擎统一写入。
 */
interface AsyncTaskHandler {
    val key: TaskKey

    fun run(payload: JsonNode)
}
