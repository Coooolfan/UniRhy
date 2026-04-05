package com.coooolfan.unirhy.sync.service

import com.coooolfan.unirhy.service.MediaUrlSigner
import com.coooolfan.unirhy.sync.protocol.PlaybackStrategy
import com.coooolfan.unirhy.sync.protocol.StopStrategy
import com.coooolfan.unirhy.sync.support.TestPlaybackSyncTimeProvider
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.server.ResponseStatusException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

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
        assertEquals(PlaybackStrategy.SEQUENTIAL, queue.playbackStrategy)
        assertEquals(StopStrategy.LIST, queue.stopStrategy)
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
    fun `navigate to next stops at list end for sequential strategy`() {
        service.replaceQueue(
            accountId = 42L,
            recordings = service.resolvePlayableRecordings(listOf(1001L, 1002L)),
            currentIndex = 1,
        )

        val advanced = service.navigateToNext(42L)

        assertFalse(advanced.changed)
        assertEquals(advanced.previousCurrentEntry?.entryId, advanced.queue.currentEntryId)
    }

    @Test
    fun `shuffle previous follows realized history`() {
        val initial = service.replaceQueue(
            accountId = 42L,
            recordings = service.resolvePlayableRecordings(listOf(1001L, 1002L, 1003L)),
            currentIndex = 0,
        )
        service.updateStrategies(42L, PlaybackStrategy.SHUFFLE, StopStrategy.LIST)

        val advanced = service.navigateToNext(42L)
        assertTrue(advanced.changed)
        assertNotEquals(initial.queue.currentEntryId, advanced.queue.currentEntryId)

        val rewound = service.navigateToPrevious(42L)
        assertTrue(rewound.changed)
        assertEquals(initial.queue.currentEntryId, rewound.queue.currentEntryId)
    }

    @Test
    fun `radio next appends similar first result`() {
        service.replaceQueue(
            accountId = 42L,
            recordings = service.resolvePlayableRecordings(listOf(1001L)),
            currentIndex = 0,
        )
        service.updateStrategies(42L, PlaybackStrategy.RADIO, StopStrategy.LIST)

        val advanced = service.navigateToNext(42L)

        assertTrue(advanced.changed)
        assertEquals(2, advanced.queue.items.size)
        assertEquals(1002L, advanced.queue.items.last().recordingId)
        assertEquals(advanced.queue.items.last().entryId, advanced.queue.currentEntryId)
    }

    @Test
    fun `radio next reuses realized history before appending`() {
        val initial = service.replaceQueue(
            accountId = 42L,
            recordings = service.resolvePlayableRecordings(listOf(1001L)),
            currentIndex = 0,
        )
        service.updateStrategies(42L, PlaybackStrategy.RADIO, StopStrategy.LIST)

        val firstAdvance = service.navigateToNext(42L)
        assertTrue(firstAdvance.changed)
        assertEquals(listOf(1001L, 1002L), firstAdvance.queue.items.map { it.recordingId })

        val rewound = service.navigateToPrevious(42L)
        assertTrue(rewound.changed)
        assertEquals(initial.queue.currentEntryId, rewound.queue.currentEntryId)

        val secondAdvance = service.navigateToNext(42L)
        assertTrue(secondAdvance.changed)
        assertEquals(listOf(1001L, 1002L), secondAdvance.queue.items.map { it.recordingId })
        assertEquals(firstAdvance.queue.currentEntryId, secondAdvance.queue.currentEntryId)
    }

    @Test
    fun `radio next skips recent window candidates when generating new tail item`() {
        service.replaceQueue(
            accountId = 42L,
            recordings = service.resolvePlayableRecordings(listOf(1001L)),
            currentIndex = 0,
        )
        service.updateStrategies(42L, PlaybackStrategy.RADIO, StopStrategy.LIST)

        val firstAdvance = service.navigateToNext(42L)
        assertTrue(firstAdvance.changed)
        assertEquals(listOf(1001L, 1002L), firstAdvance.queue.items.map { it.recordingId })

        val secondAdvance = service.navigateToNext(42L)
        assertTrue(secondAdvance.changed)
        assertEquals(listOf(1001L, 1002L, 1003L), secondAdvance.queue.items.map { it.recordingId })
        assertEquals(1003L, secondAdvance.queue.items.last().recordingId)
        assertEquals(secondAdvance.queue.items.last().entryId, secondAdvance.queue.currentEntryId)
    }

    @Test
    fun `radio next stops when similar first result is not playable`() {
        service.replaceQueue(
            accountId = 42L,
            recordings = service.resolvePlayableRecordings(listOf(1003L)),
            currentIndex = 0,
        )
        service.updateStrategies(42L, PlaybackStrategy.RADIO, StopStrategy.LIST)

        val advanced = service.navigateToNext(42L)

        assertFalse(advanced.changed)
        assertEquals(1, advanced.queue.items.size)
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
            1001L to ResolvedQueueRecording(1001L, 2001L, 3001L, "Track 1", "Artist 1", 4001L, 180_000),
            1002L to ResolvedQueueRecording(1002L, 2002L, 3002L, "Track 2", "Artist 2", null, 210_000),
            1003L to ResolvedQueueRecording(1003L, 2003L, 3003L, "Track 3", "Artist 3", null, 240_000),
        )
        private val similarRecordingIds = mapOf(
            1001L to listOf(1002L, 1003L),
            1002L to listOf(1001L, 1003L),
            1003L to listOf(9999L),
        )

        override fun getExistingRecordingIds(recordingIds: Set<Long>): Set<Long> {
            return recordings.keys.intersect(recordingIds)
        }

        override fun countWorks(): Int = recordings.values.map(ResolvedQueueRecording::workId).distinct().size

        override fun loadResolvedRecordings(
            recordingIds: Set<Long>,
            requiredMediaFileId: Long?,
        ): List<ResolvedQueueRecording> {
            return recordingIds.mapNotNull { recordingId ->
                recordings[recordingId]
                    ?.takeIf { requiredMediaFileId == null || it.mediaFileId == requiredMediaFileId }
            }
        }

        override fun findFirstSimilarRecordingId(
            recordingId: Long,
            excludedWorkIds: Set<Long>,
        ): Long? {
            return similarRecordingIds[recordingId]
                ?.firstOrNull { similarRecordingId ->
                    recordings[similarRecordingId]?.workId !in excludedWorkIds
                }
        }
    }
}
