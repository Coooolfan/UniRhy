package com.coooolfan.unirhy.service

import com.coooolfan.unirhy.model.Artist
import com.coooolfan.unirhy.model.alias
import com.coooolfan.unirhy.model.displayName
import com.coooolfan.unirhy.model.id
import com.coooolfan.unirhy.model.dto.ArtistMergeReq
import com.coooolfan.unirhy.utils.arrayToString
import org.babyfish.jimmer.Page
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.fetcher.Fetcher
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.ilike
import org.babyfish.jimmer.sql.kt.ast.expression.or
import org.babyfish.jimmer.sql.kt.ast.expression.valueIn
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class ArtistService(
    private val sql: KSqlClient,
    private val jdbc: NamedParameterJdbcTemplate,
) {
    fun listArtist(pageIndex: Int, pageSize: Int, fetcher: Fetcher<Artist>): Page<Artist> {
        return sql.createQuery(Artist::class) {
            orderBy(table.id)
            select(table.fetch(fetcher))
        }.fetchPage(pageIndex, pageSize)
    }

    fun getArtistsByIds(ids: List<Long>, fetcher: Fetcher<Artist>): List<Artist> {
        if (ids.isEmpty()) return emptyList()
        return sql.createQuery(Artist::class) {
            where(table.id valueIn ids)
            orderBy(table.id)
            select(table.fetch(fetcher))
        }.execute()
    }

    fun getArtistByName(name: String, fetcher: Fetcher<Artist>): List<Artist> {
        return sql.createQuery(Artist::class) {
            where(
                or(
                    table.displayName ilike name,
                    arrayToString(table.alias) ilike name
                )
            )
            select(table.fetch(fetcher))
        }.execute()
    }

    @Transactional
    fun mergeArtists(input: ArtistMergeReq) {
        val sourceIds = input.needMergeIds - input.targetId
        if (sourceIds.isEmpty()) {
            return
        }

        val target = sql.findById(Artist::class, input.targetId) ?: throw ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "target artist not found"
        )

        val sourceArtists = sql.createQuery(Artist::class) {
            where(table.id valueIn sourceIds)
            select(table)
        }.execute()

        if (sourceArtists.size != sourceIds.size) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "source artist not found")
        }

        val mergedAlias = (target.alias + sourceArtists.flatMap { artist ->
            artist.alias + artist.displayName
        }).map(String::trim)
            .filter(String::isNotBlank)
            .distinct()

        sql.saveCommand(Artist(target) {
            alias = mergedAlias
        }, SaveMode.UPDATE_ONLY).execute()

        val params = mapOf(
            "targetId" to input.targetId,
            "sourceIds" to sourceIds,
        )

        jdbc.update(
            """
                INSERT INTO public.work_artist_mapping (work_id, artist_id)
                SELECT DISTINCT wam.work_id, :targetId
                FROM public.work_artist_mapping wam
                WHERE wam.artist_id IN (:sourceIds)
                ON CONFLICT (work_id, artist_id) DO NOTHING
                """.trimIndent(),
            params,
        )

        jdbc.update(
            """
                DELETE FROM public.work_artist_mapping
                WHERE artist_id IN (:sourceIds)
                """.trimIndent(),
            params,
        )

        jdbc.update(
            """
                INSERT INTO public.recording_artist_mapping (recording_id, artist_id)
                SELECT DISTINCT ram.recording_id, :targetId
                FROM public.recording_artist_mapping ram
                WHERE ram.artist_id IN (:sourceIds)
                ON CONFLICT (recording_id, artist_id) DO NOTHING
                """.trimIndent(),
            params,
        )

        jdbc.update(
            """
                DELETE FROM public.recording_artist_mapping
                WHERE artist_id IN (:sourceIds)
                """.trimIndent(),
            params,
        )

        sql.createDelete(Artist::class) {
            where(table.id valueIn sourceIds)
        }.execute()
    }

    @Transactional
    fun createArtist(input: Artist, fetcher: Fetcher<Artist>, copyAssociationsFrom: Long? = null): Artist {
        val created = sql.saveCommand(input, SaveMode.INSERT_ONLY).execute(fetcher).modifiedEntity
        if (copyAssociationsFrom != null) {
            val params = mapOf("sourceId" to copyAssociationsFrom, "newId" to created.id)
            jdbc.update(
                """
                INSERT INTO public.work_artist_mapping (work_id, artist_id)
                SELECT work_id, :newId FROM public.work_artist_mapping WHERE artist_id = :sourceId
                ON CONFLICT DO NOTHING
                """.trimIndent(),
                params,
            )
            jdbc.update(
                """
                INSERT INTO public.recording_artist_mapping (recording_id, artist_id)
                SELECT recording_id, :newId FROM public.recording_artist_mapping WHERE artist_id = :sourceId
                ON CONFLICT DO NOTHING
                """.trimIndent(),
                params,
            )
        }
        return created
    }

    fun updateArtist(input: Artist, fetcher: Fetcher<Artist>): Artist {
        return sql.saveCommand(input, SaveMode.UPDATE_ONLY).execute(fetcher).modifiedEntity
    }
}
