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
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

@Service
class VectorizeTaskService(
    private val sql: KSqlClient,
    private val objectMapper: ObjectMapper,
    private val queueStore: AsyncTaskQueueStore,
    private val transactionTemplate: TransactionTemplate,
) {

    private val logger = LoggerFactory.getLogger(VectorizeTaskService::class.java)

    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build()

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

                val taskPayloadPairs = claimedTasks.map { task ->
                    task to objectMapper.readValue(task.params, VectorizeTaskPayload::class.java)
                }

                val recordingIds = taskPayloadPairs.map { it.second.recordingId }
                val recordingMap = sql.findByIds(Recording::class, recordingIds).associateBy { it.id }

                data class TaskWithLyrics(
                    val task: AsyncTaskLog,
                    val payload: VectorizeTaskPayload,
                    val lyrics: String,
                )

                val tasksWithLyrics = mutableListOf<TaskWithLyrics>()
                for ((task, payload) in taskPayloadPairs) {
                    val recording = recordingMap[payload.recordingId]
                    if (recording == null) {
                        completionUpdates += AsyncTaskLog {
                            id = task.id
                            status = TaskStatus.COMPLETED
                            completedReason = "SKIPPED: recording not found"
                        }
                    } else if (recording.lyrics.isBlank()) {
                        completionUpdates += AsyncTaskLog {
                            id = task.id
                            status = TaskStatus.COMPLETED
                            completedReason = "SKIPPED: empty lyrics"
                        }
                    } else {
                        tasksWithLyrics += TaskWithLyrics(task, payload, recording.lyrics)
                    }
                }

                if (tasksWithLyrics.isNotEmpty()) {
                    try {
                        val apiConfig = tasksWithLyrics.first().payload
                        val texts = tasksWithLyrics.map { it.lyrics }
                        val embeddings = callEmbeddingApi(
                            apiEndpoint = apiConfig.apiEndpoint,
                            apiKey = apiConfig.apiKey,
                            modelName = apiConfig.modelName,
                            texts = texts,
                        )

                        val recordingUpdates = tasksWithLyrics.mapIndexed { index, item ->
                            Recording {
                                id = item.payload.recordingId
                                embedding = Embedding(embeddings[index])
                            }
                        }
                        sql.saveEntities(recordingUpdates, SaveMode.UPDATE_ONLY)

                        for (item in tasksWithLyrics) {
                            completionUpdates += AsyncTaskLog {
                                id = item.task.id
                                status = TaskStatus.COMPLETED
                                completedReason = "SUCCESS"
                            }
                        }
                    } catch (ex: Throwable) {
                        logger.error("Embedding API call failed", ex)
                        for (item in tasksWithLyrics) {
                            completionUpdates += AsyncTaskLog {
                                id = item.task.id
                                status = TaskStatus.FAILED
                                completedReason = failureReason(ex)
                            }
                        }
                    }
                }

                sql.saveEntities(completionUpdates, SaveMode.UPDATE_ONLY)
            }
        } catch (ex: Throwable) {
            logger.error("Failed to consume pending vectorize task", ex)
        }
    }

    private fun callEmbeddingApi(
        apiEndpoint: String,
        apiKey: String,
        modelName: String,
        texts: List<String>,
    ): List<FloatArray> {
        val requestBody = objectMapper.writeValueAsString(
            mapOf(
                "model" to modelName,
                "task" to "retrieval.query",
                "normalized" to true,
                "input" to texts,
            )
        )

        val request = HttpRequest.newBuilder()
            .uri(URI.create(apiEndpoint))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $apiKey")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .timeout(Duration.ofSeconds(120))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) {
            error("Embedding API error (${response.statusCode()}): ${response.body().take(500)}")
        }

        val responseJson = objectMapper.readTree(response.body())
        val dataArray = responseJson["data"]
            ?: error("Embedding API response missing 'data' field")

        return (0 until dataArray.size())
            .map { i -> dataArray[i] }
            .sortedBy { it["index"].intValue() }
            .map { item ->
                val embArr = item["embedding"]
                FloatArray(embArr.size()) { i -> embArr[i].floatValue() }
            }
    }

    private companion object {
        private const val VECTORIZE_CLAIM_LIMIT = 10L
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
