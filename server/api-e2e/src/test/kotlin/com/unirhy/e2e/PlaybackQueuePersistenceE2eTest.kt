package com.unirhy.e2e

import com.coooolfan.unirhy.UnirhyApplication
import com.coooolfan.unirhy.sync.config.PlaybackSyncWebSocketConfig
import com.fasterxml.jackson.databind.JsonNode
import com.unirhy.e2e.support.AudioFixtureMetadata
import com.unirhy.e2e.support.E2eAdminSession
import com.unirhy.e2e.support.E2eAssert
import com.unirhy.e2e.support.E2eHttpClient
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
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpResponse
import java.net.http.WebSocket
import java.nio.file.Files
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
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
class PlaybackQueuePersistenceE2eTest {

    @LocalServerPort
    private var port: Int = 0

    @AfterAll
    fun cleanup() {
        E2eRuntime.cleanup()
    }

    @Test
    @Order(1)
    fun `current queue endpoints should reject unauthenticated access`() {
        val api = E2eHttpClient(baseUrl())

        assertAuthenticationFailed(
            api.get("/api/playback/current-queue"),
            "[auth] get current queue should require login",
        )
        assertAuthenticationFailed(
            api.put(
                path = "/api/playback/current-queue",
                json = mapOf(
                    "recordingIds" to listOf(1L, 2L),
                    "currentIndex" to 0,
                ),
            ),
            "[auth] replace current queue should require login",
        )
    }

    @Test
    @Order(2)
    fun `current queue should survive a fresh application boot`() {
        val adminState = bootstrapAdminSession(baseUrl())
        val recordingIds = prepareQueueRecordingIds(adminState)
        val suffix = suffix()
        val account = createAccountByAdmin(
            state = adminState,
            name = "queue-user-$suffix",
            email = "queue-user-$suffix@example.invalid",
            password = "queue-user-$suffix-password",
        )
        val accountSession = loginAccount(baseUrl(), account.email, account.password)
        val accountApi = accountSession.api

        val replaceResponse = accountApi.put(
            path = "/api/playback/current-queue",
            json = mapOf(
                "recordingIds" to recordingIds,
                "currentIndex" to 1,
            ),
        )
        E2eAssert.status(replaceResponse, 200, "[queue] replace current queue should succeed")
        val initialQueue = E2eJson.mapper.readTree(replaceResponse.body())
        assertQueueMatchesRecordingIds(
            queueNode = initialQueue,
            recordingIds = recordingIds,
            expectedCurrentIndex = 1,
            step = "[queue] initial queue should match replaced recording order",
        )
        establishPausedPlaybackResumeState(
            baseUrl = baseUrl(),
            token = accountSession.token,
            deviceId = "web-e2e-initial",
            recordingId = recordingIds[1],
            positionSeconds = 37.25,
        )

        startFreshApplication().use { restarted ->
            val restartedSession = loginAccount(restarted.baseUrl, account.email, account.password)
            val restartedApi = restartedSession.api
            val restoredResponse = restartedApi.get("/api/playback/current-queue")
            E2eAssert.status(restoredResponse, 200, "[queue] restored queue should be readable after fresh boot")
            val restoredQueue = E2eJson.mapper.readTree(restoredResponse.body())

            assertQueueMatchesRecordingIds(
                queueNode = restoredQueue,
                recordingIds = recordingIds,
                expectedCurrentIndex = 1,
                step = "[queue] restored queue should preserve item order and current entry",
            )
            assertEquals(
                initialQueue.path("version").longValue(),
                restoredQueue.path("version").longValue(),
                "[queue] restored queue should preserve version",
            )
            assertEquals(
                initialQueue.path("updatedAtMs").longValue(),
                restoredQueue.path("updatedAtMs").longValue(),
                "[queue] restored queue should preserve updatedAtMs",
            )

            openPlaybackSync(
                baseUrl = restarted.baseUrl,
                token = restartedSession.token,
                deviceId = "web-e2e-restored",
            ).use { playbackSync ->
                val snapshot = playbackSync.awaitMessage("SNAPSHOT", "[queue] fresh boot playback snapshot")
                val state = snapshot.path("payload").path("state")
                val snapshotQueue = snapshot.path("payload").path("queue")

                assertEquals("PAUSED", state.path("status").asText(), "[queue] restored playback snapshot should downgrade to paused")
                assertEquals(recordingIds[1], state.path("recordingId").longValue(), "[queue] restored playback snapshot should preserve current recording")
                assertEquals(37.25, state.path("positionSeconds").doubleValue(), 0.0001, "[queue] restored playback snapshot should preserve last known position")
                assertEquals(0L, state.path("serverTimeToExecuteMs").longValue(), "[queue] restored paused snapshot should reset execute time")
                assertQueueMatchesRecordingIds(
                    queueNode = snapshotQueue,
                    recordingIds = recordingIds,
                    expectedCurrentIndex = 1,
                    step = "[queue] restored playback snapshot should include persisted queue",
                )
            }
        }
    }

    @Test
    @Order(3)
    fun `playback sync should coordinate play across two instances`() {
        val adminState = bootstrapAdminSession(baseUrl())
        val recordingIds = prepareQueueRecordingIds(adminState)
        val suffix = suffix()
        val account = createAccountByAdmin(
            state = adminState,
            name = "sync-user-$suffix",
            email = "sync-user-$suffix@example.invalid",
            password = "sync-user-$suffix-password",
        )
        val primarySession = loginAccount(baseUrl(), account.email, account.password)
        val primaryDeviceId = "web-primary-$suffix"
        val secondaryDeviceId = "web-secondary-$suffix"
        val recordingId = recordingIds.first()
        val commandId = "play-cross-node-$suffix"

        startFreshApplication(nodeId = "api-e2e-playback-secondary-$suffix").use { secondary ->
            val secondarySession = loginAccount(secondary.baseUrl, account.email, account.password)

            openPlaybackSync(
                baseUrl = baseUrl(),
                token = primarySession.token,
                deviceId = primaryDeviceId,
            ).use { primaryPlayback ->
                primaryPlayback.awaitMessage("SNAPSHOT", "[sync] primary playback snapshot")

                openPlaybackSync(
                    baseUrl = secondary.baseUrl,
                    token = secondarySession.token,
                    deviceId = secondaryDeviceId,
                ).use { secondaryPlayback ->
                    secondaryPlayback.awaitMessage("SNAPSHOT", "[sync] secondary playback snapshot")

                    val expectedDevices = listOf(primaryDeviceId, secondaryDeviceId).sorted()
                    assertEquals(
                        expectedDevices,
                        deviceIdsFromMessage(
                            primaryPlayback.awaitMessage(
                                type = "ROOM_EVENT_DEVICE_CHANGE",
                                step = "[sync] primary should observe cross-node device list",
                            ) { message ->
                                deviceIdsFromMessage(message) == expectedDevices
                            },
                        ),
                    )
                    assertEquals(
                        expectedDevices,
                        deviceIdsFromMessage(
                            secondaryPlayback.awaitMessage(
                                type = "ROOM_EVENT_DEVICE_CHANGE",
                                step = "[sync] secondary should observe cross-node device list",
                            ) { message ->
                                deviceIdsFromMessage(message) == expectedDevices
                            },
                        ),
                    )

                    primaryPlayback.sendJson(
                        mapOf(
                            "type" to "PLAY",
                            "payload" to mapOf(
                                "commandId" to commandId,
                                "deviceId" to primaryDeviceId,
                                "recordingId" to recordingId,
                                "positionSeconds" to 12.5,
                            ),
                        ),
                    )

                    val queueChange = secondaryPlayback.awaitMessage(
                        type = "ROOM_EVENT_QUEUE_CHANGE",
                        step = "[sync] secondary should receive queue change from primary instance",
                    ) { message ->
                        message.path("payload")
                            .path("queue")
                            .path("items")
                            .firstOrNull()
                            ?.path("recordingId")
                            ?.longValue() == recordingId
                    }
                    val queueItems = queueChange.path("payload").path("queue").path("items")
                    assertEquals(1, queueItems.size(), "[sync] queue change should expose one current item")
                    assertEquals(recordingId, queueItems.first().path("recordingId").longValue(), "[sync] queue change should target played recording")

                    val loadAudioSource = secondaryPlayback.awaitMessage(
                        type = "ROOM_EVENT_LOAD_AUDIO_SOURCE",
                        step = "[sync] secondary should receive load-audio-source from primary instance",
                    ) { message ->
                        message.path("payload").path("commandId").asText() == commandId
                    }
                    assertEquals(commandId, loadAudioSource.path("payload").path("commandId").asText())
                    assertEquals(recordingId, loadAudioSource.path("payload").path("recordingId").longValue())

                    secondaryPlayback.sendJson(
                        mapOf(
                            "type" to "AUDIO_SOURCE_LOADED",
                            "payload" to mapOf(
                                "commandId" to commandId,
                                "deviceId" to secondaryDeviceId,
                                "recordingId" to recordingId,
                                "mediaFileId" to 0L,
                            ),
                        ),
                    )

                    val primaryScheduledAction = primaryPlayback.awaitMessage(
                        type = "SCHEDULED_ACTION",
                        step = "[sync] primary should receive scheduled action after remote load completes",
                    ) { message ->
                        message.path("payload").path("commandId").asText() == commandId
                    }
                    val secondaryScheduledAction = secondaryPlayback.awaitMessage(
                        type = "SCHEDULED_ACTION",
                        step = "[sync] secondary should receive scheduled action after remote load completes",
                    ) { message ->
                        message.path("payload").path("commandId").asText() == commandId
                    }

                    assertScheduledPlay(primaryScheduledAction, commandId, recordingId, "[sync] primary scheduled action")
                    assertScheduledPlay(secondaryScheduledAction, commandId, recordingId, "[sync] secondary scheduled action")
                    assertEquals(
                        primaryScheduledAction.path("payload").path("serverTimeToExecuteMs").longValue(),
                        secondaryScheduledAction.path("payload").path("serverTimeToExecuteMs").longValue(),
                        "[sync] both instances should emit the same execute time",
                    )
                    assertEquals(
                        primaryScheduledAction.path("payload").path("scheduledAction").path("version").longValue(),
                        secondaryScheduledAction.path("payload").path("scheduledAction").path("version").longValue(),
                        "[sync] both instances should emit the same playback version",
                    )
                }
            }
        }
    }

    private fun prepareQueueRecordingIds(state: E2eAdminSession): List<Long> {
        val suffix = suffix()
        val fixtureRoot = state.runtime.scanWorkspace.resolve("queue-persistence-$suffix")
        Files.createDirectories(fixtureRoot)
        val firstTitle = "queue-persistence-a-$suffix"
        val secondTitle = "queue-persistence-b-$suffix"
        val albumTitle = "queue-persistence-album-$suffix"

        SyntheticAudioFixture.generateOne(
            outputDir = fixtureRoot,
            fileName = "queue-persistence-a.mp3",
            metadata = AudioFixtureMetadata(
                title = firstTitle,
                artist = "queue-persistence-artist-$suffix",
                album = albumTitle,
                comment = "queue-persistence-fixture-$suffix",
            ),
        )
        SyntheticAudioFixture.generateOne(
            outputDir = fixtureRoot,
            fileName = "queue-persistence-b.mp3",
            metadata = AudioFixtureMetadata(
                title = secondTitle,
                artist = "queue-persistence-artist-$suffix",
                album = albumTitle,
                comment = "queue-persistence-fixture-$suffix",
            ),
        )

        submitScanAndAwaitCompletion(state, expectedTaskCount = 2)
        return listOf(
            findSingleRecordingIdByWorkTitle(state, firstTitle, "[queue] first queue fixture work"),
            findSingleRecordingIdByWorkTitle(state, secondTitle, "[queue] second queue fixture work"),
        )
    }

    private fun submitScanAndAwaitCompletion(state: E2eAdminSession, expectedTaskCount: Int) {
        val baselineStats = fetchTaskStats(state, "[queue] baseline task stats")
        val baselinePending = taskCount(baselineStats, "METADATA_PARSE", "PENDING")
        val baselineCompleted = taskCount(baselineStats, "METADATA_PARSE", "COMPLETED")
        val baselineFailed = taskCount(baselineStats, "METADATA_PARSE", "FAILED")

        val submitResponse = state.api.post(
            path = "/api/task/scan",
            json = scanRequestBody(state),
        )
        E2eAssert.status(submitResponse, 202, "[queue] submit scan task should return accepted")

        val deadline = System.currentTimeMillis() + SCAN_WAIT_TIMEOUT_MILLIS
        var lastStatsBody = "[]"

        while (System.currentTimeMillis() <= deadline) {
            val statsRows = fetchTaskStats(state, "[queue] task stats")
            val pending = taskCount(statsRows, "METADATA_PARSE", "PENDING")
            val completed = taskCount(statsRows, "METADATA_PARSE", "COMPLETED")
            val failed = taskCount(statsRows, "METADATA_PARSE", "FAILED")
            val terminalDelta = (completed - baselineCompleted) + (failed - baselineFailed)
            lastStatsBody = statsRows.joinToString(prefix = "[", postfix = "]") { it.toString() }

            if (pending <= baselinePending && terminalDelta >= expectedTaskCount) {
                assertEquals(
                    0L,
                    failed - baselineFailed,
                    "[queue] scan should not introduce failed tasks, last=$lastStatsBody",
                )
                return
            }

            Thread.sleep(POLL_INTERVAL_MILLIS)
        }

        fail("[queue] scan task did not finish within timeout $SCAN_WAIT_TIMEOUT_MILLIS ms, last=$lastStatsBody")
    }

    private fun fetchTaskStats(state: E2eAdminSession, step: String): List<JsonNode> {
        val response = state.api.get("/api/task/logs")
        E2eAssert.status(response, 200, "$step should succeed")
        val root = E2eJson.mapper.readTree(response.body())
        assertTrue(root.isArray, "$step expected root array")
        return root.toList()
    }

    private fun taskCount(rows: List<JsonNode>, taskType: String, status: String): Long {
        return rows.firstOrNull { row ->
            row.path("taskType").asText() == taskType && row.path("status").asText() == status
        }?.path("count")?.longValue() ?: 0L
    }

    private fun scanRequestBody(state: E2eAdminSession): Map<String, Any> {
        return mapOf(
            "providerType" to "FILE_SYSTEM",
            "providerId" to resolveSystemFsProviderId(state),
        )
    }

    private fun resolveSystemFsProviderId(state: E2eAdminSession): Long {
        val response = state.api.get("/api/system/config")
        E2eAssert.status(response, 200, "[queue] get system config should succeed")
        val fsProviderIdNode = E2eJson.mapper.readTree(response.body()).path("fsProviderId")
        assertTrue(fsProviderIdNode.isIntegralNumber, "[queue] fsProviderId should be integral")
        return fsProviderIdNode.longValue()
    }

    private fun findSingleRecordingIdByWorkTitle(state: E2eAdminSession, workTitle: String, step: String): Long {
        val response = state.api.get(
            path = "/api/works/search",
            query = mapOf("name" to workTitle),
        )
        E2eAssert.status(response, 200, "$step search should succeed")
        val root = E2eJson.mapper.readTree(response.body())
        assertTrue(root.isArray, "$step expected array response")
        val workNode = root.firstOrNull { node -> node.path("title").asText() == workTitle }
            ?: fail("$step expected work title=$workTitle")
        val recordings = workNode.path("recordings")
        assertTrue(recordings.isArray, "$step expected recordings array")
        assertEquals(1, recordings.size(), "$step should expose exactly one recording")
        val recordingIdNode = recordings.first().path("id")
        assertTrue(recordingIdNode.isIntegralNumber, "$step expected integral recording id")
        return recordingIdNode.longValue()
    }

    private fun assertQueueMatchesRecordingIds(
        queueNode: JsonNode,
        recordingIds: List<Long>,
        expectedCurrentIndex: Int,
        step: String,
    ) {
        val items = queueNode.path("items")
        assertTrue(items.isArray, "$step expected items array")
        assertEquals(recordingIds.size, items.size(), "$step item count should match requested recordings")
        assertEquals(
            recordingIds,
            items.map { item -> item.path("recordingId").longValue() },
            "$step recording order should match",
        )
        val expectedCurrentEntryId = items[expectedCurrentIndex].path("entryId").longValue()
        assertEquals(
            expectedCurrentEntryId,
            queueNode.path("currentEntryId").longValue(),
            "$step currentEntryId should point at requested current index",
        )
        assertEquals(
            "SEQUENTIAL",
            queueNode.path("playbackStrategy").asText(),
            "$step playback strategy should reset to sequential",
        )
        assertEquals(
            "LIST",
            queueNode.path("stopStrategy").asText(),
            "$step stop strategy should reset to list",
        )
        assertTrue(queueNode.path("version").longValue() > 0L, "$step version should be positive")
        assertTrue(queueNode.path("updatedAtMs").longValue() > 0L, "$step updatedAtMs should be positive")
    }

    private fun assertScheduledPlay(
        message: JsonNode,
        commandId: String,
        recordingId: Long,
        step: String,
    ) {
        assertEquals("SCHEDULED_ACTION", message.path("type").asText(), "$step should be a scheduled action message")
        assertEquals(commandId, message.path("payload").path("commandId").asText(), "$step commandId should match")
        assertEquals("PLAY", message.path("payload").path("scheduledAction").path("action").asText(), "$step action should be PLAY")
        assertEquals("PLAYING", message.path("payload").path("scheduledAction").path("status").asText(), "$step status should be PLAYING")
        assertEquals(recordingId, message.path("payload").path("scheduledAction").path("recordingId").longValue(), "$step recordingId should match")
        assertEquals(12.5, message.path("payload").path("scheduledAction").path("positionSeconds").doubleValue(), 0.0001, "$step positionSeconds should match")
    }

    private fun deviceIdsFromMessage(message: JsonNode): List<String> {
        return message.path("payload")
            .path("devices")
            .map { it.path("deviceId").asText() }
            .sorted()
    }

    private fun createAccountByAdmin(
        state: E2eAdminSession,
        name: String,
        email: String,
        password: String,
    ): CreatedAccount {
        val createResponse = state.api.post(
            path = "/api/accounts",
            json = mapOf(
                "name" to name,
                "email" to email,
                "password" to password,
            ),
        )
        E2eAssert.status(createResponse, 201, "[queue] create account should succeed")
        return CreatedAccount(
            id = readIdFromObject(createResponse.body(), "/id", "[queue] created account should expose id"),
            email = email,
            password = password,
        )
    }

    private fun loginAccount(baseUrl: String, email: String, password: String): AuthenticatedAccount {
        val api = E2eHttpClient(baseUrl)
        val loginResponse = api.post(
            path = "/api/tokens",
            json = mapOf(
                "email" to email,
                "password" to password,
            ),
        )
        E2eAssert.status(loginResponse, 200, "[queue] account login should succeed")
        val token = E2eJson.mapper.readTree(loginResponse.body()).path("token").asText()
        api.setAuthToken(token)
        return AuthenticatedAccount(api = api, token = token)
    }

    private fun loginAsAccount(baseUrl: String, email: String, password: String): E2eHttpClient {
        return loginAccount(baseUrl, email, password).api
    }

    private fun establishPausedPlaybackResumeState(
        baseUrl: String,
        token: String,
        deviceId: String,
        recordingId: Long,
        positionSeconds: Double,
    ) {
        openPlaybackSync(baseUrl, token, deviceId).use { playbackSync ->
            playbackSync.awaitMessage("SNAPSHOT", "[queue] initial playback snapshot")
            playbackSync.sendJson(
                mapOf(
                    "type" to "PAUSE",
                    "payload" to mapOf(
                        "commandId" to "pause-${System.currentTimeMillis()}",
                        "deviceId" to deviceId,
                        "recordingId" to recordingId,
                        "positionSeconds" to positionSeconds,
                    ),
                ),
            )
            val scheduledAction = playbackSync.awaitMessage("SCHEDULED_ACTION", "[queue] pause should persist playback resume state")
            assertEquals("PAUSED", scheduledAction.path("payload").path("scheduledAction").path("status").asText())
            assertEquals(recordingId, scheduledAction.path("payload").path("scheduledAction").path("recordingId").longValue())
        }
    }

    private fun openPlaybackSync(
        baseUrl: String,
        token: String,
        deviceId: String,
    ): PlaybackSyncProbe {
        val listener = PlaybackSyncListener()
        val webSocket = HttpClient.newHttpClient()
            .newWebSocketBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .buildAsync(
                URI.create(baseUrl.replaceFirst("http", "ws") + PlaybackSyncWebSocketConfig.PLAYBACK_SYNC_PATH),
                listener,
            )
            .join()
        listener.sendJson(
            webSocket = webSocket,
            json = mapOf(
                "type" to "HELLO",
                "payload" to mapOf(
                    "deviceId" to deviceId,
                    "clientVersion" to "e2e@playback-resume",
                    "token" to token,
                ),
            ),
        )
        return PlaybackSyncProbe(webSocket, listener)
    }

    private fun readIdFromObject(responseBody: String, pointer: String, step: String): Long {
        val node = E2eJson.mapper.readTree(responseBody).at(pointer)
        assertTrue(node.isIntegralNumber, "$step expected integral id")
        return node.longValue()
    }

    private fun startFreshApplication(nodeId: String = "api-e2e-playback-secondary"): FreshAppHandle {
        val runtime = E2eRuntime.context
        val context = SpringApplicationBuilder(UnirhyApplication::class.java)
            .run(
                "--server.port=0",
                "--spring.datasource.url=${runtime.database.jdbcUrl}",
                "--spring.datasource.username=${runtime.database.user}",
                "--spring.datasource.password=${runtime.database.password}",
                "--spring.flyway.url=${runtime.database.jdbcUrl}",
                "--spring.flyway.user=${runtime.database.user}",
                "--spring.flyway.password=${runtime.database.password}",
                "--unirhy.sync.node-id=$nodeId",
            )
        val localPort = context.environment.getProperty("local.server.port")?.toIntOrNull()
            ?: fail("[queue] fresh application should expose web server port")
        return FreshAppHandle(
            context = context,
            baseUrl = "http://127.0.0.1:$localPort",
        )
    }

    private fun baseUrl(): String = "http://127.0.0.1:$port"

    private fun suffix(): String = System.currentTimeMillis().toString(36)

    private fun assertAuthenticationFailed(response: HttpResponse<String>, step: String) {
        E2eAssert.apiError(
            response = response,
            family = "COMMON",
            code = "AUTHENTICATION_FAILED",
            expectedStatus = 401,
            step = step,
        )
    }

    companion object {
        private const val POLL_INTERVAL_MILLIS = 250L
        private const val SCAN_WAIT_TIMEOUT_MILLIS = 60_000L

        @JvmStatic
        @DynamicPropertySource
        fun registerDatasource(registry: DynamicPropertyRegistry) {
            E2eRuntime.registerDatasource(registry)
            registry.add("unirhy.sync.node-id") { "api-e2e-playback-primary" }
        }
    }
}

private data class FreshAppHandle(
    val context: ConfigurableApplicationContext,
    val baseUrl: String,
) : AutoCloseable {
    override fun close() {
        context.close()
    }
}

private data class CreatedAccount(
    val id: Long,
    val email: String,
    val password: String,
)

private data class AuthenticatedAccount(
    val api: E2eHttpClient,
    val token: String,
)

private class PlaybackSyncProbe(
    private val webSocket: WebSocket,
    private val listener: PlaybackSyncListener,
) : AutoCloseable {
    fun sendJson(json: Any) {
        listener.sendJson(webSocket, json)
    }

    fun awaitMessage(
        type: String,
        step: String,
        predicate: (JsonNode) -> Boolean = { true },
    ): JsonNode {
        val deadline = System.currentTimeMillis() + 10_000L
        while (System.currentTimeMillis() <= deadline) {
            val message = listener.pollMessage(250L) ?: continue
            if (message.path("type").asText() == type && predicate(message)) {
                return message
            }
        }
        fail("$step expected message type=$type within timeout")
    }

    override fun close() {
        runCatching {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join()
        }
    }
}

private class PlaybackSyncListener : WebSocket.Listener {
    private val messages = LinkedBlockingQueue<JsonNode>()
    private val textBuffer = StringBuilder()
    private val closed = CompletableFuture<Void>()

    override fun onOpen(webSocket: WebSocket) {
        webSocket.request(1)
    }

    override fun onText(
        webSocket: WebSocket,
        data: CharSequence,
        last: Boolean,
    ): CompletableFuture<*>? {
        textBuffer.append(data)
        if (last) {
            messages.offer(E2eJson.mapper.readTree(textBuffer.toString()))
            textBuffer.setLength(0)
        }
        webSocket.request(1)
        return null
    }

    override fun onClose(
        webSocket: WebSocket,
        statusCode: Int,
        reason: String,
    ): CompletableFuture<*>? {
        closed.complete(null)
        return null
    }

    override fun onError(webSocket: WebSocket, error: Throwable) {
        closed.completeExceptionally(error)
    }

    fun sendJson(webSocket: WebSocket, json: Any) {
        webSocket.sendText(E2eJson.mapper.writeValueAsString(json), true).join()
    }

    fun pollMessage(timeoutMs: Long): JsonNode? {
        return messages.poll(timeoutMs, TimeUnit.MILLISECONDS)
    }
}
