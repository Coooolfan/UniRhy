package com.coooolfan.unirhy.sync.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

@Configuration
class PlaybackSyncSchedulerConfig {

    @Bean(name = ["playbackSyncScheduledExecutor"], destroyMethod = "shutdown")
    fun playbackSyncScheduledExecutor(): ScheduledExecutorService {
        return Executors.newScheduledThreadPool(2)
    }
}
