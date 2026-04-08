package com.coooolfan.unirhy.sync.service

import com.coooolfan.unirhy.service.MediaUrlSigner
import com.coooolfan.unirhy.sync.model.AccountPlaybackState
import com.coooolfan.unirhy.sync.protocol.PlaybackStatus
import com.coooolfan.unirhy.sync.protocol.ScheduledActionType
import com.coooolfan.unirhy.sync.support.InMemoryCurrentQueueStateStore
import com.coooolfan.unirhy.sync.support.InMemoryPlaybackResumeStateStore
import com.coooolfan.unirhy.sync.support.TestPlaybackSyncTimeProvider
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaybackSessionServiceTest {
    private lateinit var timeProvider: TestPlaybackSyncTimeProvider
    private lateinit var currentQueueService: CurrentQueueService
    private lateinit var resumeStateStore: InMemoryPlaybackResumeStateStore
    private lateinit var service: PlaybackSessionService

    @BeforeEach
    fun setUp() {
        timeProvider = TestPlaybackSyncTimeProvider(1_000L)
        val lockManager = PlaybackAccountLockManager()
        currentQueueService = CurrentQueueService(
            lockManager = lockManager,
            recordingCatalog = FakePlaybackQueueRecordingCatalog(),
            timeProvider = timeProvider,
            urlSigner = MediaUrlSigner("test-signing-key", 3600),
            stateStore = InMemoryCurrentQueueStateStore(),
        )
        resumeStateStore = InMemoryPlaybackResumeStateStore()
        service = PlaybackSessionService(
            lockManager = lockManager,
            timeProvider = timeProvider,
            currentQueueService = currentQueueService,
            resumeStateStore = resumeStateStore,
        )
    }

    @Test
    fun `complete pending play updates authority state and increments version`() {
        service.createPendingPlay(
            accountId = 42L,
            commandId = "cmd-play-001",
            initiatorDeviceId = "web-a",
            recordingId = 1001L,
            positionSeconds = 12.5,
            nowMs = 1_000L,
            timeoutAtMs = 4_000L,
        )

        val scheduledAction = service.completePendingPlay(
            accountId = 42L,
            commandId = "cmd-play-001",
            nowMs = 1_500L,
            executeAtMs = 1_900L,
        )

        assertEquals(ScheduledActionType.PLAY, scheduledAction?.scheduledAction?.action)
        assertEquals(1L, scheduledAction?.scheduledAction?.version)
        assertEquals(12.5, scheduledAction?.scheduledAction?.positionSeconds)

        val state = service.getOrCreateState(42L)
        assertEquals(PlaybackStatus.PLAYING, state.status)
        assertEquals(1001L, state.recordingId)
        assertEquals(12.5, state.positionSeconds)
        assertEquals(1_900L, state.serverTimeToExecuteMs)
        assertEquals(1L, state.version)
        assertNull(service.getPendingPlay(42L))
        assertEquals(state, resumeStateStore.load(42L))
    }

    @Test
    fun `build sync action for playing state recalculates live position`() {
        service.createPendingPlay(
            accountId = 42L,
            commandId = "cmd-play-001",
            initiatorDeviceId = "web-a",
            recordingId = 1001L,
            positionSeconds = 12.5,
            nowMs = 1_000L,
            timeoutAtMs = 4_000L,
        )
        service.completePendingPlay(
            accountId = 42L,
            commandId = "cmd-play-001",
            nowMs = 1_500L,
            executeAtMs = 2_000L,
        )
        timeProvider.setNowMs(3_500L)

        val scheduledAction = service.buildSyncAction(
            accountId = 42L,
            commandId = "sync-web-a-3500",
            nowMs = 3_500L,
            executeAtMs = 5_000L,
        )

        assertEquals(ScheduledActionType.PLAY, scheduledAction.scheduledAction.action)
        assertEquals(PlaybackStatus.PLAYING, scheduledAction.scheduledAction.status)
        assertEquals(14.0, scheduledAction.scheduledAction.positionSeconds)
        assertEquals(1L, scheduledAction.scheduledAction.version)
    }

    @Test
    fun `handle device disconnected completes pending play when remaining devices are loaded`() {
        service.createPendingPlay(
            accountId = 42L,
            commandId = "cmd-play-001",
            initiatorDeviceId = "web-a",
            recordingId = 1001L,
            positionSeconds = 12.5,
            nowMs = 1_000L,
            timeoutAtMs = 4_000L,
        )
        service.markAudioSourceLoaded(
            accountId = 42L,
            commandId = "cmd-play-001",
            deviceId = "web-b",
            recordingId = 1001L,
        )

        val scheduledAction = service.handleDeviceDisconnected(
            accountId = 42L,
            deviceId = "web-a",
            remainingDeviceIds = listOf("web-b"),
            nowMs = 1_400L,
            executeAtMs = 1_800L,
        )

        assertEquals(ScheduledActionType.PLAY, scheduledAction?.scheduledAction?.action)
        assertNull(service.getPendingPlay(42L))
    }

    @Test
    fun `handle device disconnected clears pending play when no devices remain`() {
        service.createPendingPlay(
            accountId = 42L,
            commandId = "cmd-play-001",
            initiatorDeviceId = "web-a",
            recordingId = 1001L,
            positionSeconds = 12.5,
            nowMs = 1_000L,
            timeoutAtMs = 4_000L,
        )

        val scheduledAction = service.handleDeviceDisconnected(
            accountId = 42L,
            deviceId = "web-a",
            remainingDeviceIds = emptyList(),
            nowMs = 1_400L,
            executeAtMs = 1_800L,
        )

        assertNull(scheduledAction)
        assertNull(service.getPendingPlay(42L))
    }

    @Test
    fun `schedule pause from current state recovers live playing position`() {
        service.createPendingPlay(
            accountId = 42L,
            commandId = "cmd-play-001",
            initiatorDeviceId = "web-a",
            recordingId = 1001L,
            positionSeconds = 12.5,
            nowMs = 1_000L,
            timeoutAtMs = 4_000L,
        )
        service.completePendingPlay(
            accountId = 42L,
            commandId = "cmd-play-001",
            nowMs = 1_100L,
            executeAtMs = 1_500L,
        )

        val scheduledAction = service.schedulePauseFromCurrentState(
            accountId = 42L,
            commandId = "auto-disconnect-pause-2500",
            nowMs = 2_500L,
            executeAtMs = 2_900L,
        )

        assertEquals(ScheduledActionType.PAUSE, scheduledAction.scheduledAction.action)
        assertEquals(PlaybackStatus.PAUSED, scheduledAction.scheduledAction.status)
        assertEquals(1001L, scheduledAction.scheduledAction.recordingId)
        assertEquals(13.5, scheduledAction.scheduledAction.positionSeconds)
        assertEquals(2L, scheduledAction.scheduledAction.version)
    }

    @Test
    fun `schedule pause from current state preserves paused position`() {
        service.schedulePause(
            accountId = 42L,
            commandId = "cmd-pause-001",
            recordingId = 1001L,
            positionSeconds = 12.5,
            nowMs = 1_100L,
            executeAtMs = 1_500L,
        )

        val scheduledAction = service.schedulePauseFromCurrentState(
            accountId = 42L,
            commandId = "auto-disconnect-pause-2500",
            nowMs = 2_500L,
            executeAtMs = 2_900L,
        )

        assertEquals(ScheduledActionType.PAUSE, scheduledAction.scheduledAction.action)
        assertEquals(PlaybackStatus.PAUSED, scheduledAction.scheduledAction.status)
        assertEquals(1001L, scheduledAction.scheduledAction.recordingId)
        assertEquals(12.5, scheduledAction.scheduledAction.positionSeconds)
        assertEquals(2L, scheduledAction.scheduledAction.version)
    }

    @Test
    fun `schedule seek preserves paused status`() {
        service.schedulePause(
            accountId = 42L,
            commandId = "cmd-pause-001",
            recordingId = 1001L,
            positionSeconds = 12.5,
            nowMs = 1_100L,
            executeAtMs = 1_500L,
        )

        val scheduledAction = service.scheduleSeek(
            accountId = 42L,
            commandId = "cmd-seek-001",
            recordingId = 1001L,
            positionSeconds = 20.0,
            nowMs = 1_200L,
            executeAtMs = 1_600L,
        )

        assertEquals(ScheduledActionType.SEEK, scheduledAction.scheduledAction.action)
        assertEquals(PlaybackStatus.PAUSED, scheduledAction.scheduledAction.status)
        assertEquals(PlaybackStatus.PAUSED, service.getOrCreateState(42L).status)
    }

    @Test
    fun `fresh service restores persisted paused state`() {
        resumeStateStore.upsert(
            AccountPlaybackState(
                accountId = 42L,
                status = PlaybackStatus.PAUSED,
                recordingId = 1001L,
                positionSeconds = 18.25,
                serverTimeToExecuteMs = 3_200L,
                version = 7L,
                updatedAtMs = 3_100L,
            ),
        )

        val freshService = newService()
        val restored = freshService.getOrCreateState(42L)

        assertEquals(PlaybackStatus.PAUSED, restored.status)
        assertEquals(1001L, restored.recordingId)
        assertEquals(18.25, restored.positionSeconds)
        assertEquals(0L, restored.serverTimeToExecuteMs)
        assertEquals(7L, restored.version)
        assertEquals(3_100L, restored.updatedAtMs)
    }

    @Test
    fun `fresh service downgrades persisted playing state to paused`() {
        resumeStateStore.upsert(
            AccountPlaybackState(
                accountId = 42L,
                status = PlaybackStatus.PLAYING,
                recordingId = 1001L,
                positionSeconds = 22.5,
                serverTimeToExecuteMs = 4_200L,
                version = 8L,
                updatedAtMs = 4_000L,
            ),
        )

        val restored = newService().getOrCreateState(42L)

        assertEquals(PlaybackStatus.PAUSED, restored.status)
        assertEquals(1001L, restored.recordingId)
        assertEquals(22.5, restored.positionSeconds)
        assertEquals(0L, restored.serverTimeToExecuteMs)
        assertEquals(8L, restored.version)
        assertEquals(4_000L, restored.updatedAtMs)
    }

    @Test
    fun `fresh service falls back to current queue current entry when resume state is absent`() {
        currentQueueService.replaceQueue(
            accountId = 42L,
            recordings = listOf(
                ResolvedQueueRecording(
                    recordingId = 1001L,
                    mediaFileId = 2001L,
                    workId = 3001L,
                    title = "Track 1001",
                    artistLabel = "Artist 1001",
                    coverMediaFileId = null,
                    durationMs = 180_000L,
                ),
                ResolvedQueueRecording(
                    recordingId = 1002L,
                    mediaFileId = 2002L,
                    workId = 3002L,
                    title = "Track 1002",
                    artistLabel = "Artist 1002",
                    coverMediaFileId = null,
                    durationMs = 180_000L,
                ),
            ),
            currentIndex = 1,
            nowMs = 2_000L,
        )

        val restored = newService().getOrCreateState(42L)

        assertEquals(PlaybackStatus.PAUSED, restored.status)
        assertEquals(1002L, restored.recordingId)
        assertEquals(0.0, restored.positionSeconds)
        assertEquals(0L, restored.version)
    }

    @Test
    fun `fresh service remains empty when neither resume state nor queue current entry exists`() {
        val restored = newService().getOrCreateState(42L)

        assertEquals(PlaybackStatus.PAUSED, restored.status)
        assertNull(restored.recordingId)
        assertEquals(0.0, restored.positionSeconds)
        assertEquals(0L, restored.version)
    }

    @Test
    fun `schedule pause persists state for fresh service reload`() {
        service.schedulePause(
            accountId = 42L,
            commandId = "cmd-pause-001",
            recordingId = 1001L,
            positionSeconds = 9.5,
            nowMs = 1_200L,
            executeAtMs = 1_600L,
        )

        val restored = newService().getOrCreateState(42L)

        assertEquals(PlaybackStatus.PAUSED, restored.status)
        assertEquals(1001L, restored.recordingId)
        assertEquals(9.5, restored.positionSeconds)
        assertEquals(0L, restored.serverTimeToExecuteMs)
        assertEquals(1L, restored.version)
    }

    @Test
    fun `schedule seek persists state for fresh service reload`() {
        service.schedulePause(
            accountId = 42L,
            commandId = "cmd-pause-001",
            recordingId = 1001L,
            positionSeconds = 12.5,
            nowMs = 1_100L,
            executeAtMs = 1_500L,
        )
        service.scheduleSeek(
            accountId = 42L,
            commandId = "cmd-seek-001",
            recordingId = 1001L,
            positionSeconds = 20.0,
            nowMs = 1_200L,
            executeAtMs = 1_600L,
        )

        val restored = newService().getOrCreateState(42L)

        assertEquals(PlaybackStatus.PAUSED, restored.status)
        assertEquals(1001L, restored.recordingId)
        assertEquals(20.0, restored.positionSeconds)
        assertEquals(0L, restored.serverTimeToExecuteMs)
        assertEquals(2L, restored.version)
    }

    @Test
    fun `pending play does not survive fresh service reload`() {
        service.createPendingPlay(
            accountId = 42L,
            commandId = "cmd-play-001",
            initiatorDeviceId = "web-a",
            recordingId = 1001L,
            positionSeconds = 12.5,
            nowMs = 1_000L,
            timeoutAtMs = 4_000L,
        )

        val freshService = newService()

        assertNull(freshService.getPendingPlay(42L))
        val restored = freshService.getOrCreateState(42L)
        assertNull(restored.recordingId)
        assertEquals(0.0, restored.positionSeconds)
    }

    private fun newService(): PlaybackSessionService {
        return PlaybackSessionService(
            lockManager = PlaybackAccountLockManager(),
            timeProvider = timeProvider,
            currentQueueService = currentQueueService,
            resumeStateStore = resumeStateStore,
        )
    }
}

private class FakePlaybackQueueRecordingCatalog : CurrentQueueRecordingCatalog {
    override fun getExistingRecordingIds(recordingIds: Set<Long>): Set<Long> = recordingIds

    override fun countWorks(): Int = 0

    override fun loadResolvedRecordings(
        recordingIds: Set<Long>,
        requiredMediaFileId: Long?,
    ): List<ResolvedQueueRecording> {
        return recordingIds.mapNotNull { recordingId ->
            val mediaFileId = when (recordingId) {
                1001L -> 2001L
                1002L -> 2002L
                else -> null
            } ?: return@mapNotNull null
            if (requiredMediaFileId != null && requiredMediaFileId != mediaFileId) {
                return@mapNotNull null
            }
            ResolvedQueueRecording(
                recordingId = recordingId,
                mediaFileId = mediaFileId,
                workId = recordingId + 2_000L,
                title = "Track $recordingId",
                artistLabel = "Artist $recordingId",
                coverMediaFileId = null,
                durationMs = 180_000L,
            )
        }
    }

    override fun findFirstSimilarRecordingId(
        recordingId: Long,
        excludedWorkIds: Set<Long>,
    ): Long? = null
}
