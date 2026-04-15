package com.coooolfan.unirhy.sync.service

import com.coooolfan.unirhy.service.MediaUrlSigner
import com.coooolfan.unirhy.sync.protocol.PlaybackStatus
import com.coooolfan.unirhy.sync.protocol.PlaybackStrategy
import com.coooolfan.unirhy.sync.protocol.StopStrategy
import com.coooolfan.unirhy.sync.support.InMemoryCurrentQueueStateStore
import com.coooolfan.unirhy.sync.support.TestPlaybackSyncTimeProvider
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CurrentQueueServiceTest {
    private lateinit var timeProvider: TestPlaybackSyncTimeProvider
    private lateinit var stateStore: InMemoryCurrentQueueStateStore
    private lateinit var service: CurrentQueueService

    @BeforeEach
    fun setUp() {
        timeProvider = TestPlaybackSyncTimeProvider(1_000L)
        stateStore = InMemoryCurrentQueueStateStore()
        service = CurrentQueueService(
            lockManager = PlaybackAccountLockManager(),
            recordingCatalog = FakeCatalog(),
            timeProvider = timeProvider,
            urlSigner = MediaUrlSigner("test-signing-key", 3600),
            stateStore = stateStore,
        )
    }

    @Test
    fun `replace queue returns metadata and increments version`() {
        val queue = service.replaceQueue(
            accountId = 42L,
            recordingIds = listOf(1001L, 1002L),
            currentIndex = 1,
            expectedVersion = 0L,
        ).queue

        assertEquals(listOf(1001L, 1002L), queue.recordingIds)
        assertEquals(1, queue.currentIndex)
        assertEquals(PlaybackStatus.PAUSED, queue.playbackStatus)
        assertEquals(1L, queue.version)
        assertEquals(listOf(1001L, 1002L), queue.items.map { it.recordingId })
    }

    @Test
    fun `remove index keeps duplicate recording ids unambiguous`() {
        val initial = service.replaceQueue(
            accountId = 42L,
            recordingIds = listOf(1001L, 1001L, 1002L),
            currentIndex = 1,
            expectedVersion = 0L,
        ).queue

        val updated = service.removeAt(
            accountId = 42L,
            index = 0,
            expectedVersion = initial.version,
        ).queue

        assertEquals(listOf(1001L, 1002L), updated.recordingIds)
        assertEquals(0, updated.currentIndex)
        assertEquals(2L, updated.version)
    }

    @Test
    fun `shuffle order survives reload`() {
        val initial = service.replaceQueue(
            accountId = 42L,
            recordingIds = listOf(1001L, 1002L, 1003L),
            currentIndex = 0,
            expectedVersion = 0L,
        ).queue
        val shuffled = service.updateStrategies(
            accountId = 42L,
            playbackStrategy = PlaybackStrategy.SHUFFLE,
            stopStrategy = StopStrategy.LIST,
            expectedVersion = initial.version,
        ).queue
        val advanced = service.navigateToNext(
            accountId = 42L,
            expectedVersion = shuffled.version,
        ).queue

        val restored = CurrentQueueService(
            lockManager = PlaybackAccountLockManager(),
            recordingCatalog = FakeCatalog(),
            timeProvider = timeProvider,
            urlSigner = MediaUrlSigner("test-signing-key", 3600),
            stateStore = stateStore,
        )

        val rewound = restored.navigateToPrevious(
            accountId = 42L,
            expectedVersion = advanced.version,
        )

        assertTrue(rewound.changed)
        assertEquals(0, rewound.queue.currentIndex)
    }

    @Test
    fun `radio appends similar recordings`() {
        val initial = service.replaceQueue(
            accountId = 42L,
            recordingIds = listOf(1001L),
            currentIndex = 0,
            expectedVersion = 0L,
        ).queue
        val radio = service.updateStrategies(
            accountId = 42L,
            playbackStrategy = PlaybackStrategy.RADIO,
            stopStrategy = StopStrategy.LIST,
            expectedVersion = initial.version,
        ).queue

        val advanced = service.navigateToNext(
            accountId = 42L,
            expectedVersion = radio.version,
        ).queue

        assertEquals(listOf(1001L, 1002L), advanced.recordingIds)
        assertEquals(1, advanced.currentIndex)
    }

    @Test
    fun `stale version returns conflict`() {
        service.replaceQueue(
            accountId = 42L,
            recordingIds = listOf(1001L),
            currentIndex = 0,
            expectedVersion = 0L,
        )

        val error = assertFailsWith<ResponseStatusException> {
            service.appendToQueue(
                accountId = 42L,
                recordingIds = listOf(1002L),
                expectedVersion = 0L,
            )
        }

        assertEquals(HttpStatus.CONFLICT, error.statusCode)
    }

    @Test
    fun `invalid queued recording fails with conflict`() {
        val error = assertFailsWith<ResponseStatusException> {
            service.replaceQueue(
                accountId = 42L,
                recordingIds = listOf(9999L),
                currentIndex = 0,
                expectedVersion = 0L,
            )
        }

        assertEquals(HttpStatus.CONFLICT, error.statusCode)
    }

    @Test
    fun `no next item in sequential mode is noop`() {
        val initial = service.replaceQueue(
            accountId = 42L,
            recordingIds = listOf(1001L),
            currentIndex = 0,
            expectedVersion = 0L,
        ).queue

        val advanced = service.navigateToNext(
            accountId = 42L,
            expectedVersion = initial.version,
        )

        assertFalse(advanced.changed)
        assertEquals(0, advanced.queue.currentIndex)
    }

    private class FakeCatalog : CurrentQueueRecordingCatalog {
        private val recordings = mapOf(
            1001L to ResolvedQueueRecording(1001L, 3001L, "Track 1", "Artist 1", null, 180_000),
            1002L to ResolvedQueueRecording(1002L, 3002L, "Track 2", "Artist 2", null, 210_000),
            1003L to ResolvedQueueRecording(1003L, 3003L, "Track 3", "Artist 3", null, 240_000),
        )

        override fun getExistingRecordingIds(recordingIds: Set<Long>): Set<Long> {
            return recordings.keys.intersect(recordingIds)
        }

        override fun countWorks(): Int = recordings.size

        override fun loadResolvedRecordings(recordingIds: Set<Long>): List<ResolvedQueueRecording> {
            return recordingIds.mapNotNull(recordings::get)
        }

        override fun findFirstSimilarRecordingId(
            recordingId: Long,
            excludedWorkIds: Set<Long>,
        ): Long? {
            return when (recordingId) {
                1001L -> 1002L
                1002L -> 1003L
                else -> null
            }?.takeIf { recordings.getValue(it).workId !in excludedWorkIds }
        }
    }
}
