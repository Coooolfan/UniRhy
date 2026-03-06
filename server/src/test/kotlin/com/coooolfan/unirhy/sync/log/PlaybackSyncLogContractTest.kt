package com.coooolfan.unirhy.sync.log

import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackSyncLogContractTest {

    @Test
    fun `log field keys stay unique and frozen`() {
        assertEquals(
            listOf(
                "event",
                "accountId",
                "deviceId",
                "sessionId",
                "commandId",
                "recordingId",
                "mediaFileId",
                "version",
                "positionSeconds",
                "serverNowMs",
                "executeAtMs",
                "scheduleDelayMs",
                "rttMs",
                "rttEmaMs",
                "result",
                "reason",
            ).sorted(),
            stringConstantsOf(PlaybackSyncLogFields::class.java),
        )
    }

    @Test
    fun `log event names stay unique and frozen`() {
        assertEquals(
            listOf(
                "playback_sync_connection_opened",
                "playback_sync_connection_closed",
                "playback_sync_hello_received",
                "playback_sync_ntp_request_received",
                "playback_sync_ntp_response_sent",
                "playback_sync_play_request",
                "playback_sync_pause_request",
                "playback_sync_seek_request",
                "playback_sync_pending_play_created",
                "playback_sync_pending_play_replaced",
                "playback_sync_audio_source_loaded",
                "playback_sync_scheduled_action_sent",
                "playback_sync_snapshot_sent",
                "playback_sync_protocol_error",
            ).sorted(),
            stringConstantsOf(PlaybackSyncLogEvents::class.java),
        )
    }

    private fun stringConstantsOf(type: Class<*>): List<String> {
        val values = type.declaredFields
            .filter { Modifier.isStatic(it.modifiers) && it.type == String::class.java }
            .sortedBy { it.name }
            .map { field -> field.get(null) as String }

        assertEquals(values.size, values.toSet().size, "Duplicate values found in ${type.simpleName}")
        return values
    }
}
