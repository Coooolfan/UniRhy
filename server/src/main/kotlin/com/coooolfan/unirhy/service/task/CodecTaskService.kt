package com.coooolfan.unirhy.service.task

import com.coooolfan.unirhy.model.storage.FileProviderType
import com.coooolfan.unirhy.service.task.common.AsyncTaskManager
import com.coooolfan.unirhy.service.task.common.TaskService
import com.coooolfan.unirhy.service.task.common.TaskType
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class CodecTaskService(
    private val sql: KSqlClient,
    asyncTaskManager: AsyncTaskManager,
) : TaskService<CodecTaskRequest>(
    asyncTaskManager
) {
    override val type: TaskType = TaskType.CODEC

    private val logger = LoggerFactory.getLogger(CodecTaskService::class.java)

    override fun execute(request: CodecTaskRequest) {
        TODO("Not yet implemented")
    }
}

data class CodecTaskRequest(
    val srcProviderType: FileProviderType,
    val srcProviderId: Long,
    val dstProviderType: FileProviderType,
    val dstProviderId: Long,
    val codecType: CodecType = CodecType.OPUS,
)

enum class CodecType {
    MP3,
    OPUS,
    AAC,
}
