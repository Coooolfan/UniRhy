package com.coooolfan.unirhy.config

import com.coooolfan.unirhy.service.task.common.AsyncTaskQueueStore
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import java.util.logging.Level
import java.util.logging.Logger

@Component
class StartRunner(
    private val sql: KSqlClient,
    private val queueStore: AsyncTaskQueueStore,
) : ApplicationRunner {
    private val logger = LoggerFactory.getLogger(StartRunner::class.java)

    override fun run(args: ApplicationArguments) {

        Logger.getLogger("org.jaudiotagger").setLevel(Level.OFF)

        sql.validateDatabase()

        val requeued = queueStore.resetRunningTasksToPending()
        if (requeued > 0) {
            logger.info("Requeued {} orphaned running task(s) left over from previous run", requeued)
        }
    }
}
