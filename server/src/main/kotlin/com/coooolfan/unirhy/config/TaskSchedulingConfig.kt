package com.coooolfan.unirhy.config

import com.coooolfan.unirhy.service.task.dispatch.TaskDispatcher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.SchedulingConfigurer
import org.springframework.scheduling.config.ScheduledTaskRegistrar
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import java.time.Duration

/**
 * 单线程 scheduler 运行统一 fixed-delay tick（500ms，无初始延迟）。
 *
 * fixed delay 从上一轮 tick 完成后计算，tick 不重入、不并发；
 * 提交、启用插件、容量释放等均不发送进程内唤醒信号，最迟由下一轮 tick 发现。
 */
@Configuration
@EnableScheduling
class TaskSchedulingConfig(
    private val taskDispatcher: TaskDispatcher,
) : SchedulingConfigurer {

    override fun configureTasks(taskRegistrar: ScheduledTaskRegistrar) {
        taskRegistrar.setTaskScheduler(taskScheduler())
        taskRegistrar.addFixedDelayTask(
            { taskDispatcher.tick() },
            Duration.ofMillis(TASK_TICK_DELAY_MILLIS),
        )
    }

    @Bean(name = ["taskScheduler"], destroyMethod = "shutdown")
    fun taskScheduler(): TaskScheduler {
        return ThreadPoolTaskScheduler().apply {
            poolSize = 1
            setThreadNamePrefix("task-tick-")
            initialize()
        }
    }

    private companion object {
        private const val TASK_TICK_DELAY_MILLIS = 500L
    }
}
