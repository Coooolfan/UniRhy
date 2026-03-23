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
class DataCleanTaskE2eTest {

    @LocalServerPort
    private var port: Int = 0

    private var mockChatServer: HttpServer? = null

    @AfterAll
    fun cleanup() {
        mockChatServer?.stop(0)
        E2eRuntime.cleanup()
    }

    @Test
    @Order(1)
    fun `data-clean endpoint should reject unauthenticated access`() {
        val api = com.unirhy.e2e.support.E2eHttpClient(baseUrl())
        E2eAssert.apiError(
            response = api.post(
                path = "/api/task/data-clean",
                json = dataCleanRequestBodyForUnauth(),
            ),
            family = "COMMON",
            code = "AUTHENTICATION_FAILED",
            expectedStatus = 401,
            step = "[auth] submit data-clean task should require login",
        )
    }

    @Test
    @Order(2)
    fun `data-clean task should complete successfully with mock chat api`() {
        val state = bootstrapAdminSession(baseUrl())

        // 1. Generate fixtures with title metadata
        val fixture = prepareScanFixtureWithTitle(state.runtime.scanWorkspace)
        val expectedFileCount = countFilesWithExtensions(fixture.fixtureRoot, ACCEPT_EXTENSIONS)
        assertTrue(expectedFileCount > 0, "[data-clean] fixture should contain audio files")

        // 2. Submit scan task and wait for completion
        val fsProviderId = resolveSystemFsProviderId(state)
        val scanRequestBody = linkedMapOf<String, Any>(
            "providerType" to "FILE_SYSTEM",
            "providerId" to fsProviderId,
        )
        val scanResponse = state.api.post(path = "/api/task/scan", json = scanRequestBody)
        E2eAssert.status(scanResponse, 202, "[data-clean] submit scan task should return accepted")
        awaitTaskCompletion(state, "METADATA_PARSE", expectedFileCount)

        // 3. Start mock chat completion API server
        val server = startMockChatCompletionServer()
        mockChatServer = server
        val mockPort = server.address.port

        // 4. Submit data-clean task
        val dataCleanRequestBody = linkedMapOf<String, Any>(
            "srcProviderType" to "FILE_SYSTEM",
            "srcProviderId" to fsProviderId,
            "apiEndpoint" to "http://127.0.0.1:$mockPort/v1/chat/completions",
            "apiKey" to "test-key",
            "modelName" to "test-model",
        )
        val dataCleanResponse = state.api.post(path = "/api/task/data-clean", json = dataCleanRequestBody)
        E2eAssert.status(dataCleanResponse, 202, "[data-clean] submit data-clean task should return accepted")

        // 5. Wait for data-clean tasks to complete
        awaitTaskCompletion(state, "DATA_CLEAN", expectedFileCount)

        logger.info("[data-clean] all data-clean tasks completed successfully, count={}", expectedFileCount)
    }

    private fun prepareScanFixtureWithTitle(scanWorkspace: Path): FixtureInfo {
        val suffix = UUID.randomUUID().toString().replace("-", "").take(12)
        val albumTitle = "e2e-dataclean-album-$suffix"
        val metadata = AudioFixtureMetadata(
            title = "e2e-dataclean-track（测试版）",
            artist = "e2e-dataclean-artist",
            album = albumTitle,
            comment = "e2e-dataclean-fixture",
            lyrics = "placeholder lyrics for scan",
        )
        val batch = SyntheticAudioFixture.generateBatch(
            scanWorkspace = scanWorkspace,
            dirName = "dataclean-fixture-$suffix",
            count = FIXTURE_FILE_COUNT,
            extension = "mp3",
            metadata = metadata,
        )
        return FixtureInfo(fixtureRoot = batch.fixtureRoot, albumTitle = albumTitle)
    }

    private fun startMockChatCompletionServer(): HttpServer {
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/v1/chat/completions") { exchange ->
            try {
                val requestBody = exchange.requestBody.bufferedReader().readText()
                val requestJson = E2eJson.mapper.readTree(requestBody)
                val content = requestJson["messages"]?.get(0)?.get("content")?.asText() ?: ""

                // Extract items from <item>...</item> tags
                val itemPattern = Regex("<item>(.*?)</item>")
                val items = itemPattern.findAll(content).map { it.groupValues[1] }.toList()

                // Generate rules: for test, strip parenthetical suffixes
                val rules = items.map { title ->
                    val cleaned = title.replace(Regex("[（(].*?[）)]"), "").trim()
                    """{"before":"${escapeJson(title)}","after":"${escapeJson(cleaned)}"}"""
                }

                val rulesJson = rules.joinToString(",")
                val contentJson = """{"rules":[$rulesJson]}"""
                val responseBody = """{"id":"test","object":"chat.completion","created":0,"model":"test-model","choices":[{"index":0,"finish_reason":"stop","message":{"role":"assistant","content":${E2eJson.mapper.writeValueAsString(contentJson)}}}],"usage":{"prompt_tokens":0,"completion_tokens":0,"total_tokens":0}}"""

                val responseBytes = responseBody.toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, responseBytes.size.toLong())
                exchange.responseBody.use { it.write(responseBytes) }
            } catch (ex: Throwable) {
                logger.error("[mock] chat completion server error", ex)
                exchange.sendResponseHeaders(500, -1)
            }
        }
        server.executor = null
        server.start()
        logger.info("[mock] chat completion server started on port {}", server.address.port)
        return server
    }

    private fun escapeJson(s: String): String {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
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

    private fun dataCleanRequestBodyForUnauth(): Map<String, Any> {
        return linkedMapOf(
            "srcProviderType" to "FILE_SYSTEM",
            "srcProviderId" to 0L,
            "apiEndpoint" to "http://localhost:9999/v1/chat/completions",
            "apiKey" to "fake",
            "modelName" to "fake",
        )
    }

    private fun baseUrl(): String = "http://127.0.0.1:$port"

    private data class FixtureInfo(
        val fixtureRoot: Path,
        val albumTitle: String,
    )

    companion object {
        private val logger = LoggerFactory.getLogger(DataCleanTaskE2eTest::class.java)
        private const val FIXTURE_FILE_COUNT = 3
        private const val TASK_WAIT_TIMEOUT_MILLIS = 120_000L
        private val ACCEPT_EXTENSIONS = linkedSetOf("mp3", "wav", "ogg", "flac", "wma", "m4a")

        @JvmStatic
        @DynamicPropertySource
        fun registerDatasource(registry: DynamicPropertyRegistry) {
            E2eRuntime.registerDatasource(registry)
        }
    }
}
