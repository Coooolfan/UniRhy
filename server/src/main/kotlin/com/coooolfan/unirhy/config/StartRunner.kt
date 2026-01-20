package com.coooolfan.unirhy.config

import jakarta.annotation.PostConstruct
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.springframework.stereotype.Component

@Component
class StartRunner(private val sql: KSqlClient) {
    @PostConstruct
    fun run() {
        sql.validateDatabase()
    }
}