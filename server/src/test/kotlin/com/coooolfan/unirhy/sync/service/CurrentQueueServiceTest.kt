package com.coooolfan.unirhy.sync.service

import com.coooolfan.unirhy.service.MediaUrlSigner
import com.coooolfan.unirhy.sync.support.TestPlaybackSyncTimeProvider
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.server.ResponseStatusException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CurrentQueueServiceTest {
    private lateinit var timeProvider: TestPlaybackSyncTimeProvider
    private lateinit var service: CurrentQueueService

    @BeforeEach
    fun setUp() {
        timeProvider = TestPlaybackSyncTimeProvider(1_000L)
        service = CurrentQueueService(
            lockManager = PlaybackAccountLockManager(),
            recordingCatalog = FakeCatalog(),
            timeProvider = timeProvider,
            urlSigner = MediaUrlSigner("test-signing-key", 3600),
        )
    }

    @Test
    fun `replace queue sets current entry and increments version`() {
        val queue = service.replaceQueue(
            accountId = 42L,
            recordings = service.resolvePlayableRecordings(listOf(1001L, 1002L)),
            currentIndex = 1,
        ).queue

        assertEquals(2, queue.items.size)
        assertEquals(queue.items[1].entryId, queue.currentEntryId)
        assertEquals(1L, queue.version)
    }

    @Test
    fun `remove current entry advances to next item`() {
        val initial = service.replaceQueue(
            accountId = 42L,
            recordings = service.resolvePlayableRecordings(listOf(1001L, 1002L, 1003L)),
            currentIndex = 1,
        ).queue

        val updated = service.removeEntry(
            accountId = 42L,
            entryId = initial.currentEntryId!!,
        ).queue

        assertEquals(2, updated.items.size)
        assertEquals(updated.items[1].entryId, updated.currentEntryId)
    }

    @Test
    fun `advance to next loops back to first item`() {
        val initial = service.replaceQueue(
            accountId = 42L,
            recordings = service.resolvePlayableRecordings(listOf(1001L, 1002L)),
            currentIndex = 1,
        )

        val advanced = service.advanceToNext(42L)!!

        assertEquals(initial.queue.items[0].entryId, advanced.queue.currentEntryId)
    }

    @Test
    fun `resolve playable recordings rejects non-playable recording`() {
        val error = assertFailsWith<ResponseStatusException> {
            service.resolvePlayableRecordings(listOf(9999L))
        }

        assertEquals(404, error.statusCode.value())
    }

    private class FakeCatalog : CurrentQueueRecordingCatalog {
        private val recordings = mapOf(
            1001L to ResolvedQueueRecording(1001L, 2001L, "Track 1", "Artist 1", 3001L, 180_000),
            1002L to ResolvedQueueRecording(1002L, 2002L, "Track 2", "Artist 2", null, 210_000),
            1003L to ResolvedQueueRecording(1003L, 2003L, "Track 3", "Artist 3", null, 240_000),
        )

        override fun getExistingRecordingIds(recordingIds: Set<Long>): Set<Long> {
            return recordings.keys.intersect(recordingIds)
        }

        override fun loadResolvedRecordings(
            recordingIds: Set<Long>,
            requiredMediaFileId: Long?,
        ): List<ResolvedQueueRecording> {
            return recordingIds.mapNotNull { recordingId ->
                recordings[recordingId]?.takeIf { requiredMediaFileId == null || it.mediaFileId == requiredMediaFileId }
            }
        }
    }
}
