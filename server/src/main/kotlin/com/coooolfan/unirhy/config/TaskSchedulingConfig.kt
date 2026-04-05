package com.coooolfan.unirhy.config

import com.coooolfan.unirhy.service.task.ScanTaskService
import com.coooolfan.unirhy.service.task.TranscodeTaskService
import com.coooolfan.unirhy.service.task.DataCleanTaskService
import com.coooolfan.unirhy.service.task.PlaylistGenerateTaskService
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
    private val dataCleanTaskService: DataCleanTaskService,
    private val playlistGenerateTaskService: PlaylistGenerateTaskService,
) : SchedulingConfigurer {

    override fun configureTasks(taskRegistrar: ScheduledTaskRegistrar) {
        taskRegistrar.setTaskScheduler(taskScheduler())
        taskRegistrar.addExponentialBackoffTask { scanTaskService.consumePendingTask() }
        if (transcodeTaskService.isConsumerEnabled()) {
            taskRegistrar.addExponentialBackoffTask { transcodeTaskService.consumePendingTask() }
        } else {
            logger.warn("Transcode task scheduler disabled because ffmpeg is unavailable on this node")
        }
        taskRegistrar.addExponentialBackoffTask { vectorizeTaskService.consumePendingTask() }
        taskRegistrar.addExponentialBackoffTask { dataCleanTaskService.consumePendingTask() }
        taskRegistrar.addExponentialBackoffTask { playlistGenerateTaskService.consumePendingTask() }
    }

    @Bean(name = ["taskScheduler"], destroyMethod = "shutdown")
    fun taskScheduler(): TaskScheduler {
        return ThreadPoolTaskScheduler().apply {
            poolSize = 5
            setThreadNamePrefix("async-task-")
            initialize()
        }
    }

    private companion object {
        private val logger = LoggerFactory.getLogger(TaskSchedulingConfig::class.java)
        private val TASK_POLL_MIN_DELAY = Duration.ofMillis(500)
        private val TASK_POLL_MAX_DELAY = Duration.ofMinutes(1)
    }

    private fun ScheduledTaskRegistrar.addExponentialBackoffTask(task: () -> Boolean) {
        val trigger = ExponentialBackoffPollingTrigger(
            minDelay = TASK_POLL_MIN_DELAY,
            maxDelay = TASK_POLL_MAX_DELAY,
        )
        addTriggerTask(
            Runnable {
                trigger.recordResult(task())
            },
            trigger,
        )
    }
}
