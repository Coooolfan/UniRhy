package com.coooolfan.unirhy.service

import com.coooolfan.unirhy.error.ArtistException
import com.coooolfan.unirhy.error.CommonException
import com.coooolfan.unirhy.model.Artist
import com.coooolfan.unirhy.model.alias
import com.coooolfan.unirhy.model.artists
import com.coooolfan.unirhy.model.by
import com.coooolfan.unirhy.model.displayName
import com.coooolfan.unirhy.model.id
import com.coooolfan.unirhy.model.dto.ArtistMergeReq
import com.coooolfan.unirhy.utils.arrayToString
import org.babyfish.jimmer.Page
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.fetcher.Fetcher
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.fetcher.newFetcher
import org.babyfish.jimmer.sql.kt.ast.expression.ilike
import org.babyfish.jimmer.sql.kt.ast.expression.or
import org.babyfish.jimmer.sql.kt.ast.expression.valueIn
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

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

    fun getArtistById(id: Long, fetcher: Fetcher<Artist>): Artist {
        return sql.findById(fetcher, id) ?: throw CommonException.NotFound()
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

        val target = sql.findById(Artist::class, input.targetId)
            ?: throw ArtistException.TargetNotFound()

        val sourceArtists = sql.createQuery(Artist::class) {
            where(table.id valueIn sourceIds)
            select(table)
        }.execute()

        if (sourceArtists.size != sourceIds.size) {
            throw ArtistException.SourceNotFound()
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

    /**
     * 将一位艺术家拆分为多位同名关联的艺术家。
     *
     * [names] 的首项用于重命名源艺术家，其余各项创建为新艺术家并复制源艺术家的作品与录音关联。
     */
    @Transactional
    fun splitArtist(sourceArtistId: Long, names: List<String>) {
        require(names.size >= 2) { "names must contain at least two entries" }
        if (sql.findById(Artist::class, sourceArtistId) == null) throw CommonException.NotFound()

        val idOnly = newFetcher(Artist::class).by { }
        updateArtist(
            Artist {
                id = sourceArtistId
                displayName = names.first()
                alias = emptyList()
            },
            idOnly,
        )
        for (name in names.drop(1)) {
            createArtist(
                Artist {
                    displayName = name
                    alias = emptyList()
                    comment = ""
                },
                idOnly,
                sourceArtistId,
            )
        }
    }
}
