package com.coooolfan.unirhy.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 任务 Worker 使用 virtual-thread-per-task executor。
 * 并发上界由 [com.coooolfan.unirhy.service.task.dispatch.TaskCapacityManager]
 * 的 per-TaskKey 配额控制，线程池本身不设限。
 */
@Configuration
class TaskExecutorsConfig {

    @Bean(destroyMethod = "close")
    fun taskExecutorService(): ExecutorService =
        Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("task-worker-", 0).factory())
}
