package com.coooolfan.unirhy.sync.protocol

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackSyncProtocolSerializationTest {

    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `serializes each client message with frozen envelope`() {
        clientSamples.forEach { sample ->
            val actualJson = objectMapper.writerFor(ClientPlaybackSyncMessage::class.java)
                .writeValueAsString(sample.message)
            assertJsonEquals(sample.expectedJson, actualJson)
        }
    }

    @Test
    fun `deserializes each client message into expected subtype`() {
        clientSamples.forEach { sample ->
            val actualMessage = objectMapper.readValue<ClientPlaybackSyncMessage>(sample.expectedJson)
            assertEquals(sample.expectedType, actualMessage::class)
            assertEquals(sample.message, actualMessage)
        }
    }

    @Test
    fun `serializes each server message with frozen envelope`() {
        serverSamples.forEach { sample ->
            val actualJson = objectMapper.writerFor(ServerPlaybackSyncMessage::class.java)
                .writeValueAsString(sample.message)
            assertJsonEquals(sample.expectedJson, actualJson)
        }
    }

    @Test
    fun `deserializes each server message into expected subtype`() {
        serverSamples.forEach { sample ->
            val actualMessage = objectMapper.readValue<ServerPlaybackSyncMessage>(sample.expectedJson)
            assertEquals(sample.expectedType, actualMessage::class)
            assertEquals(sample.message, actualMessage)
        }
    }

    private fun assertJsonEquals(expectedJson: String, actualJson: String) {
        assertEquals(
            objectMapper.readTree(expectedJson),
            objectMapper.readTree(actualJson),
        )
    }

    private data class ClientSample(
        val message: ClientPlaybackSyncMessage,
        val expectedJson: String,
        val expectedType: KClass<out ClientPlaybackSyncMessage>,
    )

    private data class ServerSample(
        val message: ServerPlaybackSyncMessage,
        val expectedJson: String,
        val expectedType: KClass<out ServerPlaybackSyncMessage>,
    )

    private val clientSamples = listOf(
        ClientSample(
            message = HelloMessage(
                payload = HelloPayload(
                    deviceId = "web-7c2f",
                    clientVersion = "web@0.1.0",
                ),
            ),
            expectedJson = """
                {
                  "type": "HELLO",
                  "payload": {
                    "deviceId": "web-7c2f",
                    "clientVersion": "web@0.1.0"
                  }
                }
            """.trimIndent(),
            expectedType = HelloMessage::class,
        ),
        ClientSample(
            message = NtpRequestMessage(
                payload = NtpRequestPayload(
                    t0 = 1730844000000,
                    clientRttMs = 18.5,
                ),
            ),
            expectedJson = """
                {
                  "type": "NTP_REQUEST",
                  "payload": {
                    "t0": 1730844000000,
                    "clientRttMs": 18.5
                  }
                }
            """.trimIndent(),
            expectedType = NtpRequestMessage::class,
        ),
        ClientSample(
            message = PlayMessage(
                payload = PlaybackControlPayload(
                    commandId = "cmd-play-001",
                    deviceId = "web-7c2f",
                    recordingId = 1001,
                    mediaFileId = 2001,
                    positionSeconds = 12.5,
                ),
            ),
            expectedJson = """
                {
                  "type": "PLAY",
                  "payload": {
                    "commandId": "cmd-play-001",
                    "deviceId": "web-7c2f",
                    "recordingId": 1001,
                    "mediaFileId": 2001,
                    "positionSeconds": 12.5
                  }
                }
            """.trimIndent(),
            expectedType = PlayMessage::class,
        ),
        ClientSample(
            message = PauseMessage(
                payload = PlaybackControlPayload(
                    commandId = "cmd-pause-001",
                    deviceId = "web-7c2f",
                    recordingId = 1001,
                    mediaFileId = 2001,
                    positionSeconds = 36.25,
                ),
            ),
            expectedJson = """
                {
                  "type": "PAUSE",
                  "payload": {
                    "commandId": "cmd-pause-001",
                    "deviceId": "web-7c2f",
                    "recordingId": 1001,
                    "mediaFileId": 2001,
                    "positionSeconds": 36.25
                  }
                }
            """.trimIndent(),
            expectedType = PauseMessage::class,
        ),
        ClientSample(
            message = SeekMessage(
                payload = PlaybackControlPayload(
                    commandId = "cmd-seek-001",
                    deviceId = "web-7c2f",
                    recordingId = 1001,
                    mediaFileId = 2001,
                    positionSeconds = 91.0,
                ),
            ),
            expectedJson = """
                {
                  "type": "SEEK",
                  "payload": {
                    "commandId": "cmd-seek-001",
                    "deviceId": "web-7c2f",
                    "recordingId": 1001,
                    "mediaFileId": 2001,
                    "positionSeconds": 91.0
                  }
                }
            """.trimIndent(),
            expectedType = SeekMessage::class,
        ),
        ClientSample(
            message = AudioSourceLoadedMessage(
                payload = AudioSourceLoadedPayload(
                    commandId = "cmd-play-001",
                    deviceId = "web-85ab",
                    recordingId = 1001,
                    mediaFileId = 2001,
                ),
            ),
            expectedJson = """
                {
                  "type": "AUDIO_SOURCE_LOADED",
                  "payload": {
                    "commandId": "cmd-play-001",
                    "deviceId": "web-85ab",
                    "recordingId": 1001,
                    "mediaFileId": 2001
                  }
                }
            """.trimIndent(),
            expectedType = AudioSourceLoadedMessage::class,
        ),
        ClientSample(
            message = SyncMessage(
                payload = SyncPayload(
                    deviceId = "web-85ab",
                ),
            ),
            expectedJson = """
                {
                  "type": "SYNC",
                  "payload": {
                    "deviceId": "web-85ab"
                  }
                }
            """.trimIndent(),
            expectedType = SyncMessage::class,
        ),
    )

    private val serverSamples = listOf(
        ServerSample(
            message = NtpResponseMessage(
                payload = NtpResponsePayload(
                    t0 = 1730844000000,
                    t1 = 1730844000012,
                    t2 = 1730844000014,
                ),
            ),
            expectedJson = """
                {
                  "type": "NTP_RESPONSE",
                  "payload": {
                    "t0": 1730844000000,
                    "t1": 1730844000012,
                    "t2": 1730844000014
                  }
                }
            """.trimIndent(),
            expectedType = NtpResponseMessage::class,
        ),
        ServerSample(
            message = SnapshotMessage(
                payload = SnapshotPayload(
                    state = AccountPlaybackState(
                        status = PlaybackStatus.PLAYING,
                        recordingId = 1001,
                        mediaFileId = 2001,
                        sourceUrl = "/api/media/2001",
                        positionSeconds = 12.5,
                        serverTimeToExecuteMs = 1730844001500,
                        version = 8,
                        updatedAtMs = 1730844000100,
                    ),
                    serverNowMs = 1730844000200,
                ),
            ),
            expectedJson = """
                {
                  "type": "SNAPSHOT",
                  "payload": {
                    "state": {
                      "status": "PLAYING",
                      "recordingId": 1001,
                      "mediaFileId": 2001,
                      "sourceUrl": "/api/media/2001",
                      "positionSeconds": 12.5,
                      "serverTimeToExecuteMs": 1730844001500,
                      "version": 8,
                      "updatedAtMs": 1730844000100
                    },
                    "serverNowMs": 1730844000200
                  }
                }
            """.trimIndent(),
            expectedType = SnapshotMessage::class,
        ),
        ServerSample(
            message = LoadAudioSourceMessage(
                payload = LoadAudioSourcePayload(
                    commandId = "cmd-play-001",
                    recordingId = 1001,
                    mediaFileId = 2001,
                    sourceUrl = "/api/media/2001",
                ),
            ),
            expectedJson = """
                {
                  "type": "ROOM_EVENT_LOAD_AUDIO_SOURCE",
                  "payload": {
                    "commandId": "cmd-play-001",
                    "recordingId": 1001,
                    "mediaFileId": 2001,
                    "sourceUrl": "/api/media/2001"
                  }
                }
            """.trimIndent(),
            expectedType = LoadAudioSourceMessage::class,
        ),
        ServerSample(
            message = ScheduledActionMessage(
                payload = ScheduledActionPayload(
                    commandId = "cmd-seek-001",
                    serverTimeToExecuteMs = 1730844001500,
                    scheduledAction = ScheduledPlaybackAction(
                        action = ScheduledActionType.SEEK,
                        status = PlaybackStatus.PAUSED,
                        recordingId = 1001,
                        mediaFileId = 2001,
                        sourceUrl = "/api/media/2001",
                        positionSeconds = 91.0,
                        version = 9,
                    ),
                ),
            ),
            expectedJson = """
                {
                  "type": "SCHEDULED_ACTION",
                  "payload": {
                    "commandId": "cmd-seek-001",
                    "serverTimeToExecuteMs": 1730844001500,
                    "scheduledAction": {
                      "action": "SEEK",
                      "status": "PAUSED",
                      "recordingId": 1001,
                      "mediaFileId": 2001,
                      "sourceUrl": "/api/media/2001",
                      "positionSeconds": 91.0,
                      "version": 9
                    }
                  }
                }
            """.trimIndent(),
            expectedType = ScheduledActionMessage::class,
        ),
        ServerSample(
            message = DeviceChangeMessage(
                payload = DeviceChangePayload(
                    devices = listOf(
                        PlaybackSyncDevice(deviceId = "web-7c2f"),
                        PlaybackSyncDevice(deviceId = "web-85ab"),
                    ),
                ),
            ),
            expectedJson = """
                {
                  "type": "ROOM_EVENT_DEVICE_CHANGE",
                  "payload": {
                    "devices": [
                      {
                        "deviceId": "web-7c2f"
                      },
                      {
                        "deviceId": "web-85ab"
                      }
                    ]
                  }
                }
            """.trimIndent(),
            expectedType = DeviceChangeMessage::class,
        ),
        ServerSample(
            message = ErrorMessage(
                payload = ErrorPayload(
                    code = PlaybackSyncErrorCode.RECORDING_NOT_PLAYABLE,
                    message = "Recording 1001 has no playable audio asset",
                ),
            ),
            expectedJson = """
                {
                  "type": "ERROR",
                  "payload": {
                    "code": "RECORDING_NOT_PLAYABLE",
                    "message": "Recording 1001 has no playable audio asset"
                  }
                }
            """.trimIndent(),
            expectedType = ErrorMessage::class,
        ),
    )
}
