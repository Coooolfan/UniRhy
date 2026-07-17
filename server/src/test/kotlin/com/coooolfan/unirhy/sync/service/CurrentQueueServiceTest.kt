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

        val rewound = restored.navigate(
            accountId = 42L,
            step = -1,
            expectedVersion = advanced.version,
        )

        assertTrue(rewound.changed)
        assertEquals(0, rewound.queue.currentIndex)
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

    @Test
    fun `getQueue drops recordings removed before current and recomputes index`() {
        val mutableCatalog = MutableCatalog()
        val svc = CurrentQueueService(
            lockManager = PlaybackAccountLockManager(),
            recordingCatalog = mutableCatalog,
            timeProvider = timeProvider,
            urlSigner = MediaUrlSigner("test-signing-key", 3600),
            stateStore = stateStore,
        )
        val initial = svc.replaceQueue(
            accountId = 7L,
            recordingIds = listOf(1001L, 1002L, 1003L),
            currentIndex = 2,
            expectedVersion = 0L,
        ).queue

        mutableCatalog.remove(1001L)
        val queue = svc.getQueue(7L)

        assertEquals(listOf(1002L, 1003L), queue.recordingIds)
        assertEquals(1, queue.currentIndex)
        assertEquals(listOf(1002L, 1003L), queue.items.map { it.recordingId })
        assertTrue(queue.version > initial.version)
    }

    @Test
    fun `getQueue drops the current recording and clears progress`() {
        val mutableCatalog = MutableCatalog()
        val svc = CurrentQueueService(
            lockManager = PlaybackAccountLockManager(),
            recordingCatalog = mutableCatalog,
            timeProvider = timeProvider,
            urlSigner = MediaUrlSigner("test-signing-key", 3600),
            stateStore = stateStore,
        )
        svc.replaceQueue(
            accountId = 8L,
            recordingIds = listOf(1001L, 1002L, 1003L),
            currentIndex = 1,
            expectedVersion = 0L,
        )

        mutableCatalog.remove(1002L)
        val queue = svc.getQueue(8L)

        assertEquals(listOf(1001L, 1003L), queue.recordingIds)
        assertEquals(1, queue.currentIndex)
        assertEquals(PlaybackStatus.PAUSED, queue.playbackStatus)
        assertEquals(0L, queue.positionMs)
    }

    @Test
    fun `getQueue empties the queue when every recording is gone`() {
        val mutableCatalog = MutableCatalog()
        val svc = CurrentQueueService(
            lockManager = PlaybackAccountLockManager(),
            recordingCatalog = mutableCatalog,
            timeProvider = timeProvider,
            urlSigner = MediaUrlSigner("test-signing-key", 3600),
            stateStore = stateStore,
        )
        svc.replaceQueue(
            accountId = 9L,
            recordingIds = listOf(1001L, 1002L),
            currentIndex = 1,
            expectedVersion = 0L,
        )

        mutableCatalog.remove(1001L)
        mutableCatalog.remove(1002L)
        val queue = svc.getQueue(9L)

        assertTrue(queue.recordingIds.isEmpty())
        assertEquals(0, queue.currentIndex)
        assertEquals(PlaybackStatus.PAUSED, queue.playbackStatus)
    }

    @Test
    fun `switching to radio forces never stop strategy and refills queue`() {
        val catalog = RadioCatalog((1L..200L).toList())
        val svc = newService(catalog)
        val initial = svc.replaceQueue(
            accountId = 10L,
            recordingIds = listOf(1L, 2L, 3L),
            currentIndex = 0,
            expectedVersion = 0L,
        ).queue

        val radio = svc.updateStrategies(
            accountId = 10L,
            playbackStrategy = PlaybackStrategy.RADIO,
            stopStrategy = StopStrategy.LIST,
            expectedVersion = initial.version,
        ).queue

        assertEquals(PlaybackStrategy.RADIO, radio.playbackStrategy)
        assertEquals(StopStrategy.NEVER, radio.stopStrategy)
        assertEquals(23, radio.recordingIds.size)
        assertEquals(listOf(1L, 2L, 3L), radio.recordingIds.take(3))
        assertEquals(0, radio.currentIndex)
    }

    @Test
    fun `radio refill excludes recordings already in queue`() {
        val catalog = RadioCatalog((1L..200L).toList())
        val svc = newService(catalog)
        val initial = svc.replaceQueue(
            accountId = 11L,
            recordingIds = listOf(1L, 2L, 3L),
            currentIndex = 0,
            expectedVersion = 0L,
        ).queue

        val radio = svc.updateStrategies(
            accountId = 11L,
            playbackStrategy = PlaybackStrategy.RADIO,
            stopStrategy = StopStrategy.NEVER,
            expectedVersion = initial.version,
        ).queue

        assertEquals(setOf(1L, 2L, 3L), catalog.lastExcluding)
        assertEquals(radio.recordingIds.size, radio.recordingIds.distinct().size)
    }

    @Test
    fun `radio navigation near tail refills before advancing`() {
        val catalog = RadioCatalog((1L..200L).toList())
        val svc = newService(catalog)
        val initial = svc.replaceQueue(
            accountId = 12L,
            recordingIds = (1L..6L).toList(),
            currentIndex = 0,
            expectedVersion = 0L,
        ).queue
        var version = svc.updateStrategies(
            accountId = 12L,
            playbackStrategy = PlaybackStrategy.RADIO,
            stopStrategy = StopStrategy.NEVER,
            expectedVersion = initial.version,
        ).queue.version

        repeat(10) {
            val advanced = svc.navigateToNext(accountId = 12L, expectedVersion = version)
            assertTrue(advanced.changed)
            version = advanced.queue.version
        }

        val queue = svc.getQueue(12L)
        assertEquals(10, queue.currentIndex)
        assertTrue(queue.recordingIds.size > 6)
    }

    @Test
    fun `radio with empty library degrades to noop`() {
        val svc = newService(RadioCatalog(emptyList()))

        val radio = svc.updateStrategies(
            accountId = 13L,
            playbackStrategy = PlaybackStrategy.RADIO,
            stopStrategy = StopStrategy.NEVER,
            expectedVersion = 0L,
        ).queue
        val advanced = svc.navigateToNext(accountId = 13L, expectedVersion = radio.version)

        assertTrue(radio.recordingIds.isEmpty())
        assertFalse(advanced.changed)
    }

    @Test
    fun `radio trims history beyond keep limit`() {
        val catalog = RadioCatalog((1L..200L).toList())
        val svc = newService(catalog)
        val initial = svc.replaceQueue(
            accountId = 14L,
            recordingIds = (1L..60L).toList(),
            currentIndex = 59,
            expectedVersion = 0L,
        ).queue

        val radio = svc.updateStrategies(
            accountId = 14L,
            playbackStrategy = PlaybackStrategy.RADIO,
            stopStrategy = StopStrategy.NEVER,
            expectedVersion = initial.version,
        ).queue

        assertEquals(50, radio.currentIndex)
        assertEquals(60L, radio.recordingIds[radio.currentIndex])
        assertEquals(10L, radio.recordingIds.first())
        assertEquals(71, radio.recordingIds.size)
    }

    private fun newService(catalog: CurrentQueueRecordingCatalog): CurrentQueueService {
        return CurrentQueueService(
            lockManager = PlaybackAccountLockManager(),
            recordingCatalog = catalog,
            timeProvider = timeProvider,
            urlSigner = MediaUrlSigner("test-signing-key", 3600),
            stateStore = stateStore,
        )
    }

    private class RadioCatalog(libraryIds: List<Long>) : CurrentQueueRecordingCatalog {
        private val library = libraryIds.toList()
        var lastExcluding: Set<Long>? = null

        override fun getExistingRecordingIds(recordingIds: Set<Long>): Set<Long> {
            return library.filterTo(mutableSetOf()) { it in recordingIds }
        }

        override fun loadResolvedRecordings(recordingIds: Set<Long>): List<ResolvedQueueRecording> {
            return library.filter { it in recordingIds }.map { recordingId ->
                ResolvedQueueRecording(recordingId, recordingId, "Track $recordingId", "Artist $recordingId", null, 180_000, recordingId + 4_000L)
            }
        }

        override fun randomRecordingIds(count: Int, excluding: Set<Long>): List<Long> {
            lastExcluding = excluding
            return library.asSequence().filterNot { it in excluding }.take(count).toList()
        }
    }

    private class MutableCatalog : CurrentQueueRecordingCatalog {
        private val recordings = linkedMapOf(
            1001L to ResolvedQueueRecording(1001L, 3001L, "Track 1", "Artist 1", null, 180_000, 5001L),
            1002L to ResolvedQueueRecording(1002L, 3002L, "Track 2", "Artist 2", null, 210_000, 5002L),
            1003L to ResolvedQueueRecording(1003L, 3003L, "Track 3", "Artist 3", null, 240_000, 5003L),
        )

        fun remove(recordingId: Long) {
            recordings.remove(recordingId)
        }

        override fun getExistingRecordingIds(recordingIds: Set<Long>): Set<Long> {
            return recordings.keys.intersect(recordingIds)
        }

        override fun loadResolvedRecordings(recordingIds: Set<Long>): List<ResolvedQueueRecording> {
            return recordingIds.mapNotNull(recordings::get)
        }

        override fun randomRecordingIds(count: Int, excluding: Set<Long>): List<Long> {
            return recordings.keys.filterNot { it in excluding }.take(count)
        }
    }

    private class FakeCatalog : CurrentQueueRecordingCatalog {
        private val recordings = mapOf(
            1001L to ResolvedQueueRecording(1001L, 3001L, "Track 1", "Artist 1", null, 180_000, 5001L),
            1002L to ResolvedQueueRecording(1002L, 3002L, "Track 2", "Artist 2", null, 210_000, 5002L),
            1003L to ResolvedQueueRecording(1003L, 3003L, "Track 3", "Artist 3", null, 240_000, 5003L),
        )

        override fun getExistingRecordingIds(recordingIds: Set<Long>): Set<Long> {
            return recordings.keys.intersect(recordingIds)
        }

        override fun loadResolvedRecordings(recordingIds: Set<Long>): List<ResolvedQueueRecording> {
            return recordingIds.mapNotNull(recordings::get)
        }

        override fun randomRecordingIds(count: Int, excluding: Set<Long>): List<Long> {
            return recordings.keys.filterNot { it in excluding }.take(count)
        }
    }
}
