package com.coooolfan.unirhy.service.task

import com.coooolfan.unirhy.model.*
import com.coooolfan.unirhy.service.SystemConfigService
import com.coooolfan.unirhy.service.task.common.AsyncTaskQueueStore
import com.coooolfan.unirhy.service.task.common.TaskStatus
import com.coooolfan.unirhy.service.task.common.TaskType
import com.coooolfan.unirhy.service.task.common.failureReason
import com.fasterxml.jackson.databind.ObjectMapper
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.fetcher.newFetcher
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

@Service
class PlaylistGenerateTaskService(
    private val sql: KSqlClient,
    private val jdbc: NamedParameterJdbcTemplate,
    private val objectMapper: ObjectMapper,
    private val queueStore: AsyncTaskQueueStore,
    private val transactionTemplate: TransactionTemplate,
    private val systemConfigService: SystemConfigService,
) {

    private val logger = LoggerFactory.getLogger(PlaylistGenerateTaskService::class.java)

    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build()

    fun submit(request: PlaylistGenerateTaskRequest, accountId: Long) {
        val payload = PlaylistGenerateTaskPayload(
            description = request.description,
            accountId = accountId,
        )
        val paramsJson = objectMapper.writeValueAsString(payload)
        queueStore.enqueueIgnoringConflicts(TaskType.PLAYLIST_GENERATE, listOf(paramsJson))
        logger.info(
            "[playlist-generate] submitted task, accountId={}, descriptionLength={}",
            accountId, request.description.length,
        )
    }

    fun consumePendingTask(): Boolean {
        return try {
            var consumedTask = false
            transactionTemplate.executeWithoutResult {
                val claimedTasks = queueStore.claim(TaskType.PLAYLIST_GENERATE, CLAIM_LIMIT)
                if (claimedTasks.isEmpty()) return@executeWithoutResult
                consumedTask = true

                val task = claimedTasks.first()
                val payload = objectMapper.readValue(task.params, PlaylistGenerateTaskPayload::class.java)

                logger.info(
                    "[playlist-generate] task started, taskId={}, accountId={}, description={}",
                    task.id, payload.accountId, payload.description.take(100),
                )

                try {
                    // === 步骤 1: 向量化用户输入 ===
                    val embeddingModelConfig = systemConfigService.get(
                        newFetcher(SystemConfig::class).by { embeddingModel() }
                    ).embeddingModel
                        ?: error("Embedding model not configured in system settings")

                    if (embeddingModelConfig.requestFormat != AiRequestFormat.JINA) {
                        error("Unsupported request format for embedding: ${embeddingModelConfig.requestFormat}. Only JINA is supported.")
                    }

                    val queryEmbedding = callEmbeddingApi(
                        apiEndpoint = embeddingModelConfig.endpoint,
                        apiKey = embeddingModelConfig.key,
                        modelName = embeddingModelConfig.model,
                        text = payload.description,
                    )
                    logger.info(
                        "[playlist-generate] embedding complete, taskId={}, inputLength={}, vectorDim={}",
                        task.id, payload.description.length, queryEmbedding.size,
                    )

                    // === 步骤 2: 向量相似度搜索 ===
                    val similarRecordingIds = searchSimilarRecordings(queryEmbedding, SEARCH_LIMIT)
                    logger.info(
                        "[playlist-generate] vector search complete, taskId={}, found={}, ids={}",
                        task.id, similarRecordingIds.size, similarRecordingIds,
                    )

                    if (similarRecordingIds.isEmpty()) {
                        sql.saveEntities(
                            listOf(AsyncTaskLog {
                                id = task.id
                                status = TaskStatus.COMPLETED
                                completedReason = "SKIPPED: no recordings with embeddings found"
                            }),
                            SaveMode.UPDATE_ONLY,
                        )
                        return@executeWithoutResult
                    }

                    // === 步骤 3: LLM 歌单生成 ===
                    val completionModelConfig = systemConfigService.get(
                        newFetcher(SystemConfig::class).by { completionModel() }
                    ).completionModel
                        ?: error("Completion model not configured in system settings")

                    if (completionModelConfig.requestFormat != AiRequestFormat.OPENAI) {
                        error("Unsupported request format for completion: ${completionModelConfig.requestFormat}. Only OPENAI is supported.")
                    }

                    val recordingFetcher = newFetcher(Recording::class).by {
                        title()
                        lyrics()
                        work { title() }
                        artists { displayName() }
                    }
                    val recordings = sql.findByIds(recordingFetcher, similarRecordingIds)

                    val playlistMeta = callCompletionApi(
                        apiEndpoint = completionModelConfig.endpoint,
                        apiKey = completionModelConfig.key,
                        modelName = completionModelConfig.model,
                        userDescription = payload.description,
                        recordings = recordings,
                    )
                    logger.info(
                        "[playlist-generate] LLM complete, taskId={}, playlistName={}, comment={}",
                        task.id, playlistMeta.name, playlistMeta.comment.take(100),
                    )

                    // === 步骤 4: 创建歌单并关联录音 ===
                    val savedPlaylist = sql.save(Playlist {
                        ownerId = payload.accountId
                        name = playlistMeta.name
                        comment = playlistMeta.comment
                    }, SaveMode.INSERT_ONLY).modifiedEntity

                    val playlistRecordings = similarRecordingIds.mapIndexed { index, recordingId ->
                        PlaylistRecording {
                            this.playlistId = savedPlaylist.id
                            this.recordingId = recordingId
                            this.sortOrder = index
                        }
                    }
                    if (playlistRecordings.isNotEmpty()) {
                        sql.saveEntities(playlistRecordings, SaveMode.INSERT_ONLY)
                    }

                    logger.info(
                        "[playlist-generate] playlist created, taskId={}, playlistId={}, recordingCount={}",
                        task.id, savedPlaylist.id, similarRecordingIds.size,
                    )

                    sql.saveEntities(
                        listOf(AsyncTaskLog {
                            id = task.id
                            status = TaskStatus.COMPLETED
                            completedReason = "SUCCESS: playlistId=${savedPlaylist.id}"
                        }),
                        SaveMode.UPDATE_ONLY,
                    )
                } catch (ex: Throwable) {
                    logger.error("[playlist-generate] task failed, taskId={}", task.id, ex)
                    sql.saveEntities(
                        listOf(AsyncTaskLog {
                            id = task.id
                            status = TaskStatus.FAILED
                            completedReason = failureReason(ex)
                        }),
                        SaveMode.UPDATE_ONLY,
                    )
                }
            }
            consumedTask
        } catch (ex: Throwable) {
            logger.error("Failed to consume pending playlist-generate task", ex)
            false
        }
    }

    private fun callEmbeddingApi(
        apiEndpoint: String,
        apiKey: String,
        modelName: String,
        text: String,
    ): FloatArray {
        val requestBody = objectMapper.writeValueAsString(
            mapOf(
                "model" to modelName,
                "task" to "retrieval.query",
                "normalized" to true,
                "input" to listOf(text),
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
        val embArr = dataArray[0]["embedding"]
            ?: error("Embedding API response missing 'embedding' in first data entry")
        return FloatArray(embArr.size()) { i -> embArr[i].floatValue() }
    }

    private fun searchSimilarRecordings(queryEmbedding: FloatArray, limit: Int): List<Long> {
        val vectorLiteral = queryEmbedding.joinToString(",", prefix = "[", postfix = "]")
        val searchSql = """
            SELECT id
            FROM public.recording
            WHERE embedding IS NOT NULL
            ORDER BY embedding <=> :queryVector::vector
            LIMIT :limit
        """.trimIndent()

        val params = MapSqlParameterSource()
            .addValue("queryVector", vectorLiteral)
            .addValue("limit", limit)

        return jdbc.query(searchSql, params) { rs, _ -> rs.getLong("id") }
    }

    private fun callCompletionApi(
        apiEndpoint: String,
        apiKey: String,
        modelName: String,
        userDescription: String,
        recordings: List<Recording>,
    ): PlaylistMetadata {
        val trackList = recordings.joinToString("\n") { recording ->
            val title = recording.title ?: recording.work.title
            val artists = recording.artists.joinToString(", ") { it.displayName }
            val lyricsSnippet = recording.lyrics.take(200)
            "- 曲名: $title | 艺术家: $artists | 歌词片段: $lyricsSnippet"
        }

        val content = PROMPT_PREFIX + "\"$userDescription\"\n\n" +
                "以下是根据语义相似度搜索到的候选曲目：\n$trackList\n\n" +
                "请根据用户的描述，为这个歌单取一个简洁、有创意的名字，并写一段简短的歌单描述。" +
                "严格按照要求的 JSON 格式输出。"

        logger.info(
            "[playlist-generate] LLM prompt constructed, promptLength={}, trackCount={}",
            content.length, recordings.size,
        )

        val requestBody = objectMapper.writeValueAsString(
            mapOf(
                "model" to modelName,
                "messages" to listOf(
                    mapOf("role" to "user", "content" to content)
                ),
                "response_format" to mapOf(
                    "type" to "json_schema",
                    "json_schema" to mapOf(
                        "name" to "playlist_metadata",
                        "strict" to true,
                        "schema" to mapOf(
                            "type" to "object",
                            "properties" to mapOf(
                                "name" to mapOf("type" to "string"),
                                "comment" to mapOf("type" to "string"),
                            ),
                            "required" to listOf("name", "comment"),
                            "additionalProperties" to false,
                        )
                    )
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
            error("Completion API error (${response.statusCode()}): ${response.body().take(500)}")
        }

        val responseJson = objectMapper.readTree(response.body())
        val messageContent = responseJson["choices"]?.get(0)?.get("message")?.get("content")?.asText()
            ?: error("Completion API response missing message content")

        val metaJson = objectMapper.readTree(messageContent)
        return PlaylistMetadata(
            name = metaJson["name"]?.asText() ?: error("Missing 'name' in LLM response"),
            comment = metaJson["comment"]?.asText() ?: error("Missing 'comment' in LLM response"),
        )
    }

    private companion object {
        private const val CLAIM_LIMIT = 1L
        private const val SEARCH_LIMIT = 10
        private const val PROMPT_PREFIX =
            "你正在为用户生成一个智能歌单。用户描述了他想听的音乐氛围：\n"
    }
}

private data class PlaylistMetadata(
    val name: String,
    val comment: String,
)

data class PlaylistGenerateTaskRequest(
    val description: String,
)

data class PlaylistGenerateTaskPayload(
    val description: String,
    val accountId: Long,
)
