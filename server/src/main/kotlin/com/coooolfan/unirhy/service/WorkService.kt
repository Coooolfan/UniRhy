package com.coooolfan.unirhy.service

import com.coooolfan.unirhy.model.Work
import com.coooolfan.unirhy.model.id
import org.babyfish.jimmer.Page
import org.babyfish.jimmer.sql.fetcher.Fetcher
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.util.*

@Service
class WorkService(private val sql: KSqlClient) {
    companion object {
        const val DEFAULT_WORK_RANDOM_LENGTH_MILLIS: Long = 24L * 60 * 60 * 1000
    }

    fun listWork(pageIndex: Int, pageSize: Int, fetcher: Fetcher<Work>): Page<Work> {
        return sql.createQuery(Work::class) {
            orderBy(table.id)
            select(table.fetch(fetcher))
        }.fetchPage(pageIndex, pageSize)
    }

    fun randomWork(
        timestamp: Long?,
        length: Long?,
        offset: Long?,
        fetcher: Fetcher<Work>,
    ): Work? {
        val timestampMillis = normalizeTimestampMillis(timestamp ?: System.currentTimeMillis())
        val lengthMillis = length ?: DEFAULT_WORK_RANDOM_LENGTH_MILLIS
        val offsetMillis = offset ?: 0L

        if (lengthMillis <= 0L) {
            throw ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST,
                "length must be > 0 (milliseconds)",
            )
        }

        val seed = Math.floorDiv(timestampMillis - offsetMillis, lengthMillis)
        return randomWorkBySeed(seed, fetcher)
    }

    private fun randomWorkBySeed(seed: Long, fetcher: Fetcher<Work>): Work? {
        val count = sql.executeQuery(Work::class) {
            selectCount()
        }.first()

        if (count == 0L) return null

        val offset = Random(seed).nextLong(count)
        return sql.createQuery(Work::class) {
            orderBy(table.id)
            select(table.fetch(fetcher))
        }.limit(1, offset).execute().firstOrNull()
    }

    fun deleteWork(id: Long) {
        sql.deleteById(Work::class, id)
    }

    fun getWorkById(id: Long, fetcher: Fetcher<Work>): Work {
        return sql.findOneById(fetcher, id)
    }
}

private fun normalizeTimestampMillis(timestamp: Long): Long {
    return if (timestamp in 0..9_999_999_999L) timestamp * 1000 else timestamp
}
