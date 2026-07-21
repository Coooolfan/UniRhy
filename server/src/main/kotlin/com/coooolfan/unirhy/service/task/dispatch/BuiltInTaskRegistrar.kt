package com.coooolfan.unirhy.service.task.dispatch

import com.coooolfan.unirhy.service.task.BuiltInTasks
import com.coooolfan.unirhy.service.task.ScanTaskHandler
import com.coooolfan.unirhy.service.task.ScanTaskPlanner
import com.coooolfan.unirhy.service.task.TranscodeTaskHandler
import com.coooolfan.unirhy.service.task.TranscodeTaskPlanner
import com.coooolfan.unirhy.service.task.spi.AsyncTaskHandlerRegistry
import com.coooolfan.unirhy.service.task.spi.TaskPlannerRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 注册内建任务的 Planner / Handler 与节点本地并发容量。
 *
 * ffmpeg 不可用的节点不注册转码 Handler，对应任务保持 PENDING。
 */
@Component
class BuiltInTaskRegistrar(
    scanTaskPlanner: ScanTaskPlanner,
    scanTaskHandler: ScanTaskHandler,
    transcodeTaskPlanner: TranscodeTaskPlanner,
    transcodeTaskHandler: TranscodeTaskHandler,
    plannerRegistry: TaskPlannerRegistry,
    handlerRegistry: AsyncTaskHandlerRegistry,
    capacityManager: TaskCapacityManager,
) {
    private val logger = LoggerFactory.getLogger(BuiltInTaskRegistrar::class.java)

    init {
        plannerRegistry.register(scanTaskPlanner)
        handlerRegistry.register(scanTaskHandler)
        capacityManager.setHandlerLimit(BuiltInTasks.METADATA_PARSE, BuiltInTasks.METADATA_PARSE_CONCURRENCY)

        plannerRegistry.register(transcodeTaskPlanner)
        if (transcodeTaskHandler.ffmpegAvailable) {
            handlerRegistry.register(transcodeTaskHandler)
            capacityManager.setHandlerLimit(BuiltInTasks.TRANSCODE, BuiltInTasks.TRANSCODE_CONCURRENCY)
        } else {
            logger.warn("Transcode handler disabled because ffmpeg is unavailable on this node")
        }
    }
}
