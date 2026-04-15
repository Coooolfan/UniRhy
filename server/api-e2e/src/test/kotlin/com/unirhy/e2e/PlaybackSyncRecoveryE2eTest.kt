package com.unirhy.e2e

import com.coooolfan.unirhy.UnirhyApplication
import com.coooolfan.unirhy.sync.protocol.PlaybackSyncMessageType
import com.fasterxml.jackson.databind.JsonNode
import com.unirhy.e2e.support.E2eAdminSession
import com.unirhy.e2e.support.E2eAssert
import com.unirhy.e2e.support.E2eJson
import com.unirhy.e2e.support.E2eRuntime
import com.unirhy.e2e.support.E2eWebSocketClient
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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest(
    classes = [UnirhyApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("full")
class PlaybackSyncRecoveryE2eTest {

    @LocalServerPort
    private var port: Int = 0

    @AfterAll
    fun cleanup() {
        E2eRuntime.cleanup()
    }

    @Test
    fun `sync should require ntp and then return scheduled action`() {
        val state = bootstrapAdminSession(baseUrl())
        val prepared = ensurePreparedPlaybackData(state, minRecordingCount = 3)
        replaceQueue(state, prepared, currentIndex = 0)

        E2eWebSocketClient.connect(baseUrl(), state.api.authToken()).use { client ->
            hello(client, state, "web-sync")
            client.awaitMessage(PlaybackSyncMessageType.SNAPSHOT)
            client.awaitMessage(PlaybackSyncMessageType.ROOM_EVENT_DEVICE_CHANGE)

            client.send(
                PlaybackSyncMessageType.SYNC,
                mapOf("deviceId" to "web-sync"),
            )
            val notReadyError = client.awaitMessage(PlaybackSyncMessageType.ERROR)
            assertEquals("SYNC_NOT_READY", notReadyError.payload.path("code").asText(), "[sync] sync should require ntp first")

            sendNtp(client)
            client.send(
                PlaybackSyncMessageType.SYNC,
                mapOf("deviceId" to "web-sync"),
            )
            val scheduled = client.awaitMessage(PlaybackSyncMessageType.SCHEDULED_ACTION)
            assertEquals("PAUSE", scheduled.payload.path("scheduledAction").path("action").asText(), "[sync] paused recovery should emit pause action")
        }
    }

    @Test
    fun `paused queue mutation should broadcast queue change and scheduled pause`() {
        val state = bootstrapAdminSession(baseUrl())
        val prepared = ensurePreparedPlaybackData(state, minRecordingCount = 3)
        val queue = replaceQueue(state, prepared, currentIndex = 0)

        E2eWebSocketClient.connect(baseUrl(), state.api.authToken()).use { client ->
            hello(client, state, "web-paused")
            client.awaitMessage(PlaybackSyncMessageType.SNAPSHOT)
            client.awaitMessage(PlaybackSyncMessageType.ROOM_EVENT_DEVICE_CHANGE)

            val response = state.api.put(
                path = "/api/playback/current-queue/current",
                json = mapOf(
                    "currentIndex" to 1,
                    "version" to queue.path("version").longValue(),
                ),
            )
            E2eAssert.status(response, 200, "[paused] set current index should succeed")
            val updatedQueue = E2eJson.mapper.readTree(response.body())

            val queueChange = client.awaitMessage(PlaybackSyncMessageType.ROOM_EVENT_QUEUE_CHANGE)
            assertEquals(1, queueChange.payload.path("queue").path("currentIndex").intValue(), "[paused] queue change should target new index")

            val scheduled = client.awaitMessage(PlaybackSyncMessageType.SCHEDULED_ACTION)
            val persistedQueue = fetchQueue(state)
            assertEquals("PAUSE", scheduled.payload.path("scheduledAction").path("action").asText(), "[paused] paused queue mutation should emit pause")
            assertEquals(1, scheduled.payload.path("scheduledAction").path("currentIndex").intValue(), "[paused] scheduled pause should target current index")
            assertEquals(
                persistedQueue.path("version").longValue(),
                scheduled.payload.path("scheduledAction").path("version").longValue(),
                "[paused] scheduled pause should carry updated version",
            )
        }
    }

    @Test
    fun `playing queue mutations should request audio load before scheduled play`() {
        val state = bootstrapAdminSession(baseUrl())
        val prepared = ensurePreparedPlaybackData(state, minRecordingCount = 3)
        val queue = replaceQueue(state, prepared, currentIndex = 0)

        E2eWebSocketClient.connect(baseUrl(), state.api.authToken()).use { client ->
            hello(client, state, "web-playing")
            client.awaitMessage(PlaybackSyncMessageType.SNAPSHOT)
            client.awaitMessage(PlaybackSyncMessageType.ROOM_EVENT_DEVICE_CHANGE)
            sendNtp(client)

            client.send(
                PlaybackSyncMessageType.PLAY,
                mapOf(
                    "commandId" to "cmd-initial-play",
                    "deviceId" to "web-playing",
                    "currentIndex" to 0,
                    "positionSeconds" to 0.0,
                    "version" to queue.path("version").longValue(),
                ),
            )
            val initialLoad = client.awaitMessage(PlaybackSyncMessageType.ROOM_EVENT_LOAD_AUDIO_SOURCE)
            assertEquals(0, initialLoad.payload.path("currentIndex").intValue(), "[playing] initial play should load first track")
            val initialScheduled = client.awaitMessage(PlaybackSyncMessageType.SCHEDULED_ACTION)
            assertEquals("PLAY", initialScheduled.payload.path("scheduledAction").path("action").asText(), "[playing] initial play should schedule playback")

            val currentAfterPlay = fetchQueue(state)
            val nextResponse = state.api.post(
                path = "/api/playback/current-queue/actions/next",
                json = mapOf("version" to currentAfterPlay.path("version").longValue()),
            )
            E2eAssert.status(nextResponse, 200, "[playing] next action should succeed")
            val nextQueue = E2eJson.mapper.readTree(nextResponse.body())

            val nextQueueChange = client.awaitMessage(PlaybackSyncMessageType.ROOM_EVENT_QUEUE_CHANGE)
            assertEquals(1, nextQueueChange.payload.path("queue").path("currentIndex").intValue(), "[playing] next should move to second track")
            val nextLoad = client.awaitMessage(PlaybackSyncMessageType.ROOM_EVENT_LOAD_AUDIO_SOURCE)
            assertEquals(1, nextLoad.payload.path("currentIndex").intValue(), "[playing] next should request loading second track")
            client.send(
                PlaybackSyncMessageType.AUDIO_SOURCE_LOADED,
                mapOf(
                    "commandId" to nextLoad.payload.path("commandId").asText(),
                    "deviceId" to "web-playing",
                    "currentIndex" to 1,
                    "recordingId" to prepared.recordingIds[1],
                ),
            )
            val nextScheduled = client.awaitMessage(PlaybackSyncMessageType.SCHEDULED_ACTION)
            assertEquals("PLAY", nextScheduled.payload.path("scheduledAction").path("action").asText(), "[playing] next should schedule play after load")
            val persistedAfterNext = fetchQueue(state)

            val removeResponse = state.api.post(
                path = "/api/playback/current-queue/actions/remove",
                json = mapOf(
                    "index" to persistedAfterNext.path("currentIndex").intValue(),
                    "version" to persistedAfterNext.path("version").longValue(),
                ),
            )
            E2eAssert.status(removeResponse, 200, "[playing] remove current action should succeed")

            val removeQueueChange = client.awaitMessage(PlaybackSyncMessageType.ROOM_EVENT_QUEUE_CHANGE)
            assertEquals(1, removeQueueChange.payload.path("queue").path("currentIndex").intValue(), "[playing] remove current should retarget current index")
            val removeLoad = client.awaitMessage(PlaybackSyncMessageType.ROOM_EVENT_LOAD_AUDIO_SOURCE)
            val newCurrentRecordingId = removeLoad.payload.path("recordingId").longValue()
            client.send(
                PlaybackSyncMessageType.AUDIO_SOURCE_LOADED,
                mapOf(
                    "commandId" to removeLoad.payload.path("commandId").asText(),
                    "deviceId" to "web-playing",
                    "currentIndex" to removeLoad.payload.path("currentIndex").intValue(),
                    "recordingId" to newCurrentRecordingId,
                ),
            )
            val removeScheduled = client.awaitMessage(PlaybackSyncMessageType.SCHEDULED_ACTION)
            assertEquals("PLAY", removeScheduled.payload.path("scheduledAction").path("action").asText(), "[playing] remove current should schedule replacement play")
        }
    }

    @Test
    fun `disconnecting last playing device should auto pause snapshot state`() {
        val state = bootstrapAdminSession(baseUrl())
        val prepared = ensurePreparedPlaybackData(state, minRecordingCount = 3)
        val queue = replaceQueue(state, prepared, currentIndex = 0)

        val client = E2eWebSocketClient.connect(baseUrl(), state.api.authToken())
        try {
            hello(client, state, "web-disconnect")
            client.awaitMessage(PlaybackSyncMessageType.SNAPSHOT)
            client.awaitMessage(PlaybackSyncMessageType.ROOM_EVENT_DEVICE_CHANGE)
            sendNtp(client)

            client.send(
                PlaybackSyncMessageType.PLAY,
                mapOf(
                    "commandId" to "cmd-disconnect-play",
                    "deviceId" to "web-disconnect",
                    "currentIndex" to 0,
                    "positionSeconds" to 7.25,
                    "version" to queue.path("version").longValue(),
                ),
            )
            client.awaitMessage(PlaybackSyncMessageType.ROOM_EVENT_LOAD_AUDIO_SOURCE)
            val scheduled = client.awaitMessage(PlaybackSyncMessageType.SCHEDULED_ACTION)
            assertEquals("PLAY", scheduled.payload.path("scheduledAction").path("action").asText(), "[disconnect] initial play should schedule play")
        } finally {
            client.close()
            client.awaitClose()
        }

        E2eWebSocketClient.connect(baseUrl(), state.api.authToken()).use { reconnect ->
            hello(reconnect, state, "web-after-disconnect")
            val snapshot = reconnect.awaitMessage(PlaybackSyncMessageType.SNAPSHOT)
            assertEquals("PAUSED", snapshot.payload.path("state").path("status").asText(), "[disconnect] reconnect snapshot should show auto paused state")
        }
    }

    private fun hello(
        client: E2eWebSocketClient,
        state: E2eAdminSession,
        deviceId: String,
    ) {
        client.send(
            PlaybackSyncMessageType.HELLO,
            mapOf(
                "deviceId" to deviceId,
                "clientVersion" to "api-e2e",
                "token" to state.api.authToken(),
            ),
        )
    }

    private fun sendNtp(client: E2eWebSocketClient) {
        client.send(
            PlaybackSyncMessageType.NTP_REQUEST,
            mapOf(
                "t0" to System.currentTimeMillis(),
                "clientRttMs" to 20.0,
            ),
        )
        val response = client.awaitMessage(PlaybackSyncMessageType.NTP_RESPONSE)
        assertTrue(response.payload.path("t0").isIntegralNumber, "[ntp] response should include t0")
    }

    private fun replaceQueue(
        state: E2eAdminSession,
        prepared: PreparedPlaybackData,
        currentIndex: Int,
    ): JsonNode {
        resetQueue(state)
        val queue = fetchQueue(state)
        val response = state.api.put(
            path = "/api/playback/current-queue",
            json = mapOf(
                "recordingIds" to prepared.recordingIds,
                "currentIndex" to currentIndex,
                "version" to queue.path("version").longValue(),
            ),
        )
        E2eAssert.status(response, 200, "[prepare] replace queue for recovery test should succeed")
        return fetchQueue(state)
    }

    private fun resetQueue(state: E2eAdminSession) {
        val queue = fetchQueue(state)
        if (queue.path("recordingIds").isEmpty) {
            return
        }
        val response = state.api.post(
            path = "/api/playback/current-queue/actions/clear",
            json = mapOf("version" to queue.path("version").longValue()),
        )
        E2eAssert.status(response, 200, "[prepare] clear queue for recovery test should succeed")
    }

    private fun fetchQueue(state: E2eAdminSession): JsonNode {
        val response = state.api.get("/api/playback/current-queue")
        E2eAssert.status(response, 200, "[prepare] get queue for recovery test should succeed")
        return E2eJson.mapper.readTree(response.body())
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
