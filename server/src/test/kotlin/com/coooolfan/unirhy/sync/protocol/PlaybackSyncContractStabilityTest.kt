package com.coooolfan.unirhy.sync.protocol

import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackSyncContractStabilityTest {

    @Test
    fun `error codes stay frozen`() {
        assertEquals(
            listOf(
                "INVALID_MESSAGE",
                "UNSUPPORTED_MESSAGE",
                "VERSION_CONFLICT",
                "RECORDING_NOT_FOUND",
                "MEDIA_FILE_NOT_FOUND",
                "RECORDING_NOT_PLAYABLE",
                "SYNC_NOT_READY",
                "INTERNAL_ERROR",
            ),
            enumValues<PlaybackSyncErrorCode>().map { it.name },
        )
    }

    @Test
    fun `message types stay frozen`() {
        assertEquals(
            listOf(
                "HELLO",
                "NTP_REQUEST",
                "PLAY",
                "PAUSE",
                "SEEK",
                "AUDIO_SOURCE_LOADED",
                "SYNC",
                "NTP_RESPONSE",
                "SNAPSHOT",
                "ROOM_EVENT_LOAD_AUDIO_SOURCE",
                "ROOM_EVENT_QUEUE_CHANGE",
                "SCHEDULED_ACTION",
                "ROOM_EVENT_DEVICE_CHANGE",
                "ERROR",
            ),
            enumValues<PlaybackSyncMessageType>().map { it.name },
        )
    }
}
