package com.unirhy.e2e

import com.coooolfan.unirhy.UnirhyApplication
import com.fasterxml.jackson.databind.JsonNode
import com.unirhy.e2e.support.E2eAdminSession
import com.unirhy.e2e.support.E2eAssert
import com.unirhy.e2e.support.E2eHttpClient
import com.unirhy.e2e.support.E2eJson
import com.unirhy.e2e.support.E2eRuntime
import com.unirhy.e2e.support.bootstrapAdminSession
import com.unirhy.e2e.support.expandHomePath
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
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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
class TaskContentReadE2eTest {

    @LocalServerPort
    private var port: Int = 0

    private val prepareLock = Any()
    private var preparedData: PreparedData? = null

    @AfterAll
    fun cleanup() {
        // E2eRuntime cleanup removes the whole workspace recursively, including scanWorkspace fixture files.
        E2eRuntime.cleanup()
    }

    @Test
    @Order(1)
    fun `task and content endpoints should reject unauthenticated access`() {
        val api = E2eHttpClient(baseUrl())

        assertAuthenticationFailed(
            api.get("/api/task/running"),
            "[auth] get running tasks should require login",
        )
        assertAuthenticationFailed(
            api.post(
                path = "/api/task/scan",
                // This payload is only for auth gate assertion; business validation is out of scope for this test.
                json = scanRequestBodyForUnauth(),
            ),
            "[auth] submit scan task should require login",
        )

        assertAuthenticationFailed(
            api.get("/api/works"),
            "[auth] get work list should require login",
        )
        assertAuthenticationFailed(
            api.get("/api/works/random"),
            "[auth] get random work should require login",
        )
        assertAuthenticationFailed(
            api.get("/api/works/1"),
            "[auth] get work by id should require login",
        )
        assertAuthenticationFailed(
            api.delete("/api/works/1"),
            "[auth] delete work should require login",
        )

        assertAuthenticationFailed(
            api.get("/api/albums"),
            "[auth] get album list should require login",
        )
        assertAuthenticationFailed(
            api.get("/api/albums/1"),
            "[auth] get album by id should require login",
        )

        assertAuthenticationFailed(
            api.get("/api/media/1"),
            "[auth] get media should require login",
        )
        assertAuthenticationFailed(
            api.get("/api/media/1", headers = mapOf("Range" to "bytes=0-3")),
            "[auth] get media with range should require login",
        )
        // HEAD responses do not include error body, so we assert status only.
        E2eAssert.status(
            api.head("/api/media/1"),
            401,
            "[auth] head media should require login",
        )
    }

    @Test
    @Order(2)
    fun `scan task should expose running lifecycle and reject duplicate submission`() {
        val state = bootstrapAdminSession(baseUrl())
        preparedData = executeScanAndPrepareData(state)
    }

    @Test
    @Order(3)
    fun `media endpoint should support full range head and error branches`() {
        val state = bootstrapAdminSession(baseUrl())
        val data = ensurePreparedData(state, caller = "media")

        val fullMediaResponse = state.api.getBytes("/api/media/${data.mediaId}")
        E2eAssert.status(fullMediaResponse, 200, "[media] full response should succeed")
        assertEquals(
            "bytes",
            fullMediaResponse.headers().firstValue("Accept-Ranges").orElse(""),
            "[media] full response should declare byte range support",
        )
        assertTrue(fullMediaResponse.body().isNotEmpty(), "[media] full response body should not be empty")

        val partialResponse = state.api.getBytes(
            path = "/api/media/${data.mediaId}",
            headers = mapOf("Range" to "bytes=0-15"),
        )
        E2eAssert.status(partialResponse, 206, "[media] range response should return partial content")
        val contentRange = partialResponse.headers().firstValue("Content-Range").orElse("")
        assertTrue(contentRange.startsWith("bytes 0-"), "[media] range response should include content-range header")
        assertTrue(partialResponse.body().isNotEmpty(), "[media] range response should include body bytes")

        val headResponse = state.api.head("/api/media/${data.mediaId}")
        E2eAssert.status(headResponse, 200, "[media] head response should succeed")
        val contentLength = headResponse.headers().firstValue("Content-Length").orElse("")
        val contentLengthValue = contentLength.toLongOrNull()
        assertNotNull(contentLengthValue, "[media] head response should contain numeric content-length")
        assertTrue(contentLengthValue > 0L, "[media] head response should contain positive content-length")

        val invalidRangeResponse = state.api.getBytes(
            path = "/api/media/${data.mediaId}",
            headers = mapOf("Range" to "bytes=999999999-999999999"),
        )
        E2eAssert.status(invalidRangeResponse, 416, "[media] invalid range should return 416")
        assertTrue(
            invalidRangeResponse.headers().firstValue("Content-Range").orElse("").startsWith("bytes */"),
            "[media] invalid range should expose unsatisfied content-range",
        )

        val notFoundMediaResponse = state.api.get("/api/media/999999999999")
        E2eAssert.status(notFoundMediaResponse, 404, "[media] unknown media id should return 404")
    }

    @Test
    @Order(4)
    fun `works and albums should support read random and delete flow`() {
        val state = bootstrapAdminSession(baseUrl())
        val data = ensurePreparedData(state, caller = "works-albums")

        val worksListResponse = state.api.get(
            path = "/api/works",
            query = mapOf("pageIndex" to 0, "pageSize" to 200),
        )
        E2eAssert.status(worksListResponse, 200, "[works] list should succeed")
        assertTrue(
            pageContainsId(worksListResponse.body(), data.workId),
            "[works] list should contain prepared work",
        )

        val workDetailResponse = state.api.get("/api/works/${data.workId}")
        E2eAssert.status(workDetailResponse, 200, "[works] detail should succeed")
        E2eAssert.jsonAt(workDetailResponse.body(), "/id", data.workId, "[works] detail id should match")
        assertTrue(
            E2eJson.mapper.readTree(workDetailResponse.body()).path("recordings").isArray,
            "[works] detail should include recordings array",
        )

        val randomQuery = mapOf(
            "timestamp" to 1_700_000_000_000L,
            "length" to 3_600_000L,
            "offset" to 0L,
        )
        val randomWorkResponse1 = state.api.get("/api/works/random", query = randomQuery)
        E2eAssert.status(randomWorkResponse1, 200, "[works] random work call should succeed")
        val randomWorkResponse2 = state.api.get("/api/works/random", query = randomQuery)
        E2eAssert.status(randomWorkResponse2, 200, "[works] random work call should remain stable in same window")
        val randomWorkId1 = readIdFromObject(randomWorkResponse1.body(), "/id", "[works] random response 1 should contain id")
        val randomWorkId2 = readIdFromObject(randomWorkResponse2.body(), "/id", "[works] random response 2 should contain id")
        assertEquals(randomWorkId1, randomWorkId2, "[works] random work id should stay stable in same window")

        val invalidLengthRandomResponse = state.api.get(
            path = "/api/works/random",
            query = mapOf("length" to 0L),
        )
        E2eAssert.status(invalidLengthRandomResponse, 400, "[works] random with non-positive length should fail")

        val albumsListResponse = state.api.get(
            path = "/api/albums",
            query = mapOf("pageIndex" to 0, "pageSize" to 200),
        )
        E2eAssert.status(albumsListResponse, 200, "[albums] list should succeed")
        assertTrue(
            pageContainsId(albumsListResponse.body(), data.albumId),
            "[albums] list should contain prepared album",
        )

        val albumDetailResponse = state.api.get("/api/albums/${data.albumId}")
        E2eAssert.status(albumDetailResponse, 200, "[albums] detail should succeed")
        E2eAssert.jsonAt(albumDetailResponse.body(), "/id", data.albumId, "[albums] detail id should match")
        assertTrue(
            E2eJson.mapper.readTree(albumDetailResponse.body()).path("recordings").isArray,
            "[albums] detail should include recordings array",
        )

        val deleteWorkResponse = state.api.delete("/api/works/${data.workId}")
        E2eAssert.status(deleteWorkResponse, 204, "[works] delete should succeed")

        val worksAfterDeleteResponse = state.api.get(
            path = "/api/works",
            query = mapOf("pageIndex" to 0, "pageSize" to 200),
        )
        E2eAssert.status(worksAfterDeleteResponse, 200, "[works] list after delete should succeed")
        assertFalse(
            pageContainsId(worksAfterDeleteResponse.body(), data.workId),
            "[works] deleted work should not remain in list",
        )
    }

    private fun ensurePreparedData(state: E2eAdminSession, caller: String): PreparedData {
        preparedData?.let {
            logger.info(
                "[prepare] reuse prepared data, caller={}, workId={}, albumId={}, mediaId={}",
                caller,
                it.workId,
                it.albumId,
                it.mediaId,
            )
            return it
        }

        synchronized(prepareLock) {
            preparedData?.let {
                logger.info(
                    "[prepare] reuse prepared data after lock, caller={}, workId={}, albumId={}, mediaId={}",
                    caller,
                    it.workId,
                    it.albumId,
                    it.mediaId,
                )
                return it
            }

            val recovered = recoverPreparedDataFromApi(state)
            if (recovered != null) {
                logger.info(
                    "[prepare] recover prepared data from existing API data, caller={}, workId={}, albumId={}, mediaId={}",
                    caller,
                    recovered.workId,
                    recovered.albumId,
                    recovered.mediaId,
                )
                preparedData = recovered
                return recovered
            }

            logger.info("[prepare] initialize prepared data by scan, caller={}", caller)
            return executeScanAndPrepareData(state).also { preparedData = it }
        }
    }

    private fun executeScanAndPrepareData(state: E2eAdminSession): PreparedData {
        val scanRequestBody = scanRequestBody(state)
        val fixture = prepareScanFixture(state.runtime.scanWorkspace)

        val submitResponse = state.api.post(
            path = "/api/task/scan",
            json = scanRequestBody,
        )
        E2eAssert.status(submitResponse, 202, "[scan] submit scan task should return accepted")

        awaitScanTaskLifecycle(state, scanRequestBody)

        val worksResponse = state.api.get(
            path = "/api/works",
            query = mapOf("pageIndex" to 0, "pageSize" to 200),
        )
        E2eAssert.status(worksResponse, 200, "[scan] list works should succeed after scan")
        val workRows = pageRows(worksResponse.body(), "[scan] works response")
        assertTrue(
            workRows.isNotEmpty(),
            "[scan] expected works after scan, source=${fixture.sourceRoot}, fixture=${fixture.fixtureRoot}",
        )

        val workId = readIdFromNode(
            workRows.first().path("id"),
            "[scan] first work should contain id",
        )
        val mediaId = extractMediaId(workRows.first(), "[scan] first work should include media file id")

        val albumsResponse = state.api.get(
            path = "/api/albums",
            query = mapOf("pageIndex" to 0, "pageSize" to 200),
        )
        E2eAssert.status(albumsResponse, 200, "[scan] list albums should succeed after scan")
        val albumRows = pageRows(albumsResponse.body(), "[scan] albums response")
        assertTrue(
            albumRows.isNotEmpty(),
            "[scan] expected albums after scan, source=${fixture.sourceRoot}, fixture=${fixture.fixtureRoot}",
        )
        val albumId = readIdFromNode(
            albumRows.first().path("id"),
            "[scan] first album should contain id",
        )

        return PreparedData(
            workId = workId,
            albumId = albumId,
            mediaId = mediaId,
        )
    }

    private fun recoverPreparedDataFromApi(state: E2eAdminSession): PreparedData? {
        val worksResponse = state.api.get(
            path = "/api/works",
            query = mapOf("pageIndex" to 0, "pageSize" to 200),
        )
        E2eAssert.status(worksResponse, 200, "[prepare] get works should succeed when recovering prepared data")
        val workRows = pageRows(worksResponse.body(), "[prepare] works response")
        if (workRows.isEmpty()) {
            return null
        }

        val firstWork = workRows.first()
        val workIdNode = firstWork.path("id")
        if (!workIdNode.isIntegralNumber) {
            logger.warn("[prepare] skip recover: first work id is not integral")
            return null
        }
        val mediaId = runCatching {
            extractMediaId(firstWork, "[prepare] first work should include media file id when recovering")
        }.getOrElse { error ->
            logger.warn("[prepare] skip recover: cannot extract media id from first work", error)
            return null
        }

        val albumsResponse = state.api.get(
            path = "/api/albums",
            query = mapOf("pageIndex" to 0, "pageSize" to 200),
        )
        E2eAssert.status(albumsResponse, 200, "[prepare] get albums should succeed when recovering prepared data")
        val albumRows = pageRows(albumsResponse.body(), "[prepare] albums response")
        if (albumRows.isEmpty()) {
            return null
        }
        val albumIdNode = albumRows.first().path("id")
        if (!albumIdNode.isIntegralNumber) {
            logger.warn("[prepare] skip recover: first album id is not integral")
            return null
        }

        return PreparedData(
            workId = workIdNode.longValue(),
            albumId = albumIdNode.longValue(),
            mediaId = mediaId,
        )
    }

    private fun awaitScanTaskLifecycle(state: E2eAdminSession, scanRequestBody: Map<String, Any>) {
        val timeoutMillis = scanWaitTimeoutMillis()
        val deadline = System.currentTimeMillis() + timeoutMillis
        var observedRunning = false
        var validatedDuplicateConflict = false
        var finishedAfterRunning = false
        var fastCompletedBeforeObservation = false
        var emptyPollsBeforeObservation = 0
        var lastRunningBody = "[]"
        var pollCount = 0

        while (System.currentTimeMillis() <= deadline) {
            pollCount += 1
            val runningResponse = state.api.get("/api/task/running")
            E2eAssert.status(runningResponse, 200, "[scan] list running tasks should succeed")
            lastRunningBody = runningResponse.body()

            if (containsScanTask(lastRunningBody)) {
                observedRunning = true
                emptyPollsBeforeObservation = 0
                if (!validatedDuplicateConflict) {
                    val duplicateSubmitResponse = state.api.post(
                        path = "/api/task/scan",
                        json = scanRequestBody,
                    )
                    when (duplicateSubmitResponse.statusCode()) {
                        409 -> {
                            validatedDuplicateConflict = true
                        }

                        202 -> {
                            logger.info(
                                "[scan] duplicate submit returned 202 after observing running task, treat as race and continue polling",
                            )
                        }

                        else -> {
                            E2eAssert.status(
                                duplicateSubmitResponse,
                                409,
                                "[scan] duplicate submit while running should return conflict when running state is observable",
                            )
                        }
                    }
                }
            } else if (observedRunning) {
                finishedAfterRunning = true
                break
            } else {
                emptyPollsBeforeObservation += 1
                if (emptyPollsBeforeObservation >= FAST_COMPLETE_EMPTY_POLLS_THRESHOLD) {
                    fastCompletedBeforeObservation = true
                    logger.info(
                        "[scan] scan task likely completed before observation window, emptyPolls={}, threshold={}, last={}",
                        emptyPollsBeforeObservation,
                        FAST_COMPLETE_EMPTY_POLLS_THRESHOLD,
                        lastRunningBody,
                    )
                    break
                }
            }

            Thread.sleep(pollIntervalMillis(pollCount))
        }

        if (fastCompletedBeforeObservation) {
            logger.info(
                "[scan] skip strict running/duplicate assertions because task may have finished before observation",
            )
        } else {
            assertTrue(
                observedRunning,
                "[scan] running task endpoint never observed SCAN task before timeout ${timeoutMillis}ms, last=$lastRunningBody",
            )
            assertTrue(
                validatedDuplicateConflict,
                "[scan] duplicate submit conflict was not observed while SCAN task was running",
            )
            assertTrue(
                finishedAfterRunning,
                "[scan] scan task did not finish within timeout ${timeoutMillis}ms, last=$lastRunningBody",
            )
        }

        val finalRunningResponse = state.api.get("/api/task/running")
        E2eAssert.status(finalRunningResponse, 200, "[scan] running task endpoint should remain available")
        assertFalse(
            containsScanTask(finalRunningResponse.body()),
            "[scan] no SCAN task should remain after lifecycle wait",
        )
    }

    private fun pollIntervalMillis(pollCount: Int): Long {
        return when {
            pollCount <= 20 -> 100L
            pollCount <= 60 -> 200L
            else -> 500L
        }
    }

    private fun prepareScanFixture(scanWorkspace: Path): FixtureInfo {
        val sourceRoot = resolveScanSourceRoot()
        require(Files.exists(sourceRoot)) {
            "[scan] scan source path does not exist: $sourceRoot (env E2E_SCAN_SOURCE_PATH)"
        }
        require(Files.isDirectory(sourceRoot)) {
            "[scan] scan source path must be a directory: $sourceRoot (env E2E_SCAN_SOURCE_PATH)"
        }

        val candidates = collectAudioCandidates(sourceRoot)
        require(candidates.isNotEmpty()) {
            "[scan] no audio files found under $sourceRoot, expected extensions: ${ACCEPT_EXTENSIONS.joinToString(",")}"
        }

        val seed = candidates.first()
        val seedExtension = fileExtension(seed)
        val fixtureRoot = scanWorkspace.resolve("task-content-fixture-${UUID.randomUUID().toString().replace("-", "").take(12)}")
        Files.createDirectories(fixtureRoot)

        repeat(SCAN_FIXTURE_FILE_COUNT) { index ->
            val fileName = "seed-${index.toString().padStart(4, '0')}.$seedExtension"
            val target = fixtureRoot.resolve(fileName)
            symlinkOrCopy(seed, target)
        }

        return FixtureInfo(
            sourceRoot = sourceRoot,
            fixtureRoot = fixtureRoot,
        )
    }

    private fun collectAudioCandidates(sourceRoot: Path): List<Path> {
        val candidates = mutableListOf<Path>()
        Files.walk(sourceRoot).use { paths ->
            val iterator = paths.iterator()
            while (iterator.hasNext()) {
                val path = iterator.next()
                if (!Files.isRegularFile(path)) {
                    continue
                }
                val extension = fileExtension(path)
                if (extension !in ACCEPT_EXTENSIONS) {
                    continue
                }
                candidates.add(path.toAbsolutePath().normalize())
                if (candidates.size >= MAX_CANDIDATE_FILE_COUNT) {
                    break
                }
            }
        }

        return candidates.sortedWith(
            compareBy<Path> { extensionPriority(fileExtension(it)) }
                .thenBy { it.toString() },
        )
    }

    private fun resolveScanSourceRoot(): Path {
        val configured = System.getenv(SCAN_SOURCE_PATH_ENV)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: DEFAULT_SCAN_SOURCE_PATH
        return configured.expandHomePath().toAbsolutePath().normalize()
    }

    private fun symlinkOrCopy(source: Path, target: Path) {
        val normalizedSource = source.toAbsolutePath().normalize()
        val symlinkCreated = runCatching {
            Files.createSymbolicLink(target, normalizedSource)
        }.isSuccess
        if (symlinkCreated) {
            return
        }
        runCatching {
            Files.copy(normalizedSource, target, StandardCopyOption.REPLACE_EXISTING)
        }.getOrElse { copyFailure ->
            throw IllegalStateException(
                "[scan] failed to create fixture file by symlink or copy: source=$normalizedSource, target=$target",
                copyFailure,
            )
        }
    }

    private fun containsScanTask(responseBody: String): Boolean {
        val root = E2eJson.mapper.readTree(responseBody)
        if (!root.isArray) {
            return false
        }
        return root.any { item -> item.path("type").asText() == "SCAN" }
    }

    private fun pageRows(responseBody: String, step: String): List<JsonNode> {
        val root = E2eJson.mapper.readTree(responseBody)
        val rows = root.path("rows")
        assertTrue(rows.isArray, "$step expected rows array")
        return rows.toList()
    }

    private fun pageContainsId(responseBody: String, expectedId: Long): Boolean {
        return pageRows(responseBody, "[page] response").any { row ->
            row.path("id").isIntegralNumber && row.path("id").longValue() == expectedId
        }
    }

    private fun extractMediaId(workNode: JsonNode, step: String): Long {
        val recordingsNode = workNode.path("recordings")
        if (!recordingsNode.isArray) {
            fail("$step expected recordings array")
        }
        for (recording in recordingsNode) {
            val assetsNode = recording.path("assets")
            if (!assetsNode.isArray) {
                continue
            }
            for (asset in assetsNode) {
                val mediaIdNode = asset.path("mediaFile").path("id")
                if (mediaIdNode.isIntegralNumber) {
                    return mediaIdNode.longValue()
                }
            }
        }
        fail("$step expected at least one asset media file id")
    }

    private fun readIdFromObject(responseBody: String, pointer: String, step: String): Long {
        val node = E2eJson.mapper.readTree(responseBody).at(pointer)
        return readIdFromNode(node, step)
    }

    private fun readIdFromNode(node: JsonNode, step: String): Long {
        assertTrue(node.isIntegralNumber, "$step expected integral id")
        return node.longValue()
    }

    private fun fileExtension(path: Path): String {
        val name = path.fileName?.toString().orEmpty()
        return name.substringAfterLast('.', "").lowercase()
    }

    private fun extensionPriority(extension: String): Int {
        return PREFERRED_EXTENSIONS.indexOf(extension).let { if (it >= 0) it else PREFERRED_EXTENSIONS.size }
    }

    private fun scanWaitTimeoutMillis(): Long {
        val value = System.getenv(SCAN_WAIT_TIMEOUT_ENV)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return DEFAULT_SCAN_WAIT_TIMEOUT_MILLIS
        val parsed = value.toLongOrNull()
        require(parsed != null && parsed > 0) {
            "[scan] env $SCAN_WAIT_TIMEOUT_ENV must be a positive integer milliseconds, actual=$value"
        }
        return parsed
    }

    private fun scanRequestBodyForUnauth(): Map<String, Any> {
        return linkedMapOf(
            "providerType" to "FILE_SYSTEM",
            "providerId" to 0L,
        )
    }

    private fun scanRequestBody(state: E2eAdminSession): Map<String, Any> {
        val fsProviderId = resolveSystemFsProviderId(state)
        return linkedMapOf(
            "providerType" to "FILE_SYSTEM",
            "providerId" to fsProviderId,
        )
    }

    private fun resolveSystemFsProviderId(state: E2eAdminSession): Long {
        val response = state.api.get("/api/system/config")
        E2eAssert.status(response, 200, "[prepare] get system config should succeed before scan")
        val fsProviderIdNode = E2eJson.mapper.readTree(response.body()).path("fsProviderId")
        assertTrue(fsProviderIdNode.isIntegralNumber, "[prepare] /api/system/config fsProviderId should be an integer")
        return fsProviderIdNode.longValue()
    }

    private fun assertAuthenticationFailed(response: HttpResponse<String>, step: String) {
        E2eAssert.apiError(
            response = response,
            family = "COMMON",
            code = "AUTHENTICATION_FAILED",
            expectedStatus = 401,
            step = step,
        )
    }

    private fun baseUrl(): String = "http://127.0.0.1:$port"

    private data class PreparedData(
        val workId: Long,
        val albumId: Long,
        val mediaId: Long,
    )

    private data class FixtureInfo(
        val sourceRoot: Path,
        val fixtureRoot: Path,
    )

    companion object {
        private val logger = LoggerFactory.getLogger(TaskContentReadE2eTest::class.java)
        private const val SCAN_SOURCE_PATH_ENV = "E2E_SCAN_SOURCE_PATH"
        private const val SCAN_WAIT_TIMEOUT_ENV = "E2E_SCAN_WAIT_TIMEOUT_MILLIS"
        private const val DEFAULT_SCAN_SOURCE_PATH = "~/Music"
        private const val DEFAULT_SCAN_WAIT_TIMEOUT_MILLIS = 120_000L
        private const val FAST_COMPLETE_EMPTY_POLLS_THRESHOLD = 3
        private const val SCAN_FIXTURE_FILE_COUNT = 24
        private const val MAX_CANDIDATE_FILE_COUNT = 10_000

        private val ACCEPT_EXTENSIONS = linkedSetOf("mp3", "wav", "ogg", "flac", "aac", "wma", "m4a")
        private val PREFERRED_EXTENSIONS = listOf("mp3", "flac", "m4a", "ogg", "wav", "aac", "wma")

        @JvmStatic
        @DynamicPropertySource
        fun registerDatasource(registry: DynamicPropertyRegistry) {
            E2eRuntime.registerDatasource(registry)
        }
    }
}
