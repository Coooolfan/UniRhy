package com.coooolfan.unirhy.service

import com.coooolfan.unirhy.model.Artist
import com.coooolfan.unirhy.model.alias
import com.coooolfan.unirhy.model.displayName
import org.babyfish.jimmer.sql.fetcher.Fetcher
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.ilike
import org.babyfish.jimmer.sql.kt.ast.expression.or
import org.babyfish.jimmer.sql.kt.ast.expression.sql
import org.springframework.stereotype.Service

@Service
class ArtistService(private val sql: KSqlClient) {
    fun getArtistByName(name: String, fetcher: Fetcher<Artist>): List<Artist> {
        return sql.createQuery(Artist::class) {
            where(
                or(
                    table.displayName ilike name,
                    sql(String::class, "array_to_string(%e, ',')") { expression(table.alias) } ilike name
                )
            )
            select(table.fetch(fetcher))
        }.execute()
    }
}