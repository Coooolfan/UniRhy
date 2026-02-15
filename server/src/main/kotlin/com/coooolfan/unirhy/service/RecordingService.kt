package com.coooolfan.unirhy.service

import com.coooolfan.unirhy.model.Recording
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.springframework.stereotype.Service

@Service
class RecordingService(private val sql: KSqlClient) {
    fun updateRecording(input: Recording) {
        sql.saveCommand(input, SaveMode.UPDATE_ONLY).execute()
    }
}