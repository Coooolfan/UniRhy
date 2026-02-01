package com.coooolfan.unirhy.config

import org.babyfish.jimmer.sql.kt.KSqlClient
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import java.util.logging.Level
import java.util.logging.Logger

@Component
class StartRunner(private val sql: KSqlClient) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {

        Logger.getLogger("org.jaudiotagger").setLevel(Level.OFF)

        sql.validateDatabase()
    }
}