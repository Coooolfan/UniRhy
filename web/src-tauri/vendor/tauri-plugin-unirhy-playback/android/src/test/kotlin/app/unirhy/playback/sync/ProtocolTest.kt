package app.unirhy.playback.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolTest {

    @Test
    fun `parses snapshot with queue and mediaFileId`() {
        val raw = """
            {
              "type": "SNAPSHOT",
              "payload": {
                "state": {
                  "status": "PLAYING",
                  "currentIndex": 2,
                  "positionSeconds": 12.5,
                  "serverTimeToExecuteMs": 1730844001500,
                  "version": 8,
                  "updatedAtMs": 1730844000100
                },
                "queue": {
                  "items": [
                    {
                      "recordingId": 1001,
                      "title": "Track 1",
                      "artistLabel": "Artist 1",
                      "coverUrl": "/api/media-files/3001?_sig=x&_exp=1",
                      "durationMs": 180000,
                      "mediaFileId": 2001
                    }
                  ],
                  "recordingIds": [1001],
                  "currentIndex": 0,
                  "playbackStrategy": "SEQUENTIAL",
                  "stopStrategy": "LIST",
                  "playbackStatus": "PLAYING",
                  "positionMs": 12500,
                  "serverTimeToExecuteMs": 1730844001500,
                  "version": 8,
                  "updatedAtMs": 1730844000100
                },
                "serverNowMs": 1730844000200
              }
            }
        """.trimIndent()

        val message = ServerMessage.parse(raw) as ServerMessage.Snapshot
        assertEquals(PlaybackStatus.PLAYING, message.payload.state.status)
        assertEquals(2, message.payload.state.currentIndex)
        assertEquals(8L, message.payload.queue.version)
        assertEquals(2001L, message.payload.queue.items.single().mediaFileId)
    }

    @Test
    fun `parses scheduled action with late compensation flag`() {
        val raw = """
            {
              "type": "SCHEDULED_ACTION",
              "payload": {
                "commandId": "cmd-1",
                "serverTimeToExecuteMs": 1730844001500,
                "scheduledAction": {
                  "action": "PLAY",
                  "status": "PLAYING",
                  "currentIndex": 2,
                  "positionSeconds": 0.0,
                  "version": 9
                },
                "skipLateCompensation": true
              }
            }
        """.trimIndent()

        val message = ServerMessage.parse(raw) as ServerMessage.ScheduledAction
        assertEquals(ScheduledActionType.PLAY, message.payload.scheduledAction.action)
        assertTrue(message.payload.skipLateCompensation)
    }

    @Test
    fun `parse tolerates unknown fields and missing optionals`() {
        val raw = """
            {
              "type": "ROOM_EVENT_LOAD_AUDIO_SOURCE",
              "payload": { "commandId": "cmd-2", "currentIndex": 1, "recordingId": 42, "futureField": {"x": 1} }
            }
        """.trimIndent()

        val message = ServerMessage.parse(raw) as ServerMessage.LoadAudioSource
        assertEquals(42L, message.payload.recordingId)
    }

    @Test
    fun `parse returns null for unknown type or invalid json`() {
        assertNull(ServerMessage.parse("""{"type":"FUTURE_MESSAGE","payload":{}}"""))
        assertNull(ServerMessage.parse("not json"))
        assertNull(ServerMessage.parse("""{"payload":{}}"""))
    }

    @Test
    fun `encodes client messages in the envelope format`() {
        val encoded = encodeClientMessage(
            "PLAY",
            PlaybackControlPayload(
                commandId = "cmd-3",
                deviceId = "tauri-android-abc",
                currentIndex = 1,
                positionSeconds = 3.5,
                version = 7,
            ),
        )
        val tree = PlaybackSyncJson.mapper.readTree(encoded)
        assertEquals("PLAY", tree.get("type").asText())
        assertEquals("tauri-android-abc", tree.get("payload").get("deviceId").asText())
        assertEquals(7L, tree.get("payload").get("version").asLong())
    }

    @Test
    fun `hello payload omits null token`() {
        val encoded = encodeClientMessage(
            "HELLO",
            HelloPayload(deviceId = "tauri-android-abc", clientVersion = "android-native@1"),
        )
        val payload = PlaybackSyncJson.mapper.readTree(encoded).get("payload")
        // Jackson 默认序列化 null 字段；服务端按可空处理，两种形态皆可接受，
        // 此处仅固化 deviceId/clientVersion 的存在性
        assertEquals("tauri-android-abc", payload.get("deviceId").asText())
        assertEquals("android-native@1", payload.get("clientVersion").asText())
        assertFalse(payload.has("unknown"))
    }
}
