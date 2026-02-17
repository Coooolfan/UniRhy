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

        jdbc.update(
            """
                UPDATE public.album_recording_mapping 
                SET recording_id = :targetId 
                WHERE recording_id IN (:needMergeIds)
                """.trimIndent(),
            mapOf("targetId" to input.targetId, "needMergeIds" to recordingIdsNeedMerge)
        )

        jdbc.update(
            """
                UPDATE public.playlist_recording_mapping 
                SET recording_id = :targetId 
                WHERE recording_id IN (:needMergeIds)
                """.trimIndent(),
            mapOf("targetId" to input.targetId, "needMergeIds" to recordingIdsNeedMerge)
        )
        
        jdbc.update(
            """
                UPDATE public.recording_artist_mapping 
                SET recording_id = :targetId 
                WHERE recording_id IN (:needMergeIds)
                """.trimIndent(),
            mapOf("targetId" to input.targetId, "needMergeIds" to recordingIdsNeedMerge)
        )

        sql.createDelete(Recording::class) {
            where(table.id valueIn recordingIdsNeedMerge)
        }.execute()

    }
}