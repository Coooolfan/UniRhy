package com.unirhy.e2e

import com.coooolfan.unirhy.UnirhyApplication
import com.fasterxml.jackson.databind.JsonNode
import com.sun.net.httpserver.HttpServer
import com.unirhy.e2e.support.AudioFixtureMetadata
import com.unirhy.e2e.support.E2eAdminSession
import com.unirhy.e2e.support.E2eAssert
import com.unirhy.e2e.support.E2eJson
import com.unirhy.e2e.support.E2eRuntime
import com.unirhy.e2e.support.SyntheticAudioFixture
import com.unirhy.e2e.support.bootstrapAdminSession
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import org.slf4j.LoggerFactory
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.test.assertTrue
import kotlin.test.fail

@SpringBootTest(
    classes = [UnirhyApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@Tag("full")
class PlaylistGenerateTaskE2eTest {

    @LocalServerPort
    private var port: Int = 0

    private var mockEmbeddingServer: HttpServer? = null
    private var mockCompletionServer: HttpServer? = null

    @AfterAll
    fun cleanup() {
        mockEmbeddingServer?.stop(0)
        mockCompletionServer?.stop(0)
        E2eRuntime.cleanup()
    }

    @Test
    @Order(1)
    fun `playlist-generate endpoint should reject unauthenticated access`() {
        val api = com.unirhy.e2e.support.E2eHttpClient(baseUrl())
        E2eAssert.apiError(
            response = api.post(
                path = "/api/task/playlist-generate",
                json = mapOf("description" to "test description"),
            ),
            family = "COMMON",
            code = "AUTHENTICATION_FAILED",
            expectedStatus = 401,
            step = "[auth] submit playlist-generate task should require login",
        )
    }

    @Test
    @Order(2)
    fun `playlist-generate task should complete full pipeline and create playlist`() {
        val state = bootstrapAdminSession(baseUrl())

        // 1. Generate fixtures with lyrics
        val fixture = prepareScanFixtureWithLyrics(state.runtime.scanWorkspace)
        val expectedFileCount = countFilesWithExtensions(fixture.fixtureRoot, ACCEPT_EXTENSIONS)
        assertTrue(expectedFileCount > 0, "[playlist-gen] fixture should contain audio files")

        // 2. Submit scan task and wait for completion
        val fsProviderId = resolveSystemFsProviderId(state)
        val scanResponse = state.api.post(
            path = "/api/task/scan",
            json = linkedMapOf<String, Any>(
                "providerType" to "FILE_SYSTEM",
                "providerId" to fsProviderId,
            ),
        )
        E2eAssert.status(scanResponse, 202, "[playlist-gen] submit scan task should return accepted")
        awaitTaskCompletion(state, "METADATA_PARSE", expectedFileCount)

        // 3. Start mock embedding server and configure system
        val embeddingServer = startMockEmbeddingServer()
        mockEmbeddingServer = embeddingServer
        val embeddingPort = embeddingServer.address.port

        updateSystemAiConfig(
            state,
            fsProviderId = fsProviderId,
            embeddingEndpoint = "http://127.0.0.1:$embeddingPort/v1/embeddings",
            completionEndpoint = null,
        )

        // 4. Submit vectorize task (to populate recording embeddings)
        val vectorizeResponse = state.api.post(
            path = "/api/task/vectorize",
            json = mapOf("mode" to "PENDING_ONLY"),
        )
        E2eAssert.status(vectorizeResponse, 202, "[playlist-gen] submit vectorize task should return accepted")
        awaitTaskCompletion(state, "VECTORIZE", expectedFileCount)
        logger.info("[playlist-gen] vectorize tasks completed, recordings now have embeddings")

        // 5. Start mock completion server and update system config
        val completionServer = startMockCompletionServer()
        mockCompletionServer = completionServer
        val completionPort = completionServer.address.port

        updateSystemAiConfig(
            state,
            fsProviderId = fsProviderId,
            embeddingEndpoint = "http://127.0.0.1:$embeddingPort/v1/embeddings",
            completionEndpoint = "http://127.0.0.1:$completionPort/v1/chat/completions",
        )

        // 6. Submit playlist-generate task
        val generateResponse = state.api.post(
            path = "/api/task/playlist-generate",
            json = mapOf("description" to "适合运动、活泼有节奏的音乐"),
        )
        E2eAssert.status(generateResponse, 202, "[playlist-gen] submit playlist-generate task should return accepted")

        // 7. Wait for playlist-generate task to complete
        awaitTaskCompletion(state, "PLAYLIST_GENERATE", 1)
        logger.info("[playlist-gen] playlist-generate task completed successfully")

        // 8. Verify playlist was created
        val playlistsResponse = state.api.get("/api/playlists")
        E2eAssert.status(playlistsResponse, 200, "[playlist-gen] get playlists should succeed")
        val playlists = E2eJson.mapper.readTree(playlistsResponse.body())
        assertTrue(playlists.isArray, "[playlist-gen] playlists should be an array")
        assertTrue(playlists.size() > 0, "[playlist-gen] should have at least one playlist")

        val playlist = playlists[0]
        assertTrue(
            playlist.path("name").asText().isNotBlank(),
            "[playlist-gen] playlist name should not be blank",
        )
        assertTrue(
            playlist.path("comment").asText().isNotBlank(),
            "[playlist-gen] playlist comment should not be blank",
        )
        logger.info(
            "[playlist-gen] verified playlist: name={}, comment={}",
            playlist.path("name").asText(),
            playlist.path("comment").asText(),
        )
    }

    private fun prepareScanFixtureWithLyrics(scanWorkspace: Path): FixtureInfo {
        val suffix = UUID.randomUUID().toString().replace("-", "").take(12)
        val albumTitle = "e2e-playlist-gen-album-$suffix"
        val metadata = AudioFixtureMetadata(
            title = "e2e-playlist-gen-track",
            artist = "e2e-playlist-gen-artist",
            album = albumTitle,
            comment = "e2e-playlist-gen-fixture",
            lyrics = "运动的节奏让人兴奋\n活力四射的旋律\n跑步时的最佳伴侣",
        )
        val batch = SyntheticAudioFixture.generateBatch(
            scanWorkspace = scanWorkspace,
            dirName = "playlist-gen-fixture-$suffix",
            count = FIXTURE_FILE_COUNT,
            extension = "mp3",
            metadata = metadata,
        )
        return FixtureInfo(fixtureRoot = batch.fixtureRoot, albumTitle = albumTitle)
    }

    private fun startMockEmbeddingServer(): HttpServer {
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/v1/embeddings") { exchange ->
            try {
                val requestBody = exchange.requestBody.bufferedReader().readText()
                val requestJson = E2eJson.mapper.readTree(requestBody)
                val inputArray = requestJson["input"]
                val inputCount = inputArray?.size() ?: 0

                val dataEntries = (0 until inputCount).joinToString(",") { index ->
                    val values = (0 until EMBEDDING_DIM).joinToString(",") { "0.0" }
                    """{"object":"embedding","index":$index,"embedding":[$values]}"""
                }
                val responseBody =
                    """{"model":"test-model","object":"list","usage":{"total_tokens":0},"data":[$dataEntries]}"""

                val responseBytes = responseBody.toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, responseBytes.size.toLong())
                exchange.responseBody.use { it.write(responseBytes) }
            } catch (ex: Throwable) {
                logger.error("[mock] embedding server error", ex)
                exchange.sendResponseHeaders(500, -1)
            }
        }
        server.executor = null
        server.start()
        logger.info("[mock] embedding server started on port {}", server.address.port)
        return server
    }

    private fun startMockCompletionServer(): HttpServer {
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/v1/chat/completions") { exchange ->
            try {
                val contentJson = """{"name":"运动节拍","comment":"为热爱运动的你精心打造的活力歌单"}"""
                val responseBody =
                    """{"id":"test","object":"chat.completion","created":0,"model":"test-model","choices":[{"index":0,"finish_reason":"stop","message":{"role":"assistant","content":${E2eJson.mapper.writeValueAsString(contentJson)}}}],"usage":{"prompt_tokens":0,"completion_tokens":0,"total_tokens":0}}"""

                val responseBytes = responseBody.toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, responseBytes.size.toLong())
                exchange.responseBody.use { it.write(responseBytes) }
            } catch (ex: Throwable) {
                logger.error("[mock] completion server error", ex)
                exchange.sendResponseHeaders(500, -1)
            }
        }
        server.executor = null
        server.start()
        logger.info("[mock] completion server started on port {}", server.address.port)
        return server
    }

    private fun updateSystemAiConfig(
        state: E2eAdminSession,
        fsProviderId: Long,
        embeddingEndpoint: String?,
        completionEndpoint: String?,
    ) {
        val configBody = linkedMapOf<String, Any?>("fsProviderId" to fsProviderId)
        if (embeddingEndpoint != null) {
            configBody["embeddingModel"] = linkedMapOf(
                "endpoint" to embeddingEndpoint,
                "model" to "test-embedding-model",
                "key" to "test-key",
                "requestFormat" to "JINA",
            )
        }
        if (completionEndpoint != null) {
            configBody["completionModel"] = linkedMapOf(
                "endpoint" to completionEndpoint,
                "model" to "test-completion-model",
                "key" to "test-key",
                "requestFormat" to "OPENAI",
            )
        }
        val response = state.api.put(path = "/api/system/config", json = configBody)
        E2eAssert.status(response, 200, "[config] update AI model config should succeed")
    }

    private fun awaitTaskCompletion(state: E2eAdminSession, taskType: String, expectedCount: Int) {
        val timeoutMillis = TASK_WAIT_TIMEOUT_MILLIS
        val deadline = System.currentTimeMillis() + timeoutMillis
        var lastStatsBody = "[]"
        var pollCount = 0

        while (System.currentTimeMillis() <= deadline) {
            pollCount++
            val statsRows = fetchTaskStats(state)
            val pending = taskCount(statsRows, taskType, "PENDING")
            val running = taskCount(statsRows, taskType, "RUNNING")
            val completed = taskCount(statsRows, taskType, "COMPLETED")
            val failed = taskCount(statsRows, taskType, "FAILED")
            lastStatsBody = statsRows.joinToString(prefix = "[", postfix = "]") { it.toString() }

            if (pending == 0L && running == 0L && (completed + failed) >= expectedCount) {
                assertTrue(
                    failed == 0L,
                    "[$taskType] expected no failed tasks, completed=$completed, failed=$failed, stats=$lastStatsBody",
                )
                return
            }

            Thread.sleep(pollIntervalMillis(pollCount))
        }

        fail("[$taskType] tasks did not complete within ${timeoutMillis}ms, last=$lastStatsBody")
    }

    private fun fetchTaskStats(state: E2eAdminSession): List<JsonNode> {
        val response = state.api.get("/api/task/logs")
        E2eAssert.status(response, 200, "[stats] get task logs should succeed")
        val root = E2eJson.mapper.readTree(response.body())
        assertTrue(root.isArray, "[stats] expected root array")
        return root.toList()
    }

    private fun taskCount(rows: List<JsonNode>, taskType: String, status: String): Long {
        return rows.firstOrNull { row ->
            row.path("taskType").asText() == taskType && row.path("status").asText() == status
        }?.path("count")?.longValue() ?: 0L
    }

    private fun pollIntervalMillis(pollCount: Int): Long {
        return when {
            pollCount <= 20 -> 100L
            pollCount <= 60 -> 200L
            else -> 500L
        }
    }

    private fun resolveSystemFsProviderId(state: E2eAdminSession): Long {
        val response = state.api.get("/api/system/config")
        E2eAssert.status(response, 200, "[prepare] get system config should succeed")
        val fsProviderIdNode = E2eJson.mapper.readTree(response.body()).path("fsProviderId")
        assertTrue(fsProviderIdNode.isIntegralNumber, "[prepare] fsProviderId should be an integer")
        return fsProviderIdNode.longValue()
    }

    private fun countFilesWithExtensions(root: Path, extensions: Set<String>): Int {
        return Files.walk(root).use { paths ->
            paths.filter { Files.isRegularFile(it) }
                .filter { it.fileName.toString().substringAfterLast('.', "").lowercase() in extensions }
                .count()
                .toInt()
        }
    }

    private fun baseUrl(): String = "http://127.0.0.1:$port"

    private data class FixtureInfo(
        val fixtureRoot: Path,
        val albumTitle: String,
    )

    companion object {
        private val logger = LoggerFactory.getLogger(PlaylistGenerateTaskE2eTest::class.java)
        private const val FIXTURE_FILE_COUNT = 3
        private const val EMBEDDING_DIM = 1024
        private const val TASK_WAIT_TIMEOUT_MILLIS = 120_000L
        private val ACCEPT_EXTENSIONS = linkedSetOf("mp3", "wav", "ogg", "flac", "wma", "m4a")

        @JvmStatic
        @DynamicPropertySource
        fun registerDatasource(registry: DynamicPropertyRegistry) {
            E2eRuntime.registerDatasource(registry)
        }
    }
}
