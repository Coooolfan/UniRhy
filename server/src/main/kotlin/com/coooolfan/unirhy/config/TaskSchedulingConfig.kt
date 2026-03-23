package com.coooolfan.unirhy.config

import com.coooolfan.unirhy.service.task.ScanTaskService
import com.coooolfan.unirhy.service.task.TranscodeTaskService
import com.coooolfan.unirhy.service.task.VectorizeTaskService
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.SchedulingConfigurer
import org.springframework.scheduling.config.ScheduledTaskRegistrar
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import java.time.Duration

@Configuration
@EnableScheduling
class TaskSchedulingConfig(
    private val scanTaskService: ScanTaskService,
    private val transcodeTaskService: TranscodeTaskService,
    private val vectorizeTaskService: VectorizeTaskService,
) : SchedulingConfigurer {

    override fun configureTasks(taskRegistrar: ScheduledTaskRegistrar) {
        taskRegistrar.setTaskScheduler(taskScheduler())
        taskRegistrar.addFixedDelayTask(
            { scanTaskService.consumePendingTask() },
            Duration.ofMillis(TASK_POLL_DELAY_MILLIS),
        )
        if (transcodeTaskService.isConsumerEnabled()) {
            taskRegistrar.addFixedDelayTask(
                { transcodeTaskService.consumePendingTask() },
                Duration.ofMillis(TASK_POLL_DELAY_MILLIS),
            )
        } else {
            logger.warn("Transcode task scheduler disabled because ffmpeg is unavailable on this node")
        }
        taskRegistrar.addFixedDelayTask(
            { vectorizeTaskService.consumePendingTask() },
            Duration.ofMillis(TASK_POLL_DELAY_MILLIS),
        )
    }

    @Bean(name = ["taskScheduler"], destroyMethod = "shutdown")
    fun taskScheduler(): TaskScheduler {
        return ThreadPoolTaskScheduler().apply {
            poolSize = 3
            setThreadNamePrefix("async-task-")
            initialize()
        }
    }

    private companion object {
        private val logger = LoggerFactory.getLogger(TaskSchedulingConfig::class.java)
        private const val TASK_POLL_DELAY_MILLIS = 500L
    }
}
