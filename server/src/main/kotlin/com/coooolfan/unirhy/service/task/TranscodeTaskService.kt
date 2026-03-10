package com.coooolfan.unirhy.service.task

import com.coooolfan.unirhy.model.storage.FileProviderType
import com.coooolfan.unirhy.service.task.common.AsyncTaskManager
import com.coooolfan.unirhy.service.task.common.TaskService
import com.coooolfan.unirhy.service.task.common.TaskType
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class TranscodeTaskService(
    private val sql: KSqlClient,
    asyncTaskManager: AsyncTaskManager,
) : TaskService<TranscodeTaskRequest>(
    asyncTaskManager
) {
    override val type: TaskType = TaskType.TRANSCODE

    private val logger = LoggerFactory.getLogger(TranscodeTaskService::class.java)

    override fun execute(request: TranscodeTaskRequest) {
        TODO("Not yet implemented")
    }
}

data class TranscodeTaskRequest(
    val srcProviderType: FileProviderType,
    val srcProviderId: Long,
    val dstProviderType: FileProviderType,
    val dstProviderId: Long,
    val targetCodec: CodecType = CodecType.OPUS,
)

enum class CodecType {
    MP3,
    OPUS,
    AAC,
}
