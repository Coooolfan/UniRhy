package com.unirhy.e2e

import com.coooolfan.unirhy.UnirhyApplication
import com.coooolfan.unirhy.sync.protocol.PlaybackStrategy
import com.coooolfan.unirhy.sync.protocol.StopStrategy
import com.fasterxml.jackson.databind.JsonNode
import com.unirhy.e2e.support.E2eAdminSession
import com.unirhy.e2e.support.E2eAssert
import com.unirhy.e2e.support.E2eHttpClient
import com.unirhy.e2e.support.E2eJson
import com.unirhy.e2e.support.E2eRuntime
import com.unirhy.e2e.support.PreparedPlaybackData
import com.unirhy.e2e.support.bootstrapAdminSession
import com.unirhy.e2e.support.ensurePreparedPlaybackData
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.net.http.HttpResponse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest(
    classes = [UnirhyApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("full")
class PlaybackQueueE2eTest {

    @LocalServerPort
    private var port: Int = 0

    @AfterAll
    fun cleanup() {
        E2eRuntime.cleanup()
    }

    @Test
    fun `all playback queue endpoints should reject unauthenticated access`() {
        val api = E2eHttpClient(baseUrl())

        assertAuthenticationFailed(
            api.get("/api/playback/current-queue"),
            "[auth] get current queue should require login",
        )
        assertAuthenticationFailed(
            api.put(
                path = "/api/playback/current-queue",
                json = mapOf("recordingIds" to listOf(1L), "currentIndex" to 0, "version" to 0L),
            ),
            "[auth] replace current queue should require login",
        )
        assertAuthenticationFailed(
            api.post(
                path = "/api/playback/current-queue/items",
                json = mapOf("recordingIds" to listOf(1L), "version" to 0L),
            ),
            "[auth] append current queue should require login",
        )
        assertAuthenticationFailed(
            api.put(
                path = "/api/playback/current-queue/order",
                json = mapOf("recordingIds" to listOf(1L), "currentIndex" to 0, "version" to 0L),
            ),
            "[auth] reorder current queue should require login",
        )
        assertAuthenticationFailed(
            api.put(
                path = "/api/playback/current-queue/current",
                json = mapOf("currentIndex" to 0, "version" to 0L),
            ),
            "[auth] set current index should require login",
        )
        assertAuthenticationFailed(
            api.put(
                path = "/api/playback/current-queue/strategy",
                json = mapOf("playbackStrategy" to "SHUFFLE", "stopStrategy" to "TRACK", "version" to 0L),
            ),
            "[auth] update current queue strategy should require login",
        )
        assertAuthenticationFailed(
            api.post(
                path = "/api/playback/current-queue/actions/next",
                json = mapOf("version" to 0L),
            ),
            "[auth] navigate next should require login",
        )
        assertAuthenticationFailed(
            api.post(
                path = "/api/playback/current-queue/actions/previous",
                json = mapOf("version" to 0L),
            ),
            "[auth] navigate previous should require login",
        )
        assertAuthenticationFailed(
            api.post(
                path = "/api/playback/current-queue/actions/remove",
                json = mapOf("index" to 0, "version" to 0L),
            ),
            "[auth] remove current queue entry should require login",
        )
        assertAuthenticationFailed(
            api.post(
                path = "/api/playback/current-queue/actions/clear",
                json = mapOf("version" to 0L),
            ),
            "[auth] clear current queue should require login",
        )
    }

    @Test
    fun `playback queue should support full mutation flow and stable conflict branches`() {
        val state = bootstrapAdminSession(baseUrl())
        val prepared = ensurePreparedPlaybackData(state, minRecordingCount = 3)

        val initial = resetQueue(state)
        assertQueue(
            queue = initial,
            expectedRecordingIds = emptyList(),
            expectedCurrentIndex = 0,
            expectedPlaybackStrategy = PlaybackStrategy.SEQUENTIAL,
            expectedStopStrategy = StopStrategy.LIST,
        )

        val recordingA = prepared.recordingIds[0]
        val recordingB = prepared.recordingIds[1]
        val recordingC = prepared.recordingIds[2]

        val replaceResponse = state.api.put(
            path = "/api/playback/current-queue",
            json = mapOf(
                "recordingIds" to listOf(recordingA, recordingB),
                "currentIndex" to 0,
                "version" to readVersion(initial),
            ),
        )
        E2eAssert.status(replaceResponse, 200, "[flow] replace queue should succeed")
        val replaceQueue = readJson(replaceResponse.body())
        assertQueue(
            queue = replaceQueue,
            expectedRecordingIds = listOf(recordingA, recordingB),
            expectedCurrentIndex = 0,
            expectedPlaybackStrategy = PlaybackStrategy.SEQUENTIAL,
            expectedStopStrategy = StopStrategy.LIST,
        )

        val persistedAfterReplace = fetchQueue(state.api)
        assertTrue(
            readVersion(persistedAfterReplace) >= readVersion(replaceQueue),
            "[flow] replace should not regress persisted version",
        )
        assertEquals(
            listOf(recordingA, recordingB),
            recordingIdsOf(persistedAfterReplace),
            "[flow] replace should persist recording ids",
        )

        val appendResponse = state.api.post(
            path = "/api/playback/current-queue/items",
            json = mapOf(
                "recordingIds" to listOf(recordingC),
                "version" to readVersion(persistedAfterReplace),
            ),
        )
        E2eAssert.status(appendResponse, 200, "[flow] append queue should succeed")
        val appendQueue = readJson(appendResponse.body())
        assertEquals(listOf(recordingA, recordingB, recordingC), recordingIdsOf(appendQueue), "[flow] append should extend queue")
        assertVersionAdvanced(replaceQueue, appendQueue, "[flow] append should increment version")
        val persistedAfterAppend = fetchQueue(state.api)

        val reorderResponse = state.api.put(
            path = "/api/playback/current-queue/order",
            json = mapOf(
                "recordingIds" to listOf(recordingC, recordingA, recordingB),
                "currentIndex" to 1,
                "version" to readVersion(persistedAfterAppend),
            ),
        )
        E2eAssert.status(reorderResponse, 200, "[flow] reorder queue should succeed")
        val reorderQueue = readJson(reorderResponse.body())
        assertEquals(listOf(recordingC, recordingA, recordingB), recordingIdsOf(reorderQueue), "[flow] reorder should replace ordering")
        assertEquals(1, readCurrentIndex(reorderQueue), "[flow] reorder should persist anchor index")
        assertVersionAdvanced(appendQueue, reorderQueue, "[flow] reorder should increment version")
        val persistedAfterReorder = fetchQueue(state.api)

        val setCurrentResponse = state.api.put(
            path = "/api/playback/current-queue/current",
            json = mapOf(
                "currentIndex" to 2,
                "version" to readVersion(persistedAfterReorder),
            ),
        )
        E2eAssert.status(setCurrentResponse, 200, "[flow] set current index should succeed")
        val setCurrentQueue = readJson(setCurrentResponse.body())
        assertEquals(2, readCurrentIndex(setCurrentQueue), "[flow] set current should move index")
        assertVersionAdvanced(reorderQueue, setCurrentQueue, "[flow] set current should increment version")
        val persistedAfterSetCurrent = fetchQueue(state.api)

        val strategyResponse = state.api.put(
            path = "/api/playback/current-queue/strategy",
            json = mapOf(
                "playbackStrategy" to PlaybackStrategy.SHUFFLE.name,
                "stopStrategy" to StopStrategy.TRACK.name,
                "version" to readVersion(persistedAfterSetCurrent),
            ),
        )
        E2eAssert.status(strategyResponse, 200, "[flow] update queue strategy should succeed")
        val strategyQueue = readJson(strategyResponse.body())
        assertQueue(
            queue = strategyQueue,
            expectedRecordingIds = listOf(recordingC, recordingA, recordingB),
            expectedCurrentIndex = 2,
            expectedPlaybackStrategy = PlaybackStrategy.SHUFFLE,
            expectedStopStrategy = StopStrategy.TRACK,
        )
        assertVersionAdvanced(setCurrentQueue, strategyQueue, "[flow] strategy update should increment version")
        val persistedAfterStrategy = fetchQueue(state.api)

        val nextResponse = state.api.post(
            path = "/api/playback/current-queue/actions/next",
            json = mapOf("version" to readVersion(persistedAfterStrategy)),
        )
        E2eAssert.status(nextResponse, 200, "[flow] navigate next should succeed")
        val nextQueue = readJson(nextResponse.body())
        assertVersionAdvanced(strategyQueue, nextQueue, "[flow] next should increment version")
        assertTrue(
            readCurrentIndex(nextQueue) != readCurrentIndex(strategyQueue),
            "[flow] next should move current index under shuffle order",
        )
        val persistedAfterNext = fetchQueue(state.api)

        val previousResponse = state.api.post(
            path = "/api/playback/current-queue/actions/previous",
            json = mapOf("version" to readVersion(persistedAfterNext)),
        )
        E2eAssert.status(previousResponse, 200, "[flow] navigate previous should succeed")
        val previousQueue = readJson(previousResponse.body())
        assertVersionAdvanced(nextQueue, previousQueue, "[flow] previous should increment version")
        assertEquals(
            readCurrentIndex(strategyQueue),
            readCurrentIndex(previousQueue),
            "[flow] previous should restore the pre-next current index",
        )
        val persistedAfterPrevious = fetchQueue(state.api)

        val removeResponse = state.api.post(
            path = "/api/playback/current-queue/actions/remove",
            json = mapOf(
                "index" to readCurrentIndex(persistedAfterPrevious),
                "version" to readVersion(persistedAfterPrevious),
            ),
        )
        E2eAssert.status(removeResponse, 200, "[flow] remove current queue entry should succeed")
        val removeQueue = readJson(removeResponse.body())
        assertEquals(listOf(recordingC, recordingA), recordingIdsOf(removeQueue), "[flow] remove should delete current entry")
        assertEquals(1, readCurrentIndex(removeQueue), "[flow] remove current should retarget current index")
        assertVersionAdvanced(previousQueue, removeQueue, "[flow] remove should increment version")
        val persistedAfterRemove = fetchQueue(state.api)

        val clearResponse = state.api.post(
            path = "/api/playback/current-queue/actions/clear",
            json = mapOf("version" to readVersion(persistedAfterRemove)),
        )
        E2eAssert.status(clearResponse, 200, "[flow] clear current queue should succeed")
        val clearQueue = readJson(clearResponse.body())
        assertQueue(
            queue = clearQueue,
            expectedRecordingIds = emptyList(),
            expectedCurrentIndex = 0,
            expectedPlaybackStrategy = PlaybackStrategy.SEQUENTIAL,
            expectedStopStrategy = StopStrategy.LIST,
        )
        assertVersionAdvanced(removeQueue, clearQueue, "[flow] clear should increment version")
        val persistedAfterClear = fetchQueue(state.api)

        val staleVersionResponse = state.api.post(
            path = "/api/playback/current-queue/items",
            json = mapOf(
                "recordingIds" to listOf(recordingA),
                "version" to readVersion(persistedAfterClear) - 1,
            ),
        )
        E2eAssert.status(staleVersionResponse, 409, "[error] stale queue version should return conflict")

        val invalidRecordingResponse = state.api.put(
            path = "/api/playback/current-queue",
            json = mapOf(
                "recordingIds" to listOf(Long.MAX_VALUE),
                "currentIndex" to 0,
                "version" to readVersion(persistedAfterClear),
            ),
        )
        E2eAssert.status(invalidRecordingResponse, 409, "[error] unknown recording in replace should return conflict")
    }

    private fun resetQueue(state: E2eAdminSession): JsonNode {
        val queue = fetchQueue(state.api)
        if (recordingIdsOf(queue).isEmpty()) {
            return queue
        }
        val response = state.api.post(
            path = "/api/playback/current-queue/actions/clear",
            json = mapOf("version" to readVersion(queue)),
        )
        E2eAssert.status(response, 200, "[prepare] clearing playback queue should succeed")
        return readJson(response.body())
    }

    private fun fetchQueue(api: E2eHttpClient): JsonNode {
        val response = api.get("/api/playback/current-queue")
        E2eAssert.status(response, 200, "[queue] get current queue should succeed")
        return readJson(response.body())
    }

    private fun assertQueue(
        queue: JsonNode,
        expectedRecordingIds: List<Long>,
        expectedCurrentIndex: Int,
        expectedPlaybackStrategy: PlaybackStrategy,
        expectedStopStrategy: StopStrategy,
    ) {
        assertEquals(expectedRecordingIds, recordingIdsOf(queue), "[queue] recording ids should match")
        assertEquals(expectedCurrentIndex, readCurrentIndex(queue), "[queue] current index should match")
        assertEquals(expectedPlaybackStrategy.name, queue.path("playbackStrategy").asText(), "[queue] playback strategy should match")
        assertEquals(expectedStopStrategy.name, queue.path("stopStrategy").asText(), "[queue] stop strategy should match")
        assertEquals("PAUSED", queue.path("playbackStatus").asText(), "[queue] playback status should stay paused")
    }

    private fun assertVersionAdvanced(
        previous: JsonNode,
        next: JsonNode,
        step: String,
    ) {
        assertTrue(readVersion(next) > readVersion(previous), step)
    }

    private fun recordingIdsOf(queue: JsonNode): List<Long> {
        return queue.path("recordingIds").map(JsonNode::longValue)
    }

    private fun readCurrentIndex(queue: JsonNode): Int {
        return queue.path("currentIndex").intValue()
    }

    private fun readVersion(queue: JsonNode): Long {
        return queue.path("version").longValue()
    }

    private fun readJson(body: String): JsonNode {
        return E2eJson.mapper.readTree(body)
    }

    private fun assertAuthenticationFailed(
        response: HttpResponse<String>,
        step: String,
    ) {
        E2eAssert.apiError(
            response = response,
            family = "COMMON",
            code = "AUTHENTICATION_FAILED",
            expectedStatus = 401,
            step = step,
        )
    }

    private fun baseUrl(): String = "http://127.0.0.1:$port"

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun registerDatasource(registry: DynamicPropertyRegistry) {
            E2eRuntime.registerDatasource(registry)
        }
    }
}
