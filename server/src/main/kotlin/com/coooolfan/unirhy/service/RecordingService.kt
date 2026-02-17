package com.coooolfan.unirhy.service

import com.coooolfan.unirhy.model.*
import com.coooolfan.unirhy.model.dto.RecordingMergeReq
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.count
import org.babyfish.jimmer.sql.kt.ast.expression.valueIn
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class RecordingService(
    private val sql: KSqlClient,
    private val jdbc: NamedParameterJdbcTemplate,
) {
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

        jdbc.update(
            """
                INSERT INTO public.album_recording_mapping (album_id, recording_id)
                SELECT DISTINCT arm.album_id, :targetId
                FROM public.album_recording_mapping arm
                WHERE arm.recording_id IN (:needMergeIds)
                ON CONFLICT (album_id, recording_id) DO NOTHING
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
                INSERT INTO public.playlist_recording_mapping (playlist_id, recording_id)
                SELECT DISTINCT prm.playlist_id, :targetId
                FROM public.playlist_recording_mapping prm
                WHERE prm.recording_id IN (:needMergeIds)
                ON CONFLICT (playlist_id, recording_id) DO NOTHING
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
