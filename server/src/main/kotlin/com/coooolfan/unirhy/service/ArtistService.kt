package com.coooolfan.unirhy.service

import com.coooolfan.unirhy.model.Artist
import com.coooolfan.unirhy.model.alias
import com.coooolfan.unirhy.model.displayName
import com.coooolfan.unirhy.model.id
import com.coooolfan.unirhy.model.dto.ArtistMergeReq
import com.coooolfan.unirhy.model.dto.ArtistSplitReq
import com.coooolfan.unirhy.utils.arrayToString
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
    fun splitArtist(input: ArtistSplitReq): List<Artist> {
        sql.findById(Artist::class, input.sourceArtistId) ?: throw ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "source artist not found"
        )

        val artists = input.artists.map { create ->
            val displayName = create.displayName.trim()
            if (displayName.isBlank()) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "artist displayName must not be blank")
            }
            Artist {
                this.displayName = displayName
                alias = create.alias.map(String::trim).filter(String::isNotBlank).distinct()
                comment = create.comment
                avatar = null
            }
        }

        if (artists.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "split artists must not be empty")
        }

        return artists.map { artist ->
            sql.saveCommand(artist, SaveMode.INSERT_ONLY).execute().modifiedEntity
        }
    }
}
