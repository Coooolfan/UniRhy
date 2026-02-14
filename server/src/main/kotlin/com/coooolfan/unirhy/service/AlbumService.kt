package com.coooolfan.unirhy.service

import com.coooolfan.unirhy.model.Album
import com.coooolfan.unirhy.model.id
import com.coooolfan.unirhy.model.title
import org.babyfish.jimmer.Page
import org.babyfish.jimmer.sql.fetcher.Fetcher
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.babyfish.jimmer.sql.kt.ast.expression.ilike
import org.babyfish.jimmer.sql.kt.ast.expression.ne
import org.babyfish.jimmer.sql.kt.ast.table.sourceId
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

            if (filterSingle)
                where(
                    subQueries.forList(Album::recordings) {
                        where(table.sourceId eq parentTable.id)
                        selectCount()
                    } ne 1L
                )

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
