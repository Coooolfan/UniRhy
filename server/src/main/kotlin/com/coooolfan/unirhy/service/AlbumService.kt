package com.coooolfan.unirhy.service

import com.coooolfan.unirhy.model.Album
import com.coooolfan.unirhy.model.id
import org.babyfish.jimmer.sql.fetcher.Fetcher
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.babyfish.jimmer.sql.kt.ast.expression.ne
import org.babyfish.jimmer.sql.kt.ast.table.sourceId
import org.springframework.stereotype.Service

@Service
class AlbumService(private val sql: KSqlClient) {
    fun listAlbum(fetcher: Fetcher<Album>, filterSingle: Boolean = false): List<Album> {
        return sql.executeQuery(Album::class) {

            if (filterSingle)
                where(
                    subQueries.forList(Album::recordings) {
                        where(table.sourceId eq parentTable.id)
                        selectCount()
                    } ne 1L
                )

            select(table.fetch(fetcher))
        }
    }
}