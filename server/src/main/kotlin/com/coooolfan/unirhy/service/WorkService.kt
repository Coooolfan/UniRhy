package com.coooolfan.unirhy.service

import com.coooolfan.unirhy.model.Work
import org.babyfish.jimmer.sql.fetcher.Fetcher
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.springframework.stereotype.Service

@Service
class WorkService(private val sql: KSqlClient) {
    fun listWork(fetcher: Fetcher<Work>): List<Work> {
        return sql.findAll(fetcher)
    }

    fun deleteWork(id: Long) {
        sql.deleteById(Work::class, id)
    }
}