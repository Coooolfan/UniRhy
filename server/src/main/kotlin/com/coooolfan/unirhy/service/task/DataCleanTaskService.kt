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
class DataCleanTaskService(
    private val sql: KSqlClient,
    private val objectMapper: ObjectMapper,
    private val queueStore: AsyncTaskQueueStore,
    private val transactionTemplate: TransactionTemplate,
) {

    private val logger = LoggerFactory.getLogger(DataCleanTaskService::class.java)

    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build()

    fun submit(request: DataCleanTaskRequest) {
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
            DataCleanTaskPayload(
                recordingId = entry.key,
                srcObjectKey = entry.value,
                srcProviderId = srcProvider.id,
                apiEndpoint = request.apiEndpoint,
                apiKey = request.apiKey,
                modelName = request.modelName,
            )
        }
        val paramsJsonList = payloads.map(objectMapper::writeValueAsString)
        queueStore.enqueueIgnoringConflicts(TaskType.DATA_CLEAN, paramsJsonList)
    }

    fun consumePendingTask() {
        try {
            transactionTemplate.executeWithoutResult {
                val claimedTasks = queueStore.claim(TaskType.DATA_CLEAN, DATA_CLEAN_CLAIM_LIMIT)
                if (claimedTasks.isEmpty()) {
                    return@executeWithoutResult
                }

                val completionUpdates = mutableListOf<AsyncTaskLog>()

                val taskPayloadPairs = claimedTasks.map { task ->
                    task to objectMapper.readValue(task.params, DataCleanTaskPayload::class.java)
                }

                val recordingIds = taskPayloadPairs.map { it.second.recordingId }
                val recordingMap = sql.findByIds(Recording::class, recordingIds).associateBy { it.id }

                data class TaskWithTitle(
                    val task: AsyncTaskLog,
                    val payload: DataCleanTaskPayload,
                    val title: String,
                )

                val tasksWithTitle = mutableListOf<TaskWithTitle>()
                for ((task, payload) in taskPayloadPairs) {
                    val recording = recordingMap[payload.recordingId]
                    if (recording == null) {
                        completionUpdates += AsyncTaskLog {
                            id = task.id
                            status = TaskStatus.COMPLETED
                            completedReason = "SKIPPED: recording not found"
                        }
                    } else if (recording.title.isNullOrBlank()) {
                        completionUpdates += AsyncTaskLog {
                            id = task.id
                            status = TaskStatus.COMPLETED
                            completedReason = "SKIPPED: empty title"
                        }
                    } else {
                        tasksWithTitle += TaskWithTitle(task, payload, recording.title!!)
                    }
                }

                if (tasksWithTitle.isNotEmpty()) {
                    try {
                        val apiConfig = tasksWithTitle.first().payload
                        val distinctTitles = tasksWithTitle.map { it.title }.distinct()
                        val rules = callDataCleanApi(
                            apiEndpoint = apiConfig.apiEndpoint,
                            apiKey = apiConfig.apiKey,
                            modelName = apiConfig.modelName,
                            titles = distinctTitles,
                        )

                        val ruleMap = rules.associate { it.first to it.second }

                        val recordingUpdates = mutableListOf<Recording>()
                        for (item in tasksWithTitle) {
                            val cleanedTitle = ruleMap[item.title]
                            if (cleanedTitle == null) {
                                completionUpdates += AsyncTaskLog {
                                    id = item.task.id
                                    status = TaskStatus.FAILED
                                    completedReason = "API response missing rule for title: ${item.title.take(100)}"
                                }
                            } else {
                                if (cleanedTitle != item.title) {
                                    recordingUpdates += Recording {
                                        id = item.payload.recordingId
                                        title = cleanedTitle
                                    }
                                }
                                completionUpdates += AsyncTaskLog {
                                    id = item.task.id
                                    status = TaskStatus.COMPLETED
                                    completedReason = "SUCCESS"
                                }
                            }
                        }
                        if (recordingUpdates.isNotEmpty()) {
                            sql.saveEntities(recordingUpdates, SaveMode.UPDATE_ONLY)
                        }
                    } catch (ex: Throwable) {
                        logger.error("Data clean API call failed", ex)
                        for (item in tasksWithTitle) {
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
            logger.error("Failed to consume pending data clean task", ex)
        }
    }

    private fun callDataCleanApi(
        apiEndpoint: String,
        apiKey: String,
        modelName: String,
        titles: List<String>,
    ): List<Pair<String, String>> {
        val itemTags = titles.joinToString("") { "<item>$it</item>" }
        val content = PROMPT_PREFIX + itemTags

        val requestBody = objectMapper.writeValueAsString(
            mapOf(
                "model" to modelName,
                "messages" to listOf(
                    mapOf(
                        "role" to "user",
                        "content" to content,
                    )
                ),
                "response_format" to mapOf(
                    "type" to "json_schema",
                    "json_schema" to mapOf(
                        "name" to "rewrite_rules",
                        "strict" to true,
                        "schema" to mapOf(
                            "type" to "object",
                            "properties" to mapOf(
                                "rules" to mapOf(
                                    "type" to "array",
                                    "items" to mapOf(
                                        "type" to "object",
                                        "properties" to mapOf(
                                            "before" to mapOf("type" to "string"),
                                            "after" to mapOf("type" to "string"),
                                        ),
                                        "required" to listOf("before", "after"),
                                        "additionalProperties" to false,
                                    )
                                )
                            ),
                            "required" to listOf("rules"),
                            "additionalProperties" to false,
                        )
                    )
                ),
                "reasoning" to mapOf(
                    "enabled" to true,
                ),
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
            error("Data clean API error (${response.statusCode()}): ${response.body().take(500)}")
        }

        val responseJson = objectMapper.readTree(response.body())
        val choices = responseJson["choices"]
            ?: error("Data clean API response missing 'choices' field")
        val messageContent = choices[0]?.get("message")?.get("content")?.asText()
            ?: error("Data clean API response missing message content")

        val rulesJson = objectMapper.readTree(messageContent)
        val rulesArray = rulesJson["rules"]
            ?: error("Data clean API response missing 'rules' field in content")

        return (0 until rulesArray.size()).map { i ->
            val rule = rulesArray[i]
            val before = rule["before"].asText()
            val after = rule["after"].asText()
            before to after
        }
    }

    private companion object {
        private const val DATA_CLEAN_CLAIM_LIMIT = 20L
        private const val PROMPT_PREFIX =
            "你正在一个无人介入的数据清洗任务中。歌曲名字可能包含各种后缀、描述、提示。" +
                    "移除这些提示，仅保留纯粹的歌曲名字。" +
                    "例如`光年之外《粤语版》`->`光年之外`；`打火机-0.75x`->`打火机`；`画 (完整版|英文版[Live)`->`画`。" +
                    "严格按照要求的 JSON 格式输出，不要输出解释。以下每一个项目都是一个歌曲的名字。"
    }
}

data class DataCleanTaskRequest(
    val srcProviderType: FileProviderType,
    val srcProviderId: Long,
    val apiEndpoint: String,
    val apiKey: String,
    val modelName: String,
)

data class DataCleanTaskPayload(
    val recordingId: Long,
    val srcObjectKey: String,
    val srcProviderId: Long,
    val apiEndpoint: String,
    val apiKey: String,
    val modelName: String,
)
