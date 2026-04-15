package com.coooolfan.unirhy.sync.protocol

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlin.test.Test
import kotlin.test.assertTrue

class PlaybackSyncProtocolSerializationTest {
    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `play payload serializes current index and version`() {
        val json = objectMapper.writeValueAsString(
            PlayMessage(
                payload = PlaybackControlPayload(
                    commandId = "cmd-play-001",
                    deviceId = "web-7c2f",
                    currentIndex = 1,
                    positionSeconds = 12.5,
                    version = 9L,
                ),
            ),
        )

        assertTrue(json.contains(""""currentIndex":1"""))
        assertTrue(json.contains(""""version":9"""))
    }

    @Test
    fun `snapshot payload serializes queue and playback state with current index`() {
        val json = objectMapper.writeValueAsString(
            SnapshotMessage(
                payload = SnapshotPayload(
                    state = AccountPlaybackStateDto(
                        status = PlaybackStatus.PAUSED,
                        currentIndex = 1,
                        positionSeconds = 36.25,
                        serverTimeToExecuteMs = 1_730_844_000_500,
                        version = 8L,
                        updatedAtMs = 1_730_844_000_200,
                    ),
                    queue = CurrentQueueDto(
                        items = listOf(
                            CurrentQueueItemDto(
                                recordingId = 1001L,
                                title = "Track 1",
                                artistLabel = "Artist 1",
                                durationMs = 180_000L,
                            ),
                        ),
                        recordingIds = listOf(1001L),
                        currentIndex = 0,
                        playbackStrategy = PlaybackStrategy.SEQUENTIAL,
                        stopStrategy = StopStrategy.LIST,
                        playbackStatus = PlaybackStatus.PAUSED,
                        positionMs = 36_250L,
                        serverTimeToExecuteMs = 1_730_844_000_500,
                        version = 8L,
                        updatedAtMs = 1_730_844_000_200,
                    ),
                    serverNowMs = 1_730_844_000_300,
                ),
            ),
        )

        assertTrue(json.contains(""""currentIndex":1"""))
        assertTrue(json.contains(""""recordingIds":[1001]"""))
        assertTrue(json.contains(""""positionMs":36250"""))
    }

    @Test
    fun `scheduled action serializes current index`() {
        val json = objectMapper.writeValueAsString(
            ScheduledActionMessage(
                payload = ScheduledActionPayload(
                    commandId = "cmd-play-001",
                    serverTimeToExecuteMs = 1_730_844_000_600,
                    scheduledAction = ScheduledPlaybackAction(
                        action = ScheduledActionType.PLAY,
                        status = PlaybackStatus.PLAYING,
                        currentIndex = 2,
                        positionSeconds = 24.0,
                        version = 10L,
                    ),
                ),
            ),
        )

        assertTrue(json.contains(""""currentIndex":2"""))
        assertTrue(json.contains(""""version":10"""))
    }
}
