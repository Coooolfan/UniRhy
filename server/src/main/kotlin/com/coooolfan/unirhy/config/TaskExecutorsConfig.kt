package com.coooolfan.unirhy.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Planner 与 Handler 分别使用独立、具名的 virtual-thread-per-task executor。
 * 两者分开仅用于生命周期、日志与线程诊断，不代表不同资源配额。
 */
@Configuration
class TaskExecutorsConfig {

    @Bean(destroyMethod = "close")
    fun taskPlannerExecutor(): ExecutorService =
        Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("task-planner-", 0).factory())

    @Bean(destroyMethod = "close")
    fun asyncTaskWorkerExecutor(): ExecutorService =
        Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("async-task-worker-", 0).factory())
}
