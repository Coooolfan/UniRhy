package com.coooolfan.unirhy.service

import com.coooolfan.unirhy.model.*
import org.babyfish.jimmer.Page
import org.babyfish.jimmer.sql.fetcher.Fetcher
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.*
import org.springframework.stereotype.Service

@Service
class AlbumService(private val sql: KSqlClient) {
    fun listAlbum(
        pageIndex: Int,
        pageSize: Int,
        fetcher: Fetcher<Album>,
        filterSingle: Boolean = false
    ): Page<Album> {
        return sql.createQuery(Album::class) {
            if (filterSingle) {
                where(
                    exists(
                        subQuery(AlbumRecording::class) {
                            where(table.albumId eq parentTable.id)
                            groupBy(table.albumId)
                            having(count(table.id) ne 1L)
                            select(table.albumId)
                        }
                    )
                )
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
