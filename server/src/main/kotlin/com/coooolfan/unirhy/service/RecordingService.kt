package com.coooolfan.unirhy.service

import com.coooolfan.unirhy.model.*
import com.coooolfan.unirhy.model.dto.RecordingMergeReq
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.fetcher.Fetcher
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.count
import org.babyfish.jimmer.sql.kt.ast.expression.valueIn
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class RecordingService(
    private val sql: KSqlClient,
    private val jdbc: NamedParameterJdbcTemplate,
) {
    fun getRecording(id: Long, fetcher: Fetcher<Recording>): Recording {
        return sql.findById(fetcher, id) ?: throw ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "recording not found"
        )
    }

    fun findSimilarRecordings(recordingId: Long, limit: Int, fetcher: Fetcher<Recording>): List<Recording> {
        val searchSql = """
            WITH query AS (
                SELECT embedding FROM public.recording
                WHERE id = :recordingId AND embedding IS NOT NULL
            )
            SELECT r.id
            FROM public.recording r, query q
            WHERE r.embedding IS NOT NULL AND r.id != :recordingId
            ORDER BY r.embedding <=> q.embedding
            LIMIT :limit
        """.trimIndent()

        val params = MapSqlParameterSource()
            .addValue("recordingId", recordingId)
            .addValue("limit", limit)

        val ids = jdbc.query(searchSql, params) { rs, _ -> rs.getLong("id") }
        if (ids.isEmpty()) return emptyList()
        return sql.findByIds(fetcher, ids)
    }

    fun updateRecording(input: Recording) {
        sql.saveCommand(input, SaveMode.UPDATE_ONLY).execute()
    }

    @Transactional
    fun mergeRecording(input: RecordingMergeReq) {
        val recordingIdsNeedMerge = input.needMergeIds - input.targetId

        if (recordingIdsNeedMerge.isEmpty()) return

        val allRecordingIds = input.needMergeIds + input.targetId

        sql.findById(Recording::class, input.targetId) ?: throw ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "target recording not found"
        )

        val workIdCount = sql.createQuery(Recording::class) {
            where(table.id valueIn allRecordingIds)
            select(count(table.workId, true))
        }.execute().first()

        if (workIdCount != 1L) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "merge recording workId not match")
        }

        sql.createUpdate(Asset::class) {
            set(table.recordingId, input.targetId)
            where(table.recordingId valueIn recordingIdsNeedMerge)
        }.execute()

        val params = mapOf("targetId" to input.targetId, "needMergeIds" to recordingIdsNeedMerge)

        // 把待合并录音引用到的 album 关联追加到目标录音上：
        // 若目标录音已经在该 album 中，跳过；否则按目标 album 当前最大 sort_order + 1 追加。
        jdbc.update(
            """
                INSERT INTO public.album_recording_mapping (album_id, recording_id, sort_order)
                SELECT src.album_id,
                       :targetId,
                       COALESCE((
                           SELECT MAX(sort_order) + 1
                           FROM public.album_recording_mapping
                           WHERE album_id = src.album_id
                       ), 0)
                FROM (
                    SELECT DISTINCT album_id
                    FROM public.album_recording_mapping
                    WHERE recording_id IN (:needMergeIds)
                ) AS src
                ON CONFLICT ON CONSTRAINT album_recording_mapping_uniq DO NOTHING
                """.trimIndent(),
            params
        )

        jdbc.update(
            """
                DELETE FROM public.album_recording_mapping
                WHERE recording_id IN (:needMergeIds)
                """.trimIndent(),
            params
        )

        jdbc.update(
            """
                INSERT INTO public.playlist_recording_mapping (playlist_id, recording_id, sort_order)
                SELECT src.playlist_id,
                       :targetId,
                       COALESCE((
                           SELECT MAX(sort_order) + 1
                           FROM public.playlist_recording_mapping
                           WHERE playlist_id = src.playlist_id
                       ), 0)
                FROM (
                    SELECT DISTINCT playlist_id
                    FROM public.playlist_recording_mapping
                    WHERE recording_id IN (:needMergeIds)
                ) AS src
                ON CONFLICT ON CONSTRAINT playlist_recording_mapping_uniq DO NOTHING
                """.trimIndent(),
            params
        )

        jdbc.update(
            """
                DELETE FROM public.playlist_recording_mapping
                WHERE recording_id IN (:needMergeIds)
                """.trimIndent(),
            params
        )

        jdbc.update(
            """
                INSERT INTO public.recording_artist_mapping (recording_id, artist_id)
                SELECT DISTINCT :targetId, ram.artist_id
                FROM public.recording_artist_mapping ram
                WHERE ram.recording_id IN (:needMergeIds)
                ON CONFLICT (recording_id, artist_id) DO NOTHING
                """.trimIndent(),
            params
        )

        jdbc.update(
            """
                DELETE FROM public.recording_artist_mapping
                WHERE recording_id IN (:needMergeIds)
                """.trimIndent(),
            params
        )

        sql.createDelete(Recording::class) {
            where(table.id valueIn recordingIdsNeedMerge)
        }.execute()

    }
}
