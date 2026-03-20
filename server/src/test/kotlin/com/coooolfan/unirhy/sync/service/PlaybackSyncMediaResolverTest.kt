package com.coooolfan.unirhy.sync.service

import com.coooolfan.unirhy.sync.protocol.PlaybackSyncErrorCode
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PlaybackSyncMediaResolverTest {
    @Test
    fun `resolve happy path only checks recording media linkage once`() {
        val catalog = CountingPlaybackSyncMediaCatalog(
            recordingExistsResult = true,
            mediaFileExistsResult = true,
            recordingHasMediaFileResult = true,
        )
        val resolver = PlaybackSyncMediaResolver(catalog)

        val resolved = resolver.resolve(1001L, 2001L)

        assertEquals(1001L, resolved.recordingId)
        assertEquals(2001L, resolved.mediaFileId)
        assertEquals(0, catalog.recordingExistsCallCount)
        assertEquals(0, catalog.mediaFileExistsCallCount)
        assertEquals(1, catalog.recordingHasMediaFileCallCount)
    }

    @Test
    fun `resolve fails when recording is missing`() {
        val catalog = CountingPlaybackSyncMediaCatalog(
            recordingExistsResult = false,
            mediaFileExistsResult = true,
            recordingHasMediaFileResult = false,
        )
        val resolver = PlaybackSyncMediaResolver(catalog)

        val exception = assertFailsWith<PlaybackSyncProtocolException> {
            resolver.resolve(1001L, 2001L)
        }

        assertEquals(PlaybackSyncErrorCode.RECORDING_NOT_FOUND, exception.code)
        assertEquals(1, catalog.recordingHasMediaFileCallCount)
        assertEquals(1, catalog.recordingExistsCallCount)
        assertEquals(0, catalog.mediaFileExistsCallCount)
    }

    @Test
    fun `resolve fails when media file is missing`() {
        val catalog = CountingPlaybackSyncMediaCatalog(
            recordingExistsResult = true,
            mediaFileExistsResult = false,
            recordingHasMediaFileResult = false,
        )
        val resolver = PlaybackSyncMediaResolver(catalog)

        val exception = assertFailsWith<PlaybackSyncProtocolException> {
            resolver.resolve(1001L, 2001L)
        }

        assertEquals(PlaybackSyncErrorCode.MEDIA_FILE_NOT_FOUND, exception.code)
        assertEquals(1, catalog.recordingHasMediaFileCallCount)
        assertEquals(1, catalog.recordingExistsCallCount)
        assertEquals(1, catalog.mediaFileExistsCallCount)
    }

    @Test
    fun `resolve fails when recording has no playable media`() {
        val catalog = CountingPlaybackSyncMediaCatalog(
            recordingExistsResult = true,
            mediaFileExistsResult = true,
            recordingHasMediaFileResult = false,
        )
        val resolver = PlaybackSyncMediaResolver(catalog)

        val exception = assertFailsWith<PlaybackSyncProtocolException> {
            resolver.resolve(1001L, 2001L)
        }

        assertEquals(PlaybackSyncErrorCode.RECORDING_NOT_PLAYABLE, exception.code)
        assertEquals(1, catalog.recordingHasMediaFileCallCount)
        assertEquals(1, catalog.recordingExistsCallCount)
        assertEquals(1, catalog.mediaFileExistsCallCount)
    }
}

private class CountingPlaybackSyncMediaCatalog(
    private val recordingExistsResult: Boolean,
    private val mediaFileExistsResult: Boolean,
    private val recordingHasMediaFileResult: Boolean,
) : PlaybackSyncMediaCatalog {
    var recordingExistsCallCount: Int = 0
        private set
    var mediaFileExistsCallCount: Int = 0
        private set
    var recordingHasMediaFileCallCount: Int = 0
        private set

    override fun recordingExists(id: Long): Boolean {
        recordingExistsCallCount += 1
        return recordingExistsResult
    }

    override fun mediaFileExists(id: Long): Boolean {
        mediaFileExistsCallCount += 1
        return mediaFileExistsResult
    }

    override fun recordingHasMediaFile(
        recordingId: Long,
        mediaFileId: Long,
    ): Boolean {
        recordingHasMediaFileCallCount += 1
        return recordingHasMediaFileResult
    }
}
