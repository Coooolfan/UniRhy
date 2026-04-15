package com.unirhy.e2e

import com.coooolfan.unirhy.UnirhyApplication
import com.coooolfan.unirhy.sync.protocol.PlaybackSyncMessageType
import com.unirhy.e2e.support.E2eAdminSession
import com.unirhy.e2e.support.E2eAssert
import com.unirhy.e2e.support.E2eJson
import com.unirhy.e2e.support.E2eRuntime
import com.unirhy.e2e.support.E2eWebSocketClient
import com.unirhy.e2e.support.E2eWebSocketMessage
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
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest(
    classes = [UnirhyApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("full")
class PlaybackSyncWebSocketE2eTest {

    @LocalServerPort
    private var port: Int = 0

    @AfterAll
    fun cleanup() {
        E2eRuntime.cleanup()
    }

    @Test
    fun `hello should send snapshot and device change`() {
        val state = bootstrapAdminSession(baseUrl())
        resetQueue(state)

        E2eWebSocketClient.connect(baseUrl(), state.api.authToken()).use { client ->
            hello(client, state, "web-hello")

            val snapshot = client.awaitMessage(PlaybackSyncMessageType.SNAPSHOT)
            assertEquals("PAUSED", snapshot.payload.path("state").path("status").asText(), "[hello] snapshot should start paused")
            assertTrue(snapshot.payload.path("queue").path("recordingIds").isArray, "[hello] snapshot should include queue")

            val deviceChange = client.awaitMessage(PlaybackSyncMessageType.ROOM_EVENT_DEVICE_CHANGE)
            val devices = deviceChange.payload.path("devices")
            assertEquals(1, devices.size(), "[hello] device change should include one active device")
            assertEquals("web-hello", devices.first().path("deviceId").asText(), "[hello] device change should include connected device")
        }
    }

    @Test
    fun `non hello first message should close unauthenticated connection`() {
        val state = bootstrapAdminSession(baseUrl())

        E2eWebSocketClient.connect(baseUrl(), state.api.authToken()).use { client ->
            client.send(
                PlaybackSyncMessageType.PLAY,
                mapOf(
                    "commandId" to "cmd-first-play",
                    "deviceId" to "web-first",
                    "currentIndex" to 0,
                    "positionSeconds" to 0.0,
                    "version" to 0L,
                ),
            )

            client.awaitClose()
        }
    }

    @Test
    fun `duplicate hello should return invalid message error`() {
        val state = bootstrapAdminSession(baseUrl())

        E2eWebSocketClient.connect(baseUrl(), state.api.authToken()).use { client ->
            hello(client, state, "web-dup")
            client.awaitMessage(PlaybackSyncMessageType.SNAPSHOT)
            client.awaitMessage(PlaybackSyncMessageType.ROOM_EVENT_DEVICE_CHANGE)

            hello(client, state, "web-dup")

            val error = client.awaitMessage(PlaybackSyncMessageType.ERROR)
            assertEquals("INVALID_MESSAGE", error.payload.path("code").asText(), "[hello] duplicate hello should return invalid message")
        }
    }

    @Test
    fun `invalid hello token should close connection`() {
        E2eWebSocketClient.connect(baseUrl()).use { client ->
            client.send(
                PlaybackSyncMessageType.HELLO,
                mapOf(
                    "deviceId" to "web-invalid-token",
                    "clientVersion" to "e2e",
                    "token" to "invalid-token",
                ),
            )

            client.awaitClose()
        }
    }

    @Test
    fun `same device id should replace previous connection`() {
        val state = bootstrapAdminSession(baseUrl())

        E2eWebSocketClient.connect(baseUrl(), state.api.authToken()).use { first ->
            hello(first, state, "web-replaced")
            first.awaitMessage(PlaybackSyncMessageType.SNAPSHOT)
            first.awaitMessage(PlaybackSyncMessageType.ROOM_EVENT_DEVICE_CHANGE)

            E2eWebSocketClient.connect(baseUrl(), state.api.authToken()).use { second ->
                hello(second, state, "web-replaced")

                first.awaitClose()

                val snapshot = second.awaitMessage(PlaybackSyncMessageType.SNAPSHOT)
                assertEquals("PAUSED", snapshot.payload.path("state").path("status").asText(), "[replace] new connection should still receive snapshot")
                val deviceChange = second.awaitMessage(PlaybackSyncMessageType.ROOM_EVENT_DEVICE_CHANGE)
                assertEquals(1, deviceChange.payload.path("devices").size(), "[replace] only replacement device should stay online")
            }
        }
    }

    @Test
    fun `playback websocket should coordinate dual device play and version conflict`() {
        val state = bootstrapAdminSession(baseUrl())
        val prepared = ensurePreparedPlaybackData(state, minRecordingCount = 3)
        val queue = replaceQueue(state, prepared, currentIndex = 0)

        E2eWebSocketClient.connect(baseUrl(), state.api.authToken()).use { deviceA ->
            E2eWebSocketClient.connect(baseUrl(), state.api.authToken()).use { deviceB ->
                hello(deviceA, state, "web-a")
                val aSnapshot = deviceA.awaitMessage(PlaybackSyncMessageType.SNAPSHOT)
                assertEquals(queue.path("version").longValue(), aSnapshot.payload.path("queue").path("version").longValue(), "[play] device A should see prepared queue")
                deviceA.awaitMessage(PlaybackSyncMessageType.ROOM_EVENT_DEVICE_CHANGE)

                hello(deviceB, state, "web-b")
                deviceB.awaitMessage(PlaybackSyncMessageType.SNAPSHOT)
                deviceB.awaitMessage(PlaybackSyncMessageType.ROOM_EVENT_DEVICE_CHANGE)
                val joinedChange = deviceA.awaitMessage(PlaybackSyncMessageType.ROOM_EVENT_DEVICE_CHANGE)
                assertEquals(2, joinedChange.payload.path("devices").size(), "[play] device A should observe both devices")

                sendNtp(deviceA)
                sendNtp(deviceB)

                deviceA.send(
                    PlaybackSyncMessageType.PLAY,
                    mapOf(
                        "commandId" to "cmd-play-dual",
                        "deviceId" to "web-a",
                        "currentIndex" to 1,
                        "positionSeconds" to 12.5,
                        "version" to queue.path("version").longValue(),
                    ),
                )

                val queueChangeA = deviceA.awaitMessage(PlaybackSyncMessageType.ROOM_EVENT_QUEUE_CHANGE)
                val queueChangeB = deviceB.awaitMessage(PlaybackSyncMessageType.ROOM_EVENT_QUEUE_CHANGE)
                assertEquals(1, queueChangeA.payload.path("queue").path("currentIndex").intValue(), "[play] queue change should switch current index for device A")
                assertEquals(1, queueChangeB.payload.path("queue").path("currentIndex").intValue(), "[play] queue change should switch current index for device B")

                val loadA = deviceA.awaitMessage(PlaybackSyncMessageType.ROOM_EVENT_LOAD_AUDIO_SOURCE)
                val loadB = deviceB.awaitMessage(PlaybackSyncMessageType.ROOM_EVENT_LOAD_AUDIO_SOURCE)
                assertEquals("cmd-play-dual", loadA.payload.path("commandId").asText(), "[play] device A should receive load command")
                assertEquals("cmd-play-dual", loadB.payload.path("commandId").asText(), "[play] device B should receive load command")

                deviceB.send(
                    PlaybackSyncMessageType.AUDIO_SOURCE_LOADED,
                    mapOf(
                        "commandId" to "cmd-play-dual",
                        "deviceId" to "web-b",
                        "currentIndex" to 1,
                        "recordingId" to prepared.recordingIds[1],
                    ),
                )

                val scheduledA = deviceA.awaitMessage(PlaybackSyncMessageType.SCHEDULED_ACTION)
                val scheduledB = deviceB.awaitMessage(PlaybackSyncMessageType.SCHEDULED_ACTION)
                assertEquals("PLAY", scheduledA.payload.path("scheduledAction").path("action").asText(), "[play] device A should receive play action")
                assertEquals("PLAY", scheduledB.payload.path("scheduledAction").path("action").asText(), "[play] device B should receive play action")
                assertEquals(1, scheduledA.payload.path("scheduledAction").path("currentIndex").intValue(), "[play] scheduled action should target switched index")
                assertEquals(1, scheduledB.payload.path("scheduledAction").path("currentIndex").intValue(), "[play] scheduled action should target switched index on device B")
                assertEquals(12.5, scheduledA.payload.path("scheduledAction").path("positionSeconds").doubleValue(), "[play] scheduled action should preserve position")

                deviceA.send(
                    PlaybackSyncMessageType.PLAY,
                    mapOf(
                        "commandId" to "cmd-play-stale",
                        "deviceId" to "web-a",
                        "currentIndex" to 0,
                        "positionSeconds" to 0.0,
                        "version" to queue.path("version").longValue(),
                    ),
                )

                val staleError = deviceA.awaitMessage(PlaybackSyncMessageType.ERROR)
                assertEquals("VERSION_CONFLICT", staleError.payload.path("code").asText(), "[play] stale queue version should return websocket version conflict")
            }
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
                "clientRttMs" to 18.5,
            ),
        )
        val response = client.awaitMessage(PlaybackSyncMessageType.NTP_RESPONSE)
        assertTrue(response.payload.path("t0").isIntegralNumber, "[ntp] response should echo t0")
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
        E2eAssert.status(response, 200, "[prepare] clearing queue for websocket test should succeed")
    }

    private fun replaceQueue(
        state: E2eAdminSession,
        prepared: PreparedPlaybackData,
        currentIndex: Int,
    ) = run {
        resetQueue(state)
        val queue = fetchQueue(state)
        val response = state.api.put(
            path = "/api/playback/current-queue",
            json = mapOf(
                "recordingIds" to prepared.recordingIds.take(2),
                "currentIndex" to currentIndex,
                "version" to queue.path("version").longValue(),
            ),
        )
        E2eAssert.status(response, 200, "[prepare] replace queue for websocket test should succeed")
        fetchQueue(state)
    }

    private fun fetchQueue(state: E2eAdminSession) = run {
        val response = state.api.get("/api/playback/current-queue")
        E2eAssert.status(response, 200, "[prepare] get current queue for websocket test should succeed")
        E2eJson.mapper.readTree(response.body())
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
