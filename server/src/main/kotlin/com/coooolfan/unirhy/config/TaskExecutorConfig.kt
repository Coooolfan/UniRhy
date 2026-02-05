package com.coooolfan.unirhy.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Configuration
class TaskExecutorConfig {

    @Bean(name = ["taskExecutor"], destroyMethod = "close")
    fun taskExecutor(): ExecutorService {
        return Executors.newVirtualThreadPerTaskExecutor()
    }
}
