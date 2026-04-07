package com.coooolfan.unirhy.service

import com.coooolfan.unirhy.model.*
import org.babyfish.jimmer.Page
import org.babyfish.jimmer.sql.fetcher.Fetcher
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.count
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.babyfish.jimmer.sql.kt.ast.expression.ilike
import org.babyfish.jimmer.sql.kt.ast.expression.valueIn
import org.springframework.stereotype.Service

@Service
class AlbumService(private val sql: KSqlClient) {
    fun listAlbum(
        pageIndex: Int,
        pageSize: Int,
        fetcher: Fetcher<Album>,
        filterSingle: Boolean = false
    ): Page<Album> {
        val filteredAlbumIds: List<Long>? = if (!filterSingle) {
            null
        } else {
            sql.createQuery(AlbumRecording::class) {
                groupBy(table.albumId)
                select(
                    table.albumId,
                    count(table.id),
                )
            }.execute()
                .filter { it._2 != 1L }
                .map { it._1 }
        }

        return sql.createQuery(Album::class) {
            if (filteredAlbumIds != null) {
                if (filteredAlbumIds.isEmpty()) {
                    where(table.id eq -1L)
                } else {
                    where(table.id valueIn filteredAlbumIds)
                }
            }

            orderBy(table.id)
            select(table.fetch(fetcher))
        }.fetchPage(pageIndex, pageSize)
    }

    fun getAlbum(id: Long, fetcher: Fetcher<Album>): Album {
        return sql.findOneById(fetcher, id)
    }

    fun getAlbumByName(name: String, fetcher: Fetcher<Album>): List<Album> {
        return sql.createQuery(Album::class) {
            where(table.title.ilike(name))
            select(table.fetch(fetcher))
        }.execute()

    }
}
