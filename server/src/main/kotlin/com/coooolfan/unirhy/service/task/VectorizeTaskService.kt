package com.coooolfan.unirhy.service.task

import com.coooolfan.unirhy.model.*
import com.coooolfan.unirhy.model.storage.FileProviderFileSystem
import com.coooolfan.unirhy.model.storage.FileProviderType
import com.coooolfan.unirhy.service.task.common.AsyncTaskQueueStore
import com.coooolfan.unirhy.service.task.common.TaskStatus
import com.coooolfan.unirhy.service.task.common.TaskType
import com.coooolfan.unirhy.service.task.common.failureReason
import com.fasterxml.jackson.databind.ObjectMapper
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate

@Service
class VectorizeTaskService(
    private val sql: KSqlClient,
    private val objectMapper: ObjectMapper,
    private val queueStore: AsyncTaskQueueStore,
    private val transactionTemplate: TransactionTemplate,
) {

    private val logger = LoggerFactory.getLogger(VectorizeTaskService::class.java)

    fun submit(request: VectorizeTaskRequest) {
        if (request.srcProviderType != FileProviderType.FILE_SYSTEM) {
            error("Unsupported source provider type: ${request.srcProviderType}")
        }
        val srcProvider = sql.findById(FileProviderFileSystem::class, request.srcProviderId)
            ?: error("Source provider not found")

        val audioFiles = sql.createQuery(Asset::class) {
            where(table.mediaFile.fsProviderId eq srcProvider.id)
            orderBy(table.recordingId, table.mediaFile.objectKey, table.id)
            select(table.recordingId, table.mediaFile.objectKey)
        }.execute()

        val recordingAssetMap = linkedMapOf<Long, String>()
        for ((recordingId, objectKey) in audioFiles) {
            recordingAssetMap.putIfAbsent(recordingId, objectKey)
        }

        val payloads = recordingAssetMap.entries.map { entry ->
            VectorizeTaskPayload(
                recordingId = entry.key,
                srcObjectKey = entry.value,
                srcProviderId = srcProvider.id,
                apiEndpoint = request.apiEndpoint,
                apiKey = request.apiKey,
                modelName = request.modelName,
            )
        }
        val paramsJsonList = payloads.map(objectMapper::writeValueAsString)
        queueStore.enqueueIgnoringConflicts(TaskType.VECTORIZE, paramsJsonList)
    }

    fun consumePendingTask() {
        try {
            transactionTemplate.executeWithoutResult {
                val claimedTasks = queueStore.claim(TaskType.VECTORIZE, VECTORIZE_CLAIM_LIMIT)
                if (claimedTasks.isEmpty()) {
                    return@executeWithoutResult
                }
                val completionUpdates = mutableListOf<AsyncTaskLog>()
                for (claimedTask in claimedTasks) {
                    try {
                        val payload = objectMapper.readValue(claimedTask.params, VectorizeTaskPayload::class.java)
                        execute(payload)
                        completionUpdates +=
                            AsyncTaskLog {
                                id = claimedTask.id
                                status = TaskStatus.COMPLETED
                                completedReason = "SUCCESS"
                            }
                    } catch (ex: Throwable) {
                        logger.error("Vectorize task failed, logId={}", claimedTask.id, ex)
                        completionUpdates +=
                            AsyncTaskLog {
                                id = claimedTask.id
                                status = TaskStatus.FAILED
                                completedReason = failureReason(ex)
                            }
                    }
                }
                sql.saveEntities(completionUpdates, SaveMode.UPDATE_ONLY)
            }
        } catch (ex: Throwable) {
            logger.error("Failed to consume pending vectorize task", ex)
        }
    }

    private fun execute(payload: VectorizeTaskPayload) {
        // TODO: 实现实际的向量化逻辑
        logger.info(
            "Vectorize task placeholder: recordingId={}, srcObjectKey={}, endpoint={}, model={}",
            payload.recordingId,
            payload.srcObjectKey,
            payload.apiEndpoint,
            payload.modelName,
        )
    }

    private companion object {
        private const val VECTORIZE_CLAIM_LIMIT = 1L
    }
}

data class VectorizeTaskRequest(
    val srcProviderType: FileProviderType,
    val srcProviderId: Long,
    val apiEndpoint: String,
    val apiKey: String,
    val modelName: String,
)

data class VectorizeTaskPayload(
    val recordingId: Long,
    val srcObjectKey: String,
    val srcProviderId: Long,
    val apiEndpoint: String,
    val apiKey: String,
    val modelName: String,
)
