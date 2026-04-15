package com.coooolfan.unirhy.sync.service

import com.coooolfan.unirhy.service.MediaUrlSigner
import com.coooolfan.unirhy.sync.protocol.PlaybackStatus
import com.coooolfan.unirhy.sync.protocol.ScheduledActionType
import com.coooolfan.unirhy.sync.support.InMemoryCurrentQueueStateStore
import com.coooolfan.unirhy.sync.support.TestPlaybackSyncTimeProvider
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PlaybackSessionServiceTest {
    private lateinit var currentQueueService: CurrentQueueService
    private lateinit var service: PlaybackSessionService

    @BeforeEach
    fun setUp() {
        val timeProvider = TestPlaybackSyncTimeProvider(1_000L)
        val lockManager = PlaybackAccountLockManager()
        currentQueueService = CurrentQueueService(
            lockManager = lockManager,
            recordingCatalog = FakePlaybackQueueRecordingCatalog(),
            timeProvider = timeProvider,
            urlSigner = MediaUrlSigner("test-signing-key", 3600),
            stateStore = InMemoryCurrentQueueStateStore(),
        )
        currentQueueService.replaceQueue(
            accountId = 42L,
            recordingIds = listOf(1001L, 1002L),
            currentIndex = 0,
            expectedVersion = 0L,
        )
        service = PlaybackSessionService(
            lockManager = lockManager,
            currentQueueService = currentQueueService,
        )
    }

    @Test
    fun `complete pending play updates authority state`() {
        service.createPendingPlay(
            accountId = 42L,
            commandId = "cmd-play-001",
            initiatorDeviceId = "web-a",
            currentIndex = 1,
            recordingId = 1002L,
            positionSeconds = 12.5,
            nowMs = 1_000L,
            timeoutAtMs = 4_000L,
        )

        val scheduledAction = service.completePendingPlay(
            accountId = 42L,
            commandId = "cmd-play-001",
            nowMs = 1_200L,
            executeAtMs = 1_600L,
        )

        assertNotNull(scheduledAction)
        assertEquals(ScheduledActionType.PLAY, scheduledAction.scheduledAction.action)
        assertEquals(1, scheduledAction.scheduledAction.currentIndex)
        val state = service.getOrCreateState(42L)
        assertEquals(PlaybackStatus.PLAYING, state.status)
        assertEquals(1, state.currentIndex)
        assertEquals(12.5, state.positionSeconds)
    }

    @Test
    fun `schedule pause from current state keeps current index`() {
        service.createPendingPlay(
            accountId = 42L,
            commandId = "cmd-play-001",
            initiatorDeviceId = "web-a",
            currentIndex = 0,
            recordingId = 1001L,
            positionSeconds = 12.5,
            nowMs = 1_000L,
            timeoutAtMs = 4_000L,
        )
        service.completePendingPlay(
            accountId = 42L,
            commandId = "cmd-play-001",
            nowMs = 1_200L,
            executeAtMs = 1_600L,
        )

        val scheduledAction = service.schedulePauseFromCurrentState(
            accountId = 42L,
            commandId = "cmd-pause-001",
            nowMs = 2_200L,
            executeAtMs = 2_600L,
        )

        assertEquals(ScheduledActionType.PAUSE, scheduledAction.scheduledAction.action)
        assertEquals(0, scheduledAction.scheduledAction.currentIndex)
        assertEquals(13.1, scheduledAction.scheduledAction.positionSeconds)
    }

    @Test
    fun `seek updates queue-backed state version`() {
        val queue = currentQueueService.getQueue(42L)

        val scheduledAction = service.scheduleSeek(
            accountId = 42L,
            commandId = "cmd-seek-001",
            currentIndex = 0,
            positionSeconds = 24.0,
            nowMs = 1_500L,
            executeAtMs = 1_900L,
            expectedVersion = queue.version,
        )

        assertEquals(ScheduledActionType.SEEK, scheduledAction.scheduledAction.action)
        assertEquals(24.0, scheduledAction.scheduledAction.positionSeconds)
        assertEquals(queue.version + 1, scheduledAction.scheduledAction.version)
    }
}

private class FakePlaybackQueueRecordingCatalog : CurrentQueueRecordingCatalog {
    override fun getExistingRecordingIds(recordingIds: Set<Long>): Set<Long> = recordingIds

    override fun countWorks(): Int = 2

    override fun loadResolvedRecordings(recordingIds: Set<Long>): List<ResolvedQueueRecording> {
        return recordingIds.map { recordingId ->
            ResolvedQueueRecording(
                recordingId = recordingId,
                workId = recordingId + 2_000L,
                title = "Track $recordingId",
                artistLabel = "Artist $recordingId",
                coverMediaFileId = null,
                durationMs = 180_000,
            )
        }
    }

    override fun findFirstSimilarRecordingId(
        recordingId: Long,
        excludedWorkIds: Set<Long>,
    ): Long? = null
}
