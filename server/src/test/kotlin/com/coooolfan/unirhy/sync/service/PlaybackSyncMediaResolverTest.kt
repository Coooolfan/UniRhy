package com.coooolfan.unirhy.sync.service

import com.coooolfan.unirhy.sync.protocol.PlaybackSyncErrorCode
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PlaybackSyncMediaResolverTest {
    @Test
    fun `validate playable recording succeeds when recording has audio`() {
        val catalog = CountingPlaybackSyncMediaCatalog(
            recordingExistsResult = true,
            recordingHasPlayableAudioResult = true,
        )
        val resolver = PlaybackSyncMediaResolver(catalog)

        resolver.validatePlayableRecording(1001L)

        assertEquals(1, catalog.recordingExistsCallCount)
        assertEquals(1, catalog.recordingHasPlayableAudioCallCount)
    }

    @Test
    fun `validate playable recording fails when recording is missing`() {
        val catalog = CountingPlaybackSyncMediaCatalog(
            recordingExistsResult = false,
            recordingHasPlayableAudioResult = true,
        )
        val resolver = PlaybackSyncMediaResolver(catalog)

        val exception = assertFailsWith<PlaybackSyncProtocolException> {
            resolver.validatePlayableRecording(1001L)
        }

        assertEquals(PlaybackSyncErrorCode.RECORDING_NOT_FOUND, exception.code)
        assertEquals(1, catalog.recordingExistsCallCount)
        assertEquals(0, catalog.recordingHasPlayableAudioCallCount)
    }

    @Test
    fun `validate playable recording fails when recording has no playable audio`() {
        val catalog = CountingPlaybackSyncMediaCatalog(
            recordingExistsResult = true,
            recordingHasPlayableAudioResult = false,
        )
        val resolver = PlaybackSyncMediaResolver(catalog)

        val exception = assertFailsWith<PlaybackSyncProtocolException> {
            resolver.validatePlayableRecording(1001L)
        }

        assertEquals(PlaybackSyncErrorCode.RECORDING_NOT_PLAYABLE, exception.code)
        assertEquals(1, catalog.recordingExistsCallCount)
        assertEquals(1, catalog.recordingHasPlayableAudioCallCount)
    }
}

private class CountingPlaybackSyncMediaCatalog(
    private val recordingExistsResult: Boolean,
    private val recordingHasPlayableAudioResult: Boolean,
) : PlaybackSyncMediaCatalog {
    var recordingExistsCallCount: Int = 0
        private set
    var recordingHasPlayableAudioCallCount: Int = 0
        private set

    override fun recordingExists(id: Long): Boolean {
        recordingExistsCallCount += 1
        return recordingExistsResult
    }

    override fun recordingHasPlayableAudio(id: Long): Boolean {
        recordingHasPlayableAudioCallCount += 1
        return recordingHasPlayableAudioResult
    }
}
