package com.coooolfan.unirhy.service.task.dispatch

import com.coooolfan.unirhy.service.task.BuiltInTasks
import com.coooolfan.unirhy.service.task.ScanItemExecutor
import com.coooolfan.unirhy.service.task.ScanPlanExecutor
import com.coooolfan.unirhy.service.task.TranscodeItemExecutor
import com.coooolfan.unirhy.service.task.TranscodePlanExecutor
import com.coooolfan.unirhy.service.task.spi.TaskExecutorRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 注册内建任务的 Executor 与节点本地并发容量。
 *
 * 入口任务只依赖数据库与存储节点元数据，在所有节点注册；
 * 本地能力门控只作用于对应的工作任务 key —— ffmpeg 不可用的节点不注册
 * `TRANSCODE_ITEM`，但仍能规划 `TRANSCODE`。
 */
@Component
class BuiltInTaskRegistrar(
    scanPlanExecutor: ScanPlanExecutor,
    scanItemExecutor: ScanItemExecutor,
    transcodePlanExecutor: TranscodePlanExecutor,
    transcodeItemExecutor: TranscodeItemExecutor,
    executorRegistry: TaskExecutorRegistry,
    capacityManager: TaskCapacityManager,
) {
    private val logger = LoggerFactory.getLogger(BuiltInTaskRegistrar::class.java)

    init {
        executorRegistry.register(scanPlanExecutor)
        capacityManager.setLimit(BuiltInTasks.METADATA_PARSE, BuiltInTasks.ENTRY_CONCURRENCY)

        executorRegistry.register(scanItemExecutor)
        capacityManager.setLimit(
            BuiltInTasks.METADATA_PARSE_ITEM,
            BuiltInTasks.METADATA_PARSE_ITEM_CONCURRENCY,
        )

        executorRegistry.register(transcodePlanExecutor)
        capacityManager.setLimit(BuiltInTasks.TRANSCODE, BuiltInTasks.ENTRY_CONCURRENCY)

        if (transcodeItemExecutor.ffmpegAvailable) {
            executorRegistry.register(transcodeItemExecutor)
            capacityManager.setLimit(BuiltInTasks.TRANSCODE_ITEM, BuiltInTasks.TRANSCODE_ITEM_CONCURRENCY)
        } else {
            logger.warn(
                "Transcode execution disabled because ffmpeg is unavailable on this node; " +
                    "transcode planning remains available",
            )
        }
    }
}
